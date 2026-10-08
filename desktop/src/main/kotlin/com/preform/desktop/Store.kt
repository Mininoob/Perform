package com.preform.desktop

import com.preform.sync.BookState
import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.Protocol
import com.preform.sync.mergedWith
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/** A book file in the library, with where it lives. */
data class LibraryBook(
    val catalog: CatalogBook,
    /** Path relative to the library root, with forward slashes. */
    val relativePath: String,
    val modified: Long,
)

data class Device(
    val deviceId: String,
    val name: String,
    val pairedAt: Long,
    val lastSeen: Long?,
)

/**
 * Everything the server remembers, in one SQLite file: the catalog, reading state,
 * paired devices, and a change counter.
 *
 * Every change to the catalog or to reading state takes the next revision number, so a
 * phone can ask for "everything since revision N" and get only what it missed.
 */
class Store(file: Path) : AutoCloseable {

    private val db: Connection = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")

    init {
        db.createStatement().use { s ->
            s.executeUpdate("PRAGMA journal_mode=WAL")
            s.executeUpdate(
                """CREATE TABLE IF NOT EXISTS books (
                    content_id TEXT PRIMARY KEY, rel_path TEXT NOT NULL, title TEXT NOT NULL, author TEXT,
                    format TEXT NOT NULL, size INTEGER NOT NULL, modified INTEGER NOT NULL, rtl INTEGER NOT NULL,
                    cover_etag TEXT, added_at INTEGER NOT NULL, removed INTEGER NOT NULL DEFAULT 0, rev INTEGER NOT NULL)""",
            )
            s.executeUpdate("CREATE TABLE IF NOT EXISTS states (content_id TEXT PRIMARY KEY, json TEXT NOT NULL, rev INTEGER NOT NULL)")
            s.executeUpdate(
                """CREATE TABLE IF NOT EXISTS devices (device_id TEXT PRIMARY KEY, name TEXT NOT NULL,
                    token_hash TEXT NOT NULL, paired_at INTEGER NOT NULL, last_seen INTEGER)""",
            )
            s.executeUpdate("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
    }

    // Revisions

    @Synchronized
    private fun nextRev(): Long {
        val next = (meta("rev")?.toLong() ?: 0L) + 1
        setMeta("rev", next.toString())
        return next
    }

    fun meta(key: String): String? =
        db.prepareStatement("SELECT value FROM meta WHERE key = ?").use { p ->
            p.setString(1, key)
            p.executeQuery().use { if (it.next()) it.getString(1) else null }
        }

    fun setMeta(key: String, value: String) {
        db.prepareStatement("INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value").use { p ->
            p.setString(1, key)
            p.setString(2, value)
            p.executeUpdate()
        }
    }

    // Catalog

    @Synchronized
    fun upsertBook(book: LibraryBook) {
        val c = book.catalog
        db.prepareStatement(
            """INSERT INTO books(content_id, rel_path, title, author, format, size, modified, rtl, cover_etag, added_at, removed, rev)
               VALUES(?,?,?,?,?,?,?,?,?,?,0,?)
               ON CONFLICT(content_id) DO UPDATE SET rel_path = excluded.rel_path, title = excluded.title,
                 author = excluded.author, format = excluded.format, size = excluded.size, modified = excluded.modified,
                 rtl = excluded.rtl, cover_etag = excluded.cover_etag, removed = 0, rev = excluded.rev""",
        ).use { p ->
            p.setString(1, c.contentId)
            p.setString(2, book.relativePath)
            p.setString(3, c.title)
            p.setString(4, c.author)
            p.setString(5, c.format.name)
            p.setLong(6, c.sizeBytes)
            p.setLong(7, book.modified)
            p.setInt(8, if (c.rightToLeft) 1 else 0)
            p.setString(9, c.coverEtag)
            p.setLong(10, c.addedAt)
            p.setLong(11, nextRev())
            p.executeUpdate()
        }
    }

    /** A moved or renamed file: same book, new place. Not a change phones need to hear about. */
    @Synchronized
    fun movePath(contentId: String, relativePath: String, modified: Long) {
        db.prepareStatement("UPDATE books SET rel_path = ?, modified = ? WHERE content_id = ?").use { p ->
            p.setString(1, relativePath)
            p.setLong(2, modified)
            p.setString(3, contentId)
            p.executeUpdate()
        }
    }

    @Synchronized
    fun markRemoved(contentId: String) {
        db.prepareStatement("UPDATE books SET removed = 1, rev = ? WHERE content_id = ? AND removed = 0").use { p ->
            p.setLong(1, nextRev())
            p.setString(2, contentId)
            p.executeUpdate()
        }
    }

    fun book(contentId: String): LibraryBook? =
        db.prepareStatement("SELECT * FROM books WHERE content_id = ? AND removed = 0").use { p ->
            p.setString(1, contentId)
            p.executeQuery().use { if (it.next()) it.toBook() else null }
        }

    fun liveBooks(): List<LibraryBook> =
        db.prepareStatement("SELECT * FROM books WHERE removed = 0").use { p ->
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toBook()) } }
        }

    /** Changes since [since]: live books touched after it, and books removed after it. */
    fun catalogSince(since: Long): Triple<List<CatalogBook>, List<String>, Long> {
        val books = mutableListOf<CatalogBook>()
        val removed = mutableListOf<String>()
        db.prepareStatement("SELECT * FROM books WHERE rev > ? ORDER BY rev").use { p ->
            p.setLong(1, since)
            p.executeQuery().use { rs ->
                while (rs.next()) {
                    if (rs.getInt("removed") == 1) removed += rs.getString("content_id") else books += rs.toBook().catalog
                }
            }
        }
        return Triple(books, removed, currentRev())
    }

    fun currentRev(): Long = meta("rev")?.toLong() ?: 0L

    // Reading state

    /** Merges an incoming state into the stored one and returns the result. Only a real change takes a revision. */
    @Synchronized
    fun mergeState(incoming: BookState): BookState {
        val stored = state(incoming.contentId)
        val merged = stored?.mergedWith(incoming) ?: incoming
        if (merged != stored) {
            db.prepareStatement(
                """INSERT INTO states(content_id, json, rev) VALUES(?,?,?)
                   ON CONFLICT(content_id) DO UPDATE SET json = excluded.json, rev = excluded.rev""",
            ).use { p ->
                p.setString(1, merged.contentId)
                p.setString(2, Protocol.json.encodeToString(BookState.serializer(), merged))
                p.setLong(3, nextRev())
                p.executeUpdate()
            }
        }
        return merged
    }

    fun state(contentId: String): BookState? =
        db.prepareStatement("SELECT json FROM states WHERE content_id = ?").use { p ->
            p.setString(1, contentId)
            p.executeQuery().use { if (it.next()) decodeState(it.getString(1)) else null }
        }

    fun statesSince(since: Long): Pair<List<BookState>, Long> {
        val states = db.prepareStatement("SELECT json FROM states WHERE rev > ? ORDER BY rev").use { p ->
            p.setLong(1, since)
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(decodeState(rs.getString(1))) } }
        }
        return states to currentRev()
    }

    // Devices

    fun addDevice(deviceId: String, name: String, tokenHash: String, now: Long) {
        db.prepareStatement(
            """INSERT INTO devices(device_id, name, token_hash, paired_at, last_seen) VALUES(?,?,?,?,NULL)
               ON CONFLICT(device_id) DO UPDATE SET name = excluded.name, token_hash = excluded.token_hash,
                 paired_at = excluded.paired_at""",
        ).use { p ->
            p.setString(1, deviceId)
            p.setString(2, name)
            p.setString(3, tokenHash)
            p.setLong(4, now)
            p.executeUpdate()
        }
    }

    /** The device holding this token, if it's still paired. */
    fun deviceForTokenHash(tokenHash: String): Device? =
        db.prepareStatement("SELECT * FROM devices WHERE token_hash = ?").use { p ->
            p.setString(1, tokenHash)
            p.executeQuery().use { if (it.next()) it.toDevice() else null }
        }

    fun touchDevice(deviceId: String, now: Long) {
        db.prepareStatement("UPDATE devices SET last_seen = ? WHERE device_id = ?").use { p ->
            p.setLong(1, now)
            p.setString(2, deviceId)
            p.executeUpdate()
        }
    }

    fun devices(): List<Device> =
        db.prepareStatement("SELECT * FROM devices ORDER BY paired_at").use { p ->
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toDevice()) } }
        }

    /** Unpairs a device: its token stops working at once. */
    fun revoke(deviceId: String) {
        db.prepareStatement("DELETE FROM devices WHERE device_id = ?").use { p ->
            p.setString(1, deviceId)
            p.executeUpdate()
        }
    }

    override fun close() = db.close()

    private fun decodeState(json: String) = Protocol.json.decodeFromString(BookState.serializer(), json)

    private fun ResultSet.toBook() = LibraryBook(
        catalog = CatalogBook(
            contentId = getString("content_id"),
            title = getString("title"),
            author = getString("author"),
            format = Format.valueOf(getString("format")),
            sizeBytes = getLong("size"),
            rightToLeft = getInt("rtl") == 1,
            coverEtag = getString("cover_etag"),
            addedAt = getLong("added_at"),
        ),
        relativePath = getString("rel_path"),
        modified = getLong("modified"),
    )

    private fun ResultSet.toDevice() = Device(
        deviceId = getString("device_id"),
        name = getString("name"),
        pairedAt = getLong("paired_at"),
        lastSeen = getLong("last_seen").takeIf { !wasNull() },
    )
}
