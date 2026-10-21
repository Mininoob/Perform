package com.perform.reader.library

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.perform.sync.Genres
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.ReadingProgression
import org.readium.r2.shared.publication.services.cover
import java.io.File
import java.security.MessageDigest
import kotlin.math.min

/** What the library needs to know about a book, read once when it is added or changes. */
data class BookDetails(
    val title: String,
    val author: String?,
    val rightToLeft: Boolean,
    val coverThumbPath: String?,
    val coverFullPath: String?,
    val coverColor: Int?,
    val genres: List<String> = emptyList(),
    /** No title inside the book; [title] is its file name. */
    val titleFromFile: Boolean = false,
)

/** A cover saved to disk: both sizes and its dominant color. */
data class SavedCover(val thumbPath: String, val fullPath: String, val color: Int)

/**
 * Reads metadata and saves the cover to disk.
 *
 * Covers are written as two JPEGs, a grid thumbnail and a full-size copy for Book Mode,
 * named by a hash of the book's URI so a rescan overwrites rather than accumulates.
 * Book Mode and the library only ever read these files, never the book itself.
 */
class CoverExtractor(context: Context) {

    private val resolver: ContentResolver = context.contentResolver
    private val coverDir = File(context.filesDir, "covers").apply { mkdirs() }

    suspend fun extract(publication: Publication, uri: String, fallbackTitle: String): BookDetails {
        val metadata = publication.metadata
        val cover = publication.cover() ?: renderFirstPdfPage(uri, publication)

        val key = sha1(uri)
        val thumb = cover?.let { save(it, File(coverDir, "${key}_thumb.jpg"), THUMB_PX) }
        val full = cover?.let { save(it, File(coverDir, "${key}_full.jpg"), FULL_PX) }
        val color = cover?.let { Palette.from(it).generate().getDominantColor(Color.DKGRAY) }

        return BookDetails(
            title = metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            author = metadata.authors.joinToString(", ") { it.name }.ifBlank { null },
            rightToLeft = metadata.readingProgression == ReadingProgression.RTL,
            coverThumbPath = thumb,
            coverFullPath = full,
            coverColor = color,
            genres = Genres.classify(metadata.subjects.map { it.name }),
            titleFromFile = metadata.title.isNullOrBlank(),
        )
    }

    /** Saves a cover found for a book that had none (looked up by the PC). */
    fun saveCover(uri: String, bitmap: Bitmap): SavedCover {
        val key = sha1(uri)
        return SavedCover(
            thumbPath = save(bitmap, File(coverDir, "${key}_thumb.jpg"), THUMB_PX),
            fullPath = save(bitmap, File(coverDir, "${key}_full.jpg"), FULL_PX),
            color = Palette.from(bitmap).generate().getDominantColor(Color.DKGRAY),
        )
    }

    fun delete(book: BookEntity) {
        listOfNotNull(book.coverThumbPath, book.coverFullPath).forEach { File(it).delete() }
    }

    /** PDFs rarely carry a cover image; page 1 is the cover. */
    private fun renderFirstPdfPage(uri: String, publication: Publication): Bitmap? {
        if (!publication.conformsTo(Publication.Profile.PDF)) return null
        return runCatching {
            resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.openPage(0).use { page ->
                        val scale = FULL_PX.toFloat() / maxOf(page.width, page.height)
                        val bitmap = createBitmap((page.width * scale).toInt(), (page.height * scale).toInt())
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    private fun save(bitmap: Bitmap, file: File, maxPx: Int): String {
        val scale = min(1f, maxPx.toFloat() / maxOf(bitmap.width, bitmap.height))
        val sized = if (scale < 1f) {
            bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
        } else {
            bitmap
        }
        file.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.absolutePath
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val THUMB_PX = 480
        const val FULL_PX = 1400
    }
}
