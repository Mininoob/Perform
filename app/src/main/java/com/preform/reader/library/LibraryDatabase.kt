package com.preform.reader.library

import android.content.Context
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

/** FOLDER books are read in place from the library folder; IMPORTED ones are app-owned copies. */
enum class BookSource { FOLDER, IMPORTED }

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
)

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
}

@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun books(): BookDao

    companion object {
        fun create(context: Context): LibraryDatabase =
            Room.databaseBuilder(context, LibraryDatabase::class.java, "library.db").build()
    }
}
