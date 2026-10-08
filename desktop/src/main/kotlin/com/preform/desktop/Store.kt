package com.preform.desktop

import com.preform.sync.BookState
import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.PeerKind
import com.preform.sync.Protocol
import com.preform.sync.Stamp
import com.preform.sync.FolderStamp
import com.preform.sync.CatalogPage
import com.preform.sync.mergedWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    /** Where the genres came from: "book", "online", "none" (looked, found nothing), or null (not looked yet). */
    val genreSource: String? = null,
    /** No title inside the file; the catalog title is its file name until a lookup finds better. */
    val titleFromFile: Boolean = false,
    /** Null until Open Library has been asked about this book. */
    val lookup: String? = null,
    /** The file couldn't be opened as a book when it was added. */
    val unreadable: Boolean = false,
    /** Set when this is another library's book (a paired computer's or a phone's), not ours. */
    val peerId: String? = null,
)

/** "Fiction/Fantasy/The Hobbit.epub" becomes "Fiction/Fantasy"; "" for a book at the top. */
fun folderOf(relativePath: String): String = relativePath.substringBeforeLast('/', "")

private const val GENRE_SEPARATOR = "|"

data class Device(
    val deviceId: String,
    val name: String,
    val pairedAt: Long,
    val lastSeen: Long?,
    /** A phone, or another computer's Preform Library (read-only). */
    val kind: PeerKind = PeerKind.PHONE,
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

    private val _changes = MutableStateFlow(0L)

    /** Ticks on every change (books, reading state, devices), so the window can refresh. */
    val changes: StateFlow<Long> = _changes

    private fun changed() = _changes.update { it + 1 }

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
        // Added after the first release of the table.
        addColumnIfMissing("books", "genres", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing("books", "genre_source", "TEXT")
        addColumnIfMissing("books", "description", "TEXT")
        addColumnIfMissing("books", "year", "INTEGER")
        addColumnIfMissing("books", "title_from_file", "INTEGER NOT NULL DEFAULT 0")
        // Null until Open Library has been asked; "done" afterwards, found or not.
        addColumnIfMissing("books", "lookup", "TEXT")
        addColumnIfMissing("books", "series", "TEXT")
        addColumnIfMissing("books", "series_index", "REAL")
        addColumnIfMissing("books", "unreadable", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("devices", "kind", "TEXT NOT NULL DEFAULT 'PHONE'")
        // Organizing stamps: when and where a book's folder, details or removal last changed.
        addColumnIfMissing("books", "folder_at", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("books", "folder_by", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing("books", "info_at", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("books", "info_by", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing("books", "removed_at", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("books", "removed_by", "TEXT NOT NULL DEFAULT ''")
        db.createStatement().use {
            // Folders as their own facts, so empty ones and removed ones reach phones and mirrors.
            it.executeUpdate(
                """CREATE TABLE IF NOT EXISTS folders (path TEXT PRIMARY KEY, removed INTEGER NOT NULL DEFAULT 0,
                     at INTEGER NOT NULL, by TEXT NOT NULL, rev INTEGER NOT NULL)""",
            )
        }
        db.createStatement().use {
            // Ids books had before information was written into their files.
            it.executeUpdate("CREATE TABLE IF NOT EXISTS aliases (old_id TEXT PRIMARY KEY, new_id TEXT NOT NULL)")
        }
    }

    private fun addColumnIfMissing(table: String, column: String, definition: String) {
        val exists = db.prepareStatement("SELECT 1 FROM pragma_table_info(?) WHERE name = ?").use { p ->
            p.setString(1, table)
            p.setString(2, column)
            p.executeQuery().use { it.next() }
        }
        if (!exists) db.createStatement().use { it.executeUpdate("ALTER TABLE $table ADD COLUMN $column $definition") }
    }

    // Revisions

    // Stamps

    /** This library's id, for stamping its own changes. */
    @Volatile
    var origin: String = ""

    var clock: () -> Long = System::currentTimeMillis

    private val applying = ThreadLocal<Stamp?>()

    /** The stamp a change made right now carries: someone else's while applying theirs, else ours. */
    fun stamp(): Stamp = applying.get() ?: Stamp(clock(), origin)

    /** Runs [work] with every change in it carrying [stamp] (a change applied from another device). */
    fun <T> stampedAs(stamp: Stamp, work: () -> T): T {
        val before = applying.get()
        applying.set(stamp)
        try {
            return work()
        } finally {
            applying.set(before)
        }
    }

    @Synchronized
    private fun nextRev(): Long {
        val next = (meta("rev")?.toLong() ?: 0L) + 1
        setMeta("rev", next.toString())
        changed()
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
            """INSERT INTO books(content_id, rel_path, title, author, format, size, modified, rtl, cover_etag, added_at, removed, rev,
                 genres, genre_source, description, year, title_from_file, lookup, series, series_index, unreadable,
                 folder_at, folder_by)
               VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(content_id) DO UPDATE SET
                 folder_at = CASE WHEN books.removed = 1 THEN excluded.folder_at ELSE books.folder_at END,
                 folder_by = CASE WHEN books.removed = 1 THEN excluded.folder_by ELSE books.folder_by END,
                 rel_path = excluded.rel_path, title = excluded.title,
                 author = excluded.author, format = excluded.format, size = excluded.size, modified = excluded.modified,
                 rtl = excluded.rtl, cover_etag = excluded.cover_etag, removed = 0, rev = excluded.rev,
                 genres = excluded.genres, genre_source = excluded.genre_source, description = excluded.description,
                 year = excluded.year, title_from_file = excluded.title_from_file, lookup = excluded.lookup,
                 series = excluded.series, series_index = excluded.series_index, unreadable = excluded.unreadable""",
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
            p.setString(12, c.genres.joinToString(GENRE_SEPARATOR))
            p.setString(13, book.genreSource)
            p.setString(14, c.description)
            c.year?.let { p.setInt(15, it) } ?: p.setNull(15, java.sql.Types.INTEGER)
            p.setInt(16, if (book.titleFromFile) 1 else 0)
            p.setString(17, book.lookup)
            p.setString(18, c.series)
            c.seriesIndex?.let { p.setDouble(19, it) } ?: p.setNull(19, java.sql.Types.REAL)
            p.setInt(20, if (book.unreadable) 1 else 0)
            val stamp = stamp()
            p.setLong(21, stamp.at)
            p.setString(22, stamp.by)
            p.executeUpdate()
        }
    }

    /**
     * A moved or renamed file: same book, new place. Phones only hear about it when the
     * folder changed, since that moves the book to another shelf.
     */
    @Synchronized
    fun movePath(contentId: String, relativePath: String, modified: Long) {
        val current = book(contentId) ?: return
        val shelfChanged = current.catalog.folder != folderOf(relativePath)
        val stamp = if (shelfChanged) stamp() else current.catalog.folderStamp
        db.prepareStatement("UPDATE books SET rel_path = ?, modified = ?, rev = ?, folder_at = ?, folder_by = ? WHERE content_id = ?").use { p ->
            p.setString(1, relativePath)
            p.setLong(2, modified)
            p.setLong(3, if (shelfChanged) nextRev() else rev(contentId))
            p.setLong(4, stamp.at)
            p.setString(5, stamp.by)
            p.setString(6, contentId)
            p.executeUpdate()
        }
    }

    /** Marks books to be looked up again on the next pass. */
    @Synchronized
    fun resetLookup(contentIds: Collection<String>) {
        db.prepareStatement("UPDATE books SET lookup = NULL WHERE content_id = ?").use { p ->
            contentIds.forEach { p.setString(1, it); p.executeUpdate() }
        }
    }

    /** A folder was renamed: every book under [oldPrefix] now sits under [newPrefix]. */
    @Synchronized
    fun renamePathPrefix(oldPrefix: String, newPrefix: String) {
        db.prepareStatement(
            """UPDATE books SET rel_path = ? || substr(rel_path, ?), rev = ?, folder_at = ?, folder_by = ?
               WHERE substr(rel_path, 1, ?) = ?""",
        ).use { p ->
            val from = "$oldPrefix/"
            val stamp = stamp()
            p.setString(1, "$newPrefix/")
            p.setInt(2, from.length + 1)
            p.setLong(3, nextRev())
            p.setLong(4, stamp.at)
            p.setString(5, stamp.by)
            p.setInt(6, from.length)
            p.setString(7, from)
            p.executeUpdate()
        }
        // The folder and every folder inside it: gone under the old name, made under the new.
        folders().filter { !it.removed && (it.path == oldPrefix || it.path.startsWith("$oldPrefix/")) }.forEach { f ->
            saveFolder(f.path, removed = true)
            saveFolder(newPrefix + f.path.removePrefix(oldPrefix), removed = false)
        }
        if (folder(newPrefix)?.removed != false) saveFolder(newPrefix, removed = false)
    }

    private fun rev(contentId: String): Long =
        db.prepareStatement("SELECT rev FROM books WHERE content_id = ?").use { p ->
            p.setString(1, contentId)
            p.executeQuery().use { if (it.next()) it.getLong(1) else 0L }
        }

    /** Live books Open Library hasn't been asked about yet. */
    fun booksAwaitingLookup(): List<LibraryBook> =
        db.prepareStatement("SELECT * FROM books WHERE removed = 0 AND lookup IS NULL").use { p ->
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toBook()) } }
        }

    /** Saves what a lookup found (already merged with what the book had), so phones hear about it. */
    @Synchronized
    fun saveInfo(book: LibraryBook) {
        val c = book.catalog
        db.prepareStatement(
            """UPDATE books SET title = ?, author = ?, genres = ?, genre_source = ?, description = ?, year = ?,
                 title_from_file = ?, cover_etag = ?, lookup = ?, series = ?, series_index = ?, rev = ?,
                 info_at = ?, info_by = ? WHERE content_id = ?""",
        ).use { p ->
            p.setString(1, c.title)
            p.setString(2, c.author)
            p.setString(3, c.genres.joinToString(GENRE_SEPARATOR))
            p.setString(4, book.genreSource)
            p.setString(5, c.description)
            c.year?.let { p.setInt(6, it) } ?: p.setNull(6, java.sql.Types.INTEGER)
            p.setInt(7, if (book.titleFromFile) 1 else 0)
            p.setString(8, c.coverEtag)
            p.setString(9, book.lookup)
            p.setString(10, c.series)
            c.seriesIndex?.let { p.setDouble(11, it) } ?: p.setNull(11, java.sql.Types.REAL)
            p.setLong(12, nextRev())
            val stamp = stamp()
            p.setLong(13, stamp.at)
            p.setString(14, stamp.by)
            p.setString(15, c.contentId)
            p.executeUpdate()
        }
    }

    /**
     * The book's file was rewritten (information written in, or the original restored), so
     * its bytes and id changed. Moves everything known under the old id to the new one and
     * remembers the old id, so phones that knew it, and old requests, still find the book.
     */
    @Synchronized
    fun rekey(oldId: String, newId: String, size: Long, modified: Long) {
        db.autoCommit = false
        try {
            db.prepareStatement("UPDATE books SET content_id = ?, size = ?, modified = ?, rev = ? WHERE content_id = ?").use { p ->
                p.setString(1, newId)
                p.setLong(2, size)
                p.setLong(3, modified)
                p.setLong(4, nextRev())
                p.setString(5, oldId)
                p.executeUpdate()
            }
            // Earlier ids now lead here too; an id being returned to is no longer an alias.
            db.prepareStatement("UPDATE aliases SET new_id = ? WHERE new_id = ?").use { p ->
                p.setString(1, newId)
                p.setString(2, oldId)
                p.executeUpdate()
            }
            db.prepareStatement("DELETE FROM aliases WHERE old_id = ?").use { p ->
                p.setString(1, newId)
                p.executeUpdate()
            }
            db.prepareStatement("INSERT OR REPLACE INTO aliases(old_id, new_id) VALUES(?, ?)").use { p ->
                p.setString(1, oldId)
                p.setString(2, newId)
                p.executeUpdate()
            }
            state(oldId)?.let { moved ->
                db.prepareStatement("DELETE FROM states WHERE content_id = ?").use { p ->
                    p.setString(1, oldId)
                    p.executeUpdate()
                }
                mergeState(moved.copy(contentId = newId))
            }
            db.commit()
        } catch (e: Exception) {
            db.rollback()
            throw e
        } finally {
            db.autoCommit = true
        }
    }

    /** The current id for [id]: itself, or what it became after its file was rewritten. */
    fun resolve(id: String): String =
        db.prepareStatement("SELECT new_id FROM aliases WHERE old_id = ?").use { p ->
            p.setString(1, id)
            p.executeQuery().use { if (it.next()) it.getString(1) else id }
        }

    private fun previousIds(): Map<String, List<String>> =
        db.prepareStatement("SELECT old_id, new_id FROM aliases").use { p ->
            p.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(2) to rs.getString(1)) }
            }
        }.groupBy({ it.first }, { it.second })

    @Synchronized
    fun markRemoved(contentId: String) {
        db.prepareStatement("UPDATE books SET removed = 1, rev = ?, removed_at = ?, removed_by = ? WHERE content_id = ? AND removed = 0").use { p ->
            val stamp = stamp()
            p.setLong(1, nextRev())
            p.setLong(2, stamp.at)
            p.setString(3, stamp.by)
            p.setString(4, contentId)
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

    /** Changes since [since]: live books touched after it, books removed after it, and folders made or removed. */
    @Synchronized
    fun catalogSince(since: Long): CatalogPage {
        val books = mutableListOf<CatalogBook>()
        val removed = mutableListOf<String>()
        val removedStamps = HashMap<String, Stamp>()
        val aliases = previousIds()
        val cursor = currentRev()
        db.prepareStatement("SELECT * FROM books WHERE rev > ? ORDER BY rev").use { p ->
            p.setLong(1, since)
            p.executeQuery().use { rs ->
                while (rs.next()) {
                    if (rs.getInt("removed") == 1) {
                        val id = rs.getString("content_id")
                        removed += id
                        removedStamps[id] = Stamp(rs.getLong("removed_at"), rs.getString("removed_by"))
                    } else {
                        val book = rs.toBook().catalog
                        books += book.copy(previousIds = aliases[book.contentId].orEmpty())
                    }
                }
            }
        }
        val folders = db.prepareStatement("SELECT * FROM folders WHERE rev > ? ORDER BY rev").use { p ->
            p.setLong(1, since)
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toFolder()) } }
        }
        return CatalogPage(books, removed, cursor, removedStamps, folders)
    }

    // Folders

    /** Records a folder made ([removed] false) or removed, stamped like any other change. */
    @Synchronized
    fun saveFolder(path: String, removed: Boolean) {
        if (path.isEmpty()) return
        val stamp = stamp()
        db.prepareStatement(
            """INSERT INTO folders(path, removed, at, by, rev) VALUES(?,?,?,?,?)
               ON CONFLICT(path) DO UPDATE SET removed = excluded.removed, at = excluded.at, by = excluded.by, rev = excluded.rev""",
        ).use { p ->
            p.setString(1, path)
            p.setInt(2, if (removed) 1 else 0)
            p.setLong(3, stamp.at)
            p.setString(4, stamp.by)
            p.setLong(5, nextRev())
            p.executeUpdate()
        }
        changed()
    }

    fun folder(path: String): FolderStamp? =
        db.prepareStatement("SELECT * FROM folders WHERE path = ?").use { p ->
            p.setString(1, path)
            p.executeQuery().use { if (it.next()) it.toFolder() else null }
        }

    fun folders(): List<FolderStamp> =
        db.prepareStatement("SELECT * FROM folders ORDER BY path").use { p ->
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toFolder()) } }
        }

    /** When a removed book went, if it did. */
    fun removedStamp(contentId: String): Stamp? =
        db.prepareStatement("SELECT removed_at, removed_by FROM books WHERE content_id = ? AND removed = 1").use { p ->
            p.setString(1, contentId)
            p.executeQuery().use { if (it.next()) Stamp(it.getLong(1), it.getString(2)) else null }
        }

    private fun ResultSet.toFolder() = FolderStamp(getString("path"), getInt("removed") == 1, Stamp(getLong("at"), getString("by")))

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

    fun addDevice(deviceId: String, name: String, tokenHash: String, now: Long, kind: PeerKind = PeerKind.PHONE) {
        db.prepareStatement(
            """INSERT INTO devices(device_id, name, token_hash, paired_at, last_seen, kind) VALUES(?,?,?,?,NULL,?)
               ON CONFLICT(device_id) DO UPDATE SET name = excluded.name, token_hash = excluded.token_hash,
                 paired_at = excluded.paired_at, kind = excluded.kind""",
        ).use { p ->
            p.setString(1, deviceId)
            p.setString(2, name)
            p.setString(3, tokenHash)
            p.setLong(4, now)
            p.setString(5, kind.name)
            p.executeUpdate()
        }
        changed()
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
        changed()
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
        changed()
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
            folder = folderOf(getString("rel_path")),
            genres = getString("genres").orEmpty().split(GENRE_SEPARATOR).filter(String::isNotEmpty),
            description = getString("description"),
            year = getInt("year").takeIf { !wasNull() },
            series = getString("series"),
            seriesIndex = getDouble("series_index").takeIf { !wasNull() },
            folderStamp = Stamp(getLong("folder_at"), getString("folder_by")),
            infoStamp = Stamp(getLong("info_at"), getString("info_by")),
            fileName = getString("rel_path").substringAfterLast('/'),
        ),
        relativePath = getString("rel_path"),
        modified = getLong("modified"),
        genreSource = getString("genre_source"),
        titleFromFile = getInt("title_from_file") == 1,
        lookup = getString("lookup"),
        unreadable = getInt("unreadable") == 1,
    )

    private fun ResultSet.toDevice() = Device(
        deviceId = getString("device_id"),
        name = getString("name"),
        pairedAt = getLong("paired_at"),
        lastSeen = getLong("last_seen").takeIf { !wasNull() },
        kind = runCatching { PeerKind.valueOf(getString("kind")) }.getOrDefault(PeerKind.PHONE),
    )
}
