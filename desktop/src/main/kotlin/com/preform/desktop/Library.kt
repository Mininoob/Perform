package com.preform.desktop

import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.contentIdOf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeBytes

/**
 * The library folder as a catalog. A rescan only hashes files that are new or whose size
 * or timestamp changed, so it's cheap to run often; recursive file watching on Windows
 * misses nested changes, so periodic rescans are the reliable signal.
 */
class Library(
    private val root: () -> Path,
    private val store: Store,
    private val coverDir: Path,
) {
    init {
        coverDir.createDirectories()
    }

    data class ScanResult(val added: Int, val moved: Int, val removed: Int)

    @Synchronized
    fun scan(now: Long = System.currentTimeMillis()): ScanResult {
        val rootPath = root()
        if (!rootPath.isDirectory()) return ScanResult(0, 0, 0)

        val known = store.liveBooks()
        val byPath = known.associateBy { it.relativePath }
        val seen = HashSet<String>()
        var added = 0
        var moved = 0

        Files.walk(rootPath).use { paths ->
            paths.filter { it.isRegularFile() && BookMetadata.formatOf(it) != null }.forEach { file ->
                val format = BookMetadata.formatOf(file)!!
                val relative = rootPath.relativize(file).invariantSeparatorsPathString
                val size = file.fileSize()
                val modified = file.getLastModifiedTime().toMillis()

                // Unchanged since last time: reuse the hash instead of re-reading the file.
                val cached = byPath[relative]?.takeIf { it.catalog.sizeBytes == size && it.modified == modified }
                val contentId = cached?.catalog?.contentId
                    ?: runCatching { file.inputStream().use(::contentIdOf) }.getOrNull()
                    ?: return@forEach
                seen += contentId

                val existing = store.book(contentId)
                when {
                    existing == null -> {
                        add(file, contentId, relative, format, size, modified, now)
                        added++
                    }
                    existing.relativePath != relative -> {
                        store.movePath(contentId, relative, modified)
                        moved++
                    }
                }
            }
        }

        val gone = known.filter { it.catalog.contentId !in seen }
        gone.forEach { store.markRemoved(it.catalog.contentId) }
        return ScanResult(added, moved, gone.size)
    }

    /**
     * Files a book uploaded from a phone into "Added on phone", unless the library already
     * has it. Returns the book either way.
     */
    @Synchronized
    fun addUpload(temp: Path, originalName: String, contentId: String, format: Format): CatalogBook {
        store.book(contentId)?.let {
            Files.deleteIfExists(temp)
            return it.catalog
        }
        val folder = root().resolve(UPLOAD_FOLDER).createDirectories()
        val target = uniqueName(folder, safeFileName(originalName, format))
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        val relative = root().relativize(target).invariantSeparatorsPathString
        add(target, contentId, relative, format, target.fileSize(), target.getLastModifiedTime().toMillis(), System.currentTimeMillis())
        return store.book(contentId)!!.catalog
    }

    fun file(book: LibraryBook): Path? {
        val rootPath = root().toAbsolutePath().normalize()
        val file = rootPath.resolve(book.relativePath).normalize()
        // Paths come from our own scan, but never serve anything outside the library.
        return file.takeIf { it.startsWith(rootPath) && it.exists() }
    }

    fun cover(contentId: String, size: CoverSize): Path? =
        coverDir.resolve("${contentId}_${size.suffix}.jpg").takeIf { it.exists() }

    enum class CoverSize(val suffix: String, val maxPx: Int) { THUMB("thumb", 480), FULL("full", 1400) }

    private fun add(file: Path, contentId: String, relative: String, format: Format, size: Long, modified: Long, now: Long) {
        val meta = BookMetadata.read(file, format)
        val etag = meta.cover?.let { image ->
            val thumb = BookMetadata.jpeg(image, CoverSize.THUMB.maxPx)
            coverDir.resolve("${contentId}_thumb.jpg").writeBytes(thumb)
            coverDir.resolve("${contentId}_full.jpg").writeBytes(BookMetadata.jpeg(image, CoverSize.FULL.maxPx))
            contentIdOf(thumb.inputStream()).take(16)
        }
        store.upsertBook(
            LibraryBook(
                catalog = CatalogBook(
                    contentId = contentId,
                    title = meta.title,
                    author = meta.author,
                    format = format,
                    sizeBytes = size,
                    rightToLeft = meta.rightToLeft,
                    coverEtag = etag,
                    addedAt = now,
                ),
                relativePath = relative,
                modified = modified,
            ),
        )
    }

    private fun uniqueName(folder: Path, name: String): Path {
        var candidate = folder.resolve(name)
        var n = 2
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.')
        while (candidate.exists()) candidate = folder.resolve("$base ($n).$ext").also { n++ }
        return candidate
    }

    companion object {
        const val UPLOAD_FOLDER = "Added on phone"

        /** A name that's safe on Windows: no separators, reserved characters or trailing dots. */
        fun safeFileName(original: String, format: Format): String {
            val ext = if (format == Format.PDF) "pdf" else "epub"
            // Plain string work: Windows' own path parser throws on exactly the characters
            // being cleaned out.
            val name = original.replace('\\', '/').substringAfterLast('/')
            val base = if (name.endsWith(".$ext", ignoreCase = true)) name.dropLast(ext.length + 1) else name
            val cleaned = base.replace(Regex("""[<>:"/\\|?*\x00-\x1F]"""), "_").trim().trimEnd('.').take(150)
            return "${cleaned.ifBlank { "book" }}.$ext"
        }
    }
}
