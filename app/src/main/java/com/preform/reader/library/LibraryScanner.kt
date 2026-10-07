package com.preform.reader.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** One book file found in the library folder. */
data class ScannedFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val format: BookFormat,
)

/** What a scan means for the library, relative to the folder books it already knows. */
data class LibraryDiff(
    val added: List<ScannedFile>,
    /** Files whose size or timestamp changed, paired with the existing row to update. */
    val changed: List<Pair<BookEntity, ScannedFile>>,
    /** Rows whose files are gone from the folder. */
    val removed: List<BookEntity>,
)

/**
 * Compares a scan against the library's folder books, by URI.
 *
 * Only folder books are compared; imports live outside the folder and are never removed
 * by a scan. A file counts as changed when its size or modification time differs.
 */
fun diffLibrary(existingFolderBooks: List<BookEntity>, scanned: List<ScannedFile>): LibraryDiff {
    val existingByUri = existingFolderBooks.associateBy { it.uri }
    val scannedUris = scanned.mapTo(HashSet()) { it.uri }

    val added = scanned.filter { it.uri !in existingByUri }
    val changed = scanned.mapNotNull { file ->
        existingByUri[file.uri]
            ?.takeIf { it.sizeBytes != file.sizeBytes || it.lastModified != file.lastModified }
            ?.let { it to file }
    }
    val removed = existingFolderBooks.filter { it.uri !in scannedUris }
    return LibraryDiff(added, changed, removed)
}

/** EPUB or PDF by MIME type, falling back to the extension (some providers report neither reliably). */
fun formatOf(name: String, mimeType: String?): BookFormat? = when {
    mimeType == "application/epub+zip" || name.endsWith(".epub", ignoreCase = true) -> BookFormat.EPUB
    mimeType == "application/pdf" || name.endsWith(".pdf", ignoreCase = true) -> BookFormat.PDF
    else -> null
}

/**
 * Lists every EPUB and PDF under a SAF tree, recursively.
 *
 * Uses DocumentsContract child queries directly: DocumentFile issues one query per
 * attribute per file, which is painfully slow on a large book folder.
 */
class LibraryScanner(private val resolver: ContentResolver) {

    fun scan(treeUri: Uri): List<ScannedFile> {
        val found = mutableListOf<ScannedFile>()
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(treeUri)))

        while (pending.isNotEmpty()) {
            val parentId = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(children, COLUMNS, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending += id
                        continue
                    }
                    val format = formatOf(name, mime) ?: continue
                    found += ScannedFile(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString(),
                        name = name,
                        sizeBytes = if (cursor.isNull(3)) 0 else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0 else cursor.getLong(4),
                        format = format,
                    )
                }
            }
        }
        return found
    }

    private companion object {
        val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}

/** "primary:Books/Novels" → "Books/Novels": a readable label for a picked folder. */
fun folderLabel(treeUri: String): String =
    runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }
        .getOrNull()
        ?.substringAfter(':')
        ?.ifEmpty { "Internal storage" }
        ?: treeUri
