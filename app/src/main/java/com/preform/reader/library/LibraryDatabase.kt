package com.preform.reader.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class BookFormat { EPUB, PDF }

const val GENRE_SEPARATOR = "|"

/**
 * FOLDER books are read in place from the library folder; IMPORTED ones are app-owned copies;
 * SERVER books live in the PC library and are downloaded when first opened.
 */
enum class BookSource { FOLDER, IMPORTED, SERVER }

@Entity(tableName = "books", indices = [Index(value = ["uri"], unique = true)])
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** content:// document URI for folder books, file:// for imports. */
    val uri: String,
    val source: BookSource,
    val format: BookFormat,
    val title: String,
    val author: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val coverThumbPath: String?,
    val coverFullPath: String?,
    /** Dominant cover color, ARGB. */
    val coverColor: Int?,
    val rightToLeft: Boolean,
    /** Readium Locator JSON: exactly where the reader left off. */
    val locatorJson: String? = null,
    /** 0..1 through the whole book, for progress bars. */
    val progression: Double = 0.0,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    /** Removed from the library by the user. Folder books are hidden rather than deleted, or the next scan would bring them back. */
    val hidden: Boolean = false,
    /** This book's ReadingPreferences overrides as JSON; null when it follows the global settings. */
    val prefsOverrideJson: String? = null,
    /** Book Mode for this book; null until toggled, which counts as on. */
    val bookMode: Boolean? = null,
    /** SHA-256 of the file: the same book on the phone and the PC shares one identity. */
    val contentId: String? = null,
    /** SERVER books: the downloaded copy, or null while it only lives on the PC. */
    val localPath: String? = null,
    /** SERVER books: "Keep downloaded", never cleared to free up space. */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    /** SERVER books: the PC's cover version, so covers are only fetched again when they change. */
    val coverEtag: String? = null,
    /** When each synced group last changed on this phone; see BookState in :sync-protocol. */
    val positionAt: Long? = null,
    val settingsAt: Long? = null,
    val bookModeAt: Long? = null,
    /** Changed here since the last successful push to the PC. */
    @ColumnInfo(defaultValue = "0") val stateDirty: Boolean = false,
    /** The folder the book sits in ("Fiction/Fantasy"), "" at the top; its shelf in the library. */
    @ColumnInfo(defaultValue = "") val shelf: String = "",
    /** Genres from [com.preform.sync.Genres], joined by [GENRE_SEPARATOR]. */
    @ColumnInfo(defaultValue = "") val genres: String = "",
    val description: String? = null,
    /** First published. */
    val year: Int? = null,
    /** No title inside the file: [title] is the file name until a lookup finds the real one. */
    @ColumnInfo(defaultValue = "0") val titleFromFile: Boolean = false,
    /** A phone-only book the PC has looked up (found or not). */
    @ColumnInfo(defaultValue = "0") val lookedUp: Boolean = false,
    val series: String? = null,
    /** Place in the series; fractional for in-between books (2.5). */
    val seriesIndex: Double? = null,
) {
    val genreList: List<String> get() = genres.split(GENRE_SEPARATOR).filter(String::isNotEmpty)

    /** Where Readium can open this book now; null for a SERVER book not downloaded yet. */
    val readableUri: String?
        get() = if (source == BookSource.SERVER) localPath?.let { "file://$it" } else uri

    val downloaded: Boolean get() = source != BookSource.SERVER || localPath != null
}

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE hidden = 0")
    fun observeVisible(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE source = :source")
    suspend fun bySource(source: BookSource): List<BookEntity>

    @Query("SELECT * FROM books WHERE source = 'IMPORTED' AND title = :title AND sizeBytes = :size LIMIT 1")
    suspend fun findImport(title: String, size: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE books SET locatorJson = :locatorJson, progression = :progression, lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun updatePosition(id: Long, locatorJson: String, progression: Double, openedAt: Long)

    @Query("UPDATE books SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun markOpened(id: Long, openedAt: Long)

    @Query("UPDATE books SET prefsOverrideJson = :json WHERE id = :id")
    suspend fun updateOverride(id: Long, json: String?)

    @Query("UPDATE books SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE books SET bookMode = :on WHERE id = :id")
    suspend fun setBookMode(id: Long, on: Boolean)

    @Query("SELECT * FROM books WHERE source = 'SERVER'")
    fun observeServerBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE source = 'SERVER'")
    suspend fun serverBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE source = 'SERVER' AND contentId = :contentId LIMIT 1")
    suspend fun serverBook(contentId: String): BookEntity?

    @Query("UPDATE books SET localPath = :path WHERE id = :id")
    suspend fun setLocalPath(id: Long, path: String?)

    @Query("UPDATE books SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** Books only this phone has, not yet looked up by the PC. */
    @Query("SELECT * FROM books WHERE source != 'SERVER' AND lookedUp = 0 AND hidden = 0 LIMIT :limit")
    suspend fun localBooksAwaitingLookup(limit: Int): List<BookEntity>
}

@Database(
    entities = [BookEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        // v2: per-book Book Mode.
        AutoMigration(from = 1, to = 2),
        // v3: books from the PC library, content ids and sync stamps.
        AutoMigration(from = 2, to = 3),
        // v4: shelves (folders) and genres.
        AutoMigration(from = 3, to = 4),
        // v5: descriptions, years and lookups.
        AutoMigration(from = 4, to = 5),
        // v6: series.
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun books(): BookDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()
    }
}
