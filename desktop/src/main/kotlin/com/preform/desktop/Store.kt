package com.preform.desktop

import com.preform.sync.BookState
import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.Protocol
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
)

/** "Fiction/Fantasy/The Hobbit.epub" becomes "Fiction/Fantasy"; "" for a book at the top. */
fun folderOf(relativePath: String): String = relativePath.substringBeforeLast('/', "")

private const val GENRE_SEPARATOR = "|"

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
                 genres, genre_source, description, year, title_from_file, lookup)
               VALUES(?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?,?,?)
               ON CONFLICT(content_id) DO UPDATE SET rel_path = excluded.rel_path, title = excluded.title,
                 author = excluded.author, format = excluded.format, size = excluded.size, modified = excluded.modified,
                 rtl = excluded.rtl, cover_etag = excluded.cover_etag, removed = 0, rev = excluded.rev,
                 genres = excluded.genres, genre_source = excluded.genre_source, description = excluded.description,
                 year = excluded.year, title_from_file = excluded.title_from_file, lookup = excluded.lookup""",
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
            p.executeUpdate()
        }
    }

    /**
     * A moved or renamed file: same book, new place. Phones only hear about it when the
     * folder changed, since that moves the book to another shelf.
     */
    @Synchronized
    fun movePath(contentId: String, relativePath: String, modified: Long) {
        val shelfChanged = book(contentId)?.catalog?.folder != folderOf(relativePath)
        db.prepareStatement("UPDATE books SET rel_path = ?, modified = ?, rev = ? WHERE content_id = ?").use { p ->
            p.setString(1, relativePath)
            p.setLong(2, modified)
            p.setLong(3, if (shelfChanged) nextRev() else rev(contentId))
            p.setString(4, contentId)
            p.executeUpdate()
        }
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
                 title_from_file = ?, cover_etag = ?, lookup = ?, rev = ? WHERE content_id = ?""",
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
            p.setLong(10, nextRev())
            p.setString(11, c.contentId)
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
        val aliases = previousIds()
        db.prepareStatement("SELECT * FROM books WHERE rev > ? ORDER BY rev").use { p ->
            p.setLong(1, since)
            p.executeQuery().use { rs ->
                while (rs.next()) {
                    if (rs.getInt("removed") == 1) {
                        removed += rs.getString("content_id")
                    } else {
                        val book = rs.toBook().catalog
                        books += book.copy(previousIds = aliases[book.contentId].orEmpty())
                    }
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
        ),
        relativePath = getString("rel_path"),
        modified = getLong("modified"),
        genreSource = getString("genre_source"),
        titleFromFile = getInt("title_from_file") == 1,
        lookup = getString("lookup"),
    )

    private fun ResultSet.toDevice() = Device(
        deviceId = getString("device_id"),
        name = getString("name"),
        pairedAt = getLong("paired_at"),
        lastSeen = getLong("last_seen").takeIf { !wasNull() },
    )
}
