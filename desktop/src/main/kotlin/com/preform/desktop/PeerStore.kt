package com.preform.desktop

import com.preform.sync.CatalogBook
import com.preform.sync.CopyRequest
import com.preform.sync.Format
import com.preform.sync.PeerKind
import com.preform.sync.Protocol
import com.preform.sync.Stamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/** Another library this one can see: a computer we paired with, or a phone that publishes its books. */
data class Peer(
    val peerId: String,
    val name: String,
    val kind: PeerKind,
    val platform: String,
    /** Computers only: how to reach them and the one certificate to trust. */
    val addresses: List<String> = emptyList(),
    val port: Int = Protocol.DEFAULT_PORT,
    val certSha256: String = "",
    val token: String = "",
    val lastGood: String? = null,
    val cursor: Long = 0,
    val lastSync: Long? = null,
    /** Show "Not on <peer>" among the checks. */
    val watched: Boolean = false,
    /** Apply this library's changes here: its new books, moves, details and removals. */
    val mirror: Boolean = false,
    /** When mirroring began; only removals after it are followed. */
    val mirrorSince: Long? = null,
) {
    val addressOrder: List<String> get() = listOfNotNull(lastGood) + (addresses - setOfNotNull(lastGood))
}

/**
 * What this library knows about the others: paired computers (with their tokens), and the
 * last catalog heard from each computer or phone, kept so they stay browsable offline.
 * Its own file, apart from the library's store.
 */
class PeerStore(file: Path) : AutoCloseable {
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")

    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes
    private fun changed() = _changes.update { it + 1 }

    init {
        db.createStatement().use { s ->
            s.executeUpdate("PRAGMA journal_mode=WAL")
            s.executeUpdate(
                """CREATE TABLE IF NOT EXISTS peers (
                    peer_id TEXT PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, platform TEXT NOT NULL,
                    addresses TEXT NOT NULL DEFAULT '[]', port INTEGER NOT NULL DEFAULT 0, cert TEXT NOT NULL DEFAULT '',
                    token TEXT NOT NULL DEFAULT '', last_good TEXT, cursor INTEGER NOT NULL DEFAULT 0,
                    last_sync INTEGER, watched INTEGER NOT NULL DEFAULT 0)""",
            )
            s.executeUpdate(
                """CREATE TABLE IF NOT EXISTS peer_books (
                    peer_id TEXT NOT NULL, content_id TEXT NOT NULL, title TEXT NOT NULL, author TEXT,
                    format TEXT NOT NULL, size INTEGER NOT NULL, folder TEXT NOT NULL DEFAULT '',
                    genres TEXT NOT NULL DEFAULT '', cover_etag TEXT, year INTEGER, series TEXT, series_index REAL,
                    previous_ids TEXT NOT NULL DEFAULT '', added_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (peer_id, content_id))""",
            )
            // Which of their books is which of ours, once matched or copied.
            s.executeUpdate(
                """CREATE TABLE IF NOT EXISTS peer_links (peer_id TEXT NOT NULL, their_id TEXT NOT NULL, our_id TEXT NOT NULL,
                    PRIMARY KEY (peer_id, their_id))""",
            )
            s.executeUpdate(
                """CREATE TABLE IF NOT EXISTS copy_requests (
                    device_id TEXT NOT NULL, content_id TEXT NOT NULL, folder TEXT NOT NULL, requested_at INTEGER NOT NULL,
                    PRIMARY KEY (device_id, content_id))""",
            )
        }
    }

    init {
        listOf(
            "peers" to "mirror INTEGER NOT NULL DEFAULT 0",
            "peers" to "mirror_since INTEGER",
            "peer_books" to "folder_at INTEGER NOT NULL DEFAULT 0",
            "peer_books" to "folder_by TEXT NOT NULL DEFAULT ''",
            "peer_books" to "info_at INTEGER NOT NULL DEFAULT 0",
            "peer_books" to "info_by TEXT NOT NULL DEFAULT ''",
            "peer_books" to "description TEXT",
            "peer_books" to "file_name TEXT",
        ).forEach { (table, column) ->
            val name = column.substringBefore(' ')
            val exists = db.prepareStatement("SELECT 1 FROM pragma_table_info(?) WHERE name = ?").use { p ->
                p.setString(1, table)
                p.setString(2, name)
                p.executeQuery().use { it.next() }
            }
            if (!exists) db.createStatement().use { it.executeUpdate("ALTER TABLE $table ADD COLUMN $column") }
        }
    }

    // Peers

    @Synchronized
    fun savePeer(peer: Peer) {
        db.prepareStatement(
            """INSERT INTO peers(peer_id, name, kind, platform, addresses, port, cert, token, last_good, cursor, last_sync, watched,
                 mirror, mirror_since)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(peer_id) DO UPDATE SET name = excluded.name, kind = excluded.kind, platform = excluded.platform,
                 addresses = excluded.addresses, port = excluded.port, cert = excluded.cert, token = excluded.token,
                 last_good = excluded.last_good, cursor = excluded.cursor, last_sync = excluded.last_sync, watched = excluded.watched,
                 mirror = excluded.mirror, mirror_since = excluded.mirror_since""",
        ).use { p ->
            p.setString(1, peer.peerId)
            p.setString(2, peer.name)
            p.setString(3, peer.kind.name)
            p.setString(4, peer.platform)
            p.setString(5, Protocol.json.encodeToString(LIST, peer.addresses))
            p.setInt(6, peer.port)
            p.setString(7, peer.certSha256)
            p.setString(8, peer.token)
            p.setString(9, peer.lastGood)
            p.setLong(10, peer.cursor)
            peer.lastSync?.let { p.setLong(11, it) } ?: p.setNull(11, java.sql.Types.INTEGER)
            p.setInt(12, if (peer.watched) 1 else 0)
            p.setInt(13, if (peer.mirror) 1 else 0)
            peer.mirrorSince?.let { p.setLong(14, it) } ?: p.setNull(14, java.sql.Types.INTEGER)
            p.executeUpdate()
        }
        changed()
    }

    fun peers(): List<Peer> = db.prepareStatement("SELECT * FROM peers ORDER BY name").use { p ->
        p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toPeer()) } }
    }

    fun peer(peerId: String): Peer? = peers().firstOrNull { it.peerId == peerId }

    /** Forgets a peer and everything heard from it. */
    @Synchronized
    fun removePeer(peerId: String) {
        listOf("DELETE FROM peers WHERE peer_id = ?", "DELETE FROM peer_books WHERE peer_id = ?", "DELETE FROM peer_links WHERE peer_id = ?").forEach { sql ->
            db.prepareStatement(sql).use { p -> p.setString(1, peerId); p.executeUpdate() }
        }
        changed()
    }

    // Their books

    /** Applies catalog changes from a computer peer. */
    @Synchronized
    fun applyCatalog(peerId: String, books: List<CatalogBook>, removed: List<String>, cursor: Long, now: Long) {
        db.autoCommit = false
        try {
            books.forEach { upsertBook(peerId, it) }
            removed.forEach { id ->
                db.prepareStatement("DELETE FROM peer_books WHERE peer_id = ? AND content_id = ?").use { p ->
                    p.setString(1, peerId)
                    p.setString(2, id)
                    p.executeUpdate()
                }
            }
            db.prepareStatement("UPDATE peers SET cursor = ?, last_sync = ? WHERE peer_id = ?").use { p ->
                p.setLong(1, cursor)
                p.setLong(2, now)
                p.setString(3, peerId)
                p.executeUpdate()
            }
            db.commit()
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
        changed()
    }

    /** A phone's whole catalog, replacing what it said last time. */
    @Synchronized
    fun replaceCatalog(peerId: String, books: List<CatalogBook>, now: Long) {
        db.autoCommit = false
        try {
            db.prepareStatement("DELETE FROM peer_books WHERE peer_id = ?").use { p -> p.setString(1, peerId); p.executeUpdate() }
            books.forEach { upsertBook(peerId, it) }
            db.prepareStatement("UPDATE peers SET last_sync = ? WHERE peer_id = ?").use { p ->
                p.setLong(1, now)
                p.setString(2, peerId)
                p.executeUpdate()
            }
            db.commit()
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
        changed()
    }

    private fun upsertBook(peerId: String, b: CatalogBook) {
        db.prepareStatement(
            """INSERT OR REPLACE INTO peer_books(peer_id, content_id, title, author, format, size, folder, genres, cover_etag,
                 year, series, series_index, previous_ids, added_at, folder_at, folder_by, info_at, info_by, description, file_name)
                 VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        ).use { p ->
            p.setString(1, peerId)
            p.setString(2, b.contentId)
            p.setString(3, b.title)
            p.setString(4, b.author)
            p.setString(5, b.format.name)
            p.setLong(6, b.sizeBytes)
            p.setString(7, b.folder)
            p.setString(8, b.genres.joinToString(SEP))
            p.setString(9, b.coverEtag)
            b.year?.let { p.setInt(10, it) } ?: p.setNull(10, java.sql.Types.INTEGER)
            p.setString(11, b.series)
            b.seriesIndex?.let { p.setDouble(12, it) } ?: p.setNull(12, java.sql.Types.REAL)
            p.setString(13, b.previousIds.joinToString(SEP))
            p.setLong(14, b.addedAt)
            p.setLong(15, b.folderStamp.at)
            p.setString(16, b.folderStamp.by)
            p.setLong(17, b.infoStamp.at)
            p.setString(18, b.infoStamp.by)
            p.setString(19, b.description)
            p.setString(20, b.fileName)
            p.executeUpdate()
        }
    }

    /** A peer's books, shaped like this library's own so the same grid and covers show them. */
    fun books(peerId: String): List<LibraryBook> =
        db.prepareStatement("SELECT * FROM peer_books WHERE peer_id = ?").use { p ->
            p.setString(1, peerId)
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toBook(peerId)) } }
        }

    fun bookCounts(): Map<String, Int> =
        db.prepareStatement("SELECT peer_id, COUNT(*) FROM peer_books GROUP BY peer_id").use { p ->
            p.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) } }
        }

    // Links between their books and ours (mirroring)

    @Synchronized
    fun link(peerId: String, theirId: String, ourId: String) {
        db.prepareStatement("INSERT OR REPLACE INTO peer_links(peer_id, their_id, our_id) VALUES(?,?,?)").use { p ->
            p.setString(1, peerId)
            p.setString(2, theirId)
            p.setString(3, ourId)
            p.executeUpdate()
        }
    }

    fun links(peerId: String): Map<String, String> =
        db.prepareStatement("SELECT their_id, our_id FROM peer_links WHERE peer_id = ?").use { p ->
            p.setString(1, peerId)
            p.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2)) } }
        }

    @Synchronized
    fun unlink(peerId: String, theirId: String) {
        db.prepareStatement("DELETE FROM peer_links WHERE peer_id = ? AND their_id = ?").use { p ->
            p.setString(1, peerId)
            p.setString(2, theirId)
            p.executeUpdate()
        }
    }

    // Copy requests to phones

    @Synchronized
    fun requestCopy(deviceId: String, contentId: String, folder: String, now: Long) {
        db.prepareStatement("INSERT OR REPLACE INTO copy_requests(device_id, content_id, folder, requested_at) VALUES(?,?,?,?)").use { p ->
            p.setString(1, deviceId)
            p.setString(2, contentId)
            p.setString(3, folder)
            p.setLong(4, now)
            p.executeUpdate()
        }
        changed()
    }

    fun requests(deviceId: String): List<CopyRequest> =
        db.prepareStatement("SELECT * FROM copy_requests WHERE device_id = ? ORDER BY requested_at").use { p ->
            p.setString(1, deviceId)
            p.executeQuery().use { rs ->
                buildList { while (rs.next()) add(CopyRequest(rs.getString("content_id"), rs.getString("folder"), rs.getLong("requested_at"))) }
            }
        }

    fun allRequests(): Map<String, List<CopyRequest>> =
        db.prepareStatement("SELECT device_id FROM copy_requests GROUP BY device_id").use { p ->
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }.associateWith(::requests)

    @Synchronized
    fun fulfil(deviceId: String, contentId: String) {
        db.prepareStatement("DELETE FROM copy_requests WHERE device_id = ? AND content_id = ?").use { p ->
            p.setString(1, deviceId)
            p.setString(2, contentId)
            p.executeUpdate()
        }
        changed()
    }

    override fun close() = db.close()

    private fun ResultSet.toPeer() = Peer(
        peerId = getString("peer_id"),
        name = getString("name"),
        kind = runCatching { PeerKind.valueOf(getString("kind")) }.getOrDefault(PeerKind.COMPUTER),
        platform = getString("platform"),
        addresses = Protocol.json.decodeFromString(LIST, getString("addresses")),
        port = getInt("port"),
        certSha256 = getString("cert"),
        token = getString("token"),
        lastGood = getString("last_good"),
        cursor = getLong("cursor"),
        lastSync = getLong("last_sync").takeIf { !wasNull() },
        watched = getInt("watched") == 1,
        mirror = getInt("mirror") == 1,
        mirrorSince = getLong("mirror_since").takeIf { !wasNull() },
    )

    private fun ResultSet.toBook(peerId: String): LibraryBook {
        val folder = getString("folder")
        val title = getString("title")
        return LibraryBook(
            catalog = CatalogBook(
                contentId = getString("content_id"),
                title = title,
                author = getString("author"),
                format = Format.valueOf(getString("format")),
                sizeBytes = getLong("size"),
                coverEtag = getString("cover_etag"),
                addedAt = getLong("added_at"),
                folder = folder,
                genres = getString("genres").split(SEP).filter(String::isNotEmpty),
                year = getInt("year").takeIf { !wasNull() },
                series = getString("series"),
                seriesIndex = getDouble("series_index").takeIf { !wasNull() },
                previousIds = getString("previous_ids").split(SEP).filter(String::isNotEmpty),
                folderStamp = Stamp(getLong("folder_at"), getString("folder_by")),
                infoStamp = Stamp(getLong("info_at"), getString("info_by")),
                description = getString("description"),
                fileName = getString("file_name"),
            ),
            relativePath = if (folder.isEmpty()) title else "$folder/$title",
            modified = 0,
            peerId = peerId,
        )
    }

    private companion object {
        const val SEP = "|"
        val LIST = ListSerializer(String.serializer())
    }
}
