package com.preform.reader.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.preform.reader.reader.ReadiumServices
import com.preform.reader.settings.PreferencesStore
import com.preform.reader.settings.ReadingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface ScanState {
    data object Idle : ScanState

    /** [done] of [total] new or changed books processed. */
    data class Scanning(val done: Int, val total: Int) : ScanState
}

class LibraryRepository(
    context: Context,
    private val dao: BookDao,
    private val readium: ReadiumServices,
    private val covers: CoverExtractor,
    private val preferences: PreferencesStore,
) {
    private val resolver = context.contentResolver
    private val scanner = LibraryScanner(resolver)
    private val importDir = File(context.filesDir, "books").apply { mkdirs() }

    /** One scan at a time: launch and pull-to-refresh must not race each other. */
    private val scanLock = Mutex()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    val books: Flow<List<BookEntity>> = dao.observeVisible()

    fun observe(id: Long): Flow<BookEntity?> = dao.observe(id)

    suspend fun get(id: Long): BookEntity? = dao.get(id)

    // Library folder

    /** Brings the library in line with the folder: adds new files, refreshes changed ones, drops deleted ones. */
    suspend fun syncFolder(): Result<Unit> = scanLock.withLock {
        withContext(Dispatchers.IO) {
            val tree = preferences.libraryFolderNow() ?: return@withContext Result.success(Unit)
            val scanned = runCatching { scanner.scan(Uri.parse(tree)) }
                // Most likely the folder's permission was revoked or the folder was deleted.
                .getOrElse { return@withContext Result.failure(it) }

            val diff = diffLibrary(dao.bySource(BookSource.FOLDER), scanned)
            if (diff.removed.isNotEmpty()) {
                diff.removed.forEach(covers::delete)
                dao.delete(diff.removed.map { it.id })
            }

            val total = diff.added.size + diff.changed.size
            var done = 0
            try {
                for (file in diff.added) {
                    _scanState.value = ScanState.Scanning(done++, total)
                    val details = readDetails(file.uri, file.name) ?: continue
                    dao.insert(
                        BookEntity(
                            uri = file.uri,
                            source = BookSource.FOLDER,
                            format = file.format,
                            title = details.title,
                            author = details.author,
                            sizeBytes = file.sizeBytes,
                            lastModified = file.lastModified,
                            coverThumbPath = details.coverThumbPath,
                            coverFullPath = details.coverFullPath,
                            coverColor = details.coverColor,
                            rightToLeft = details.rightToLeft,
                            addedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                for ((existing, file) in diff.changed) {
                    _scanState.value = ScanState.Scanning(done++, total)
                    val details = readDetails(file.uri, file.name) ?: continue
                    // Keeps the reading position, overrides and hidden flag.
                    dao.update(
                        existing.copy(
                            title = details.title,
                            author = details.author,
                            sizeBytes = file.sizeBytes,
                            lastModified = file.lastModified,
                            coverThumbPath = details.coverThumbPath,
                            coverFullPath = details.coverFullPath,
                            coverColor = details.coverColor,
                            rightToLeft = details.rightToLeft,
                        ),
                    )
                }
            } finally {
                _scanState.value = ScanState.Idle
            }
            Result.success(Unit)
        }
    }

    /** Adopts a newly picked folder: keeps read access across reboots, then scans it. */
    suspend fun setLibraryFolder(treeUri: Uri): Result<Unit> {
        val previous = preferences.libraryFolderNow()
        resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (previous != null && previous != treeUri.toString()) {
            forgetFolder(previous)
        }
        preferences.setLibraryFolder(treeUri.toString())
        return syncFolder()
    }

    suspend fun clearLibraryFolder() {
        preferences.libraryFolderNow()?.let { forgetFolder(it) }
        preferences.setLibraryFolder(null)
    }

    private suspend fun forgetFolder(treeUri: String) {
        runCatching {
            resolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val folderBooks = dao.bySource(BookSource.FOLDER)
        folderBooks.forEach(covers::delete)
        dao.delete(folderBooks.map { it.id })
    }

    // One-off imports

    /**
     * Copies a book into app storage and adds it. One-off URI grants (a file picked once,
     * "Open with Preform") don't survive, so imports are always copies.
     * Importing the same book twice returns the existing entry.
     */
    suspend fun import(source: Uri): Result<Long> = withContext(Dispatchers.IO) {
        val name = displayName(source)
        val format = formatOf(name, resolver.getType(source))
            ?: return@withContext Result.failure(IllegalArgumentException("Only EPUB and PDF books are supported."))

        val extension = if (format == BookFormat.PDF) "pdf" else "epub"
        val copy = File(importDir, "${UUID.randomUUID()}.$extension")
        val copied = runCatching {
            resolver.openInputStream(source)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
        }
        if (copied.isFailure) {
            copy.delete()
            return@withContext Result.failure(copied.exceptionOrNull()!!)
        }

        val uri = Uri.fromFile(copy).toString()
        val details = readDetails(uri, name)
        if (details == null) {
            copy.delete()
            return@withContext Result.failure(IllegalStateException("This file couldn't be opened as a book."))
        }

        dao.findImport(details.title, copy.length())?.let { existing ->
            copy.delete()
            dao.setHidden(existing.id, false)
            return@withContext Result.success(existing.id)
        }

        val id = dao.insert(
            BookEntity(
                uri = uri,
                source = BookSource.IMPORTED,
                format = format,
                title = details.title,
                author = details.author,
                sizeBytes = copy.length(),
                lastModified = copy.lastModified(),
                coverThumbPath = details.coverThumbPath,
                coverFullPath = details.coverFullPath,
                coverColor = details.coverColor,
                rightToLeft = details.rightToLeft,
                addedAt = System.currentTimeMillis(),
            ),
        )
        Result.success(id)
    }

    /**
     * Imports are deleted outright. Folder and PC books are hidden, or the next scan or
     * sync would bring them back; a PC book's download is cleared too. Nothing on the PC
     * is ever deleted from the phone.
     */
    suspend fun remove(book: BookEntity) {
        when (book.source) {
            BookSource.IMPORTED -> {
                runCatching { Uri.parse(book.uri).path?.let { File(it).delete() } }
                covers.delete(book)
                dao.delete(listOf(book.id))
            }
            BookSource.SERVER -> {
                book.localPath?.let { File(it).delete() }
                dao.setLocalPath(book.id, null)
                dao.setHidden(book.id, true)
            }
            BookSource.FOLDER -> dao.setHidden(book.id, true)
        }
    }

    // Reading state

    suspend fun savePosition(id: Long, locatorJson: String, progression: Double) =
        dao.updatePosition(id, locatorJson, progression, System.currentTimeMillis())

    suspend fun markOpened(id: Long) = dao.markOpened(id, System.currentTimeMillis())

    suspend fun updateOverride(id: Long, change: (ReadingPreferences) -> ReadingPreferences) {
        val current = ReadingPreferences.fromJson(dao.get(id)?.prefsOverrideJson)
        val updated = change(current)
        dao.updateOverride(id, updated.takeUnless { it.isEmpty }?.toJson())
    }

    suspend fun clearOverride(id: Long) = dao.updateOverride(id, null)

    suspend fun setBookMode(id: Long, on: Boolean) = dao.setBookMode(id, on)

    private suspend fun readDetails(uri: String, fileName: String): BookDetails? {
        val publication = readium.open(uri).getOrNull() ?: return null
        return try {
            covers.extract(publication, uri, fallbackTitle = fileName.substringBeforeLast('.'))
        } finally {
            publication.close()
        }
    }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "book"
}
