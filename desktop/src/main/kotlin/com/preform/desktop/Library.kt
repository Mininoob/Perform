package com.preform.desktop

import com.preform.sync.CatalogBook
import com.preform.sync.Format
import com.preform.sync.Genres
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
     * has it (perhaps under a newer id, after information was written into it). Returns
     * the book either way.
     */
    @Synchronized
    fun addUpload(temp: Path, originalName: String, contentId: String, format: Format): CatalogBook {
        store.book(store.resolve(contentId))?.let {
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

    /** Every folder under the library ("Fiction/Fantasy"), empty ones too; hidden ones skipped. */
    fun folders(): List<String> {
        val rootPath = root()
        if (!rootPath.isDirectory()) return emptyList()
        return Files.walk(rootPath).use { paths ->
            paths.filter { it != rootPath && it.isDirectory() }
                .map { rootPath.relativize(it).invariantSeparatorsPathString }
                .filter { path -> path.split('/').none { it.startsWith('.') } }
                .toList()
        }.sortedBy { it.lowercase() }
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
        val genres = Genres.classify(meta.subjects)
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
                    genres = genres,
                ),
                relativePath = relative,
                modified = modified,
                // No genres in the book itself: left for the online lookup.
                genreSource = if (genres.isNotEmpty()) "book" else null,
                titleFromFile = meta.titleFromFile,
            ),
        )
    }

    /**
     * Writes [fill] into the book's file, keeping the original in [backups]. The new file
     * is built beside the old one, read back to make sure it's still a good book, then
     * swapped in; the book moves to its new id. Returns false if nothing was written.
     */
    @Synchronized
    fun inject(contentId: String, fill: BookFill, backups: Path): Boolean {
        val book = store.book(contentId) ?: return false
        val file = file(book) ?: return false
        val format = book.catalog.format
        val temp = file.resolveSibling(".${file.fileName}.preform-tmp")
        try {
            val wrote = when (format) {
                Format.EPUB -> BookWriter.writeEpub(file, fill, temp)
                Format.PDF -> BookWriter.writePdf(file, fill, temp)
            }
            if (!wrote) return false
            BookMetadata.readOrNull(temp, format) ?: return false

            backups.createDirectories()
            val backup = backups.resolve("$contentId.${extensionOf(format)}")
            if (!backup.exists()) Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES)

            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            moveTo(contentId, file)
            return true
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    /**
     * Puts back every original kept in [backups], and returns how many. Each book returns
     * to its original id, so phones and reading progress follow it back.
     */
    @Synchronized
    fun restoreOriginals(backups: Path): Int {
        if (!backups.isDirectory()) return 0
        var restored = 0
        Files.list(backups).use { it.toList() }.forEach { backup ->
            val originalId = backup.fileName.toString().substringBefore('.')
            val currentId = store.resolve(originalId)
            val book = store.book(currentId)
            val file = book?.let(::file)
            if (currentId == originalId || file == null) {
                // The book left the library, or was never rewritten: nothing to put back.
                Files.deleteIfExists(backup)
                return@forEach
            }
            val temp = file.resolveSibling(".${file.fileName}.preform-tmp")
            try {
                Files.copy(backup, temp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally {
                Files.deleteIfExists(temp)
            }
            moveTo(currentId, file)
            Files.deleteIfExists(backup)
            restored++
        }
        return restored
    }

    /** The file at [file] now holds different bytes: re-key the book from [oldId] to their id. */
    private fun moveTo(oldId: String, file: Path) {
        val newId = file.inputStream().use(::contentIdOf)
        if (newId == oldId) return
        store.rekey(oldId, newId, file.fileSize(), file.getLastModifiedTime().toMillis())
        CoverSize.entries.forEach { size ->
            val from = coverDir.resolve("${oldId}_${size.suffix}.jpg")
            if (from.exists()) Files.move(from, coverDir.resolve("${newId}_${size.suffix}.jpg"), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Saves a cover found online for a book that had none; returns its etag. */
    fun saveCover(contentId: String, jpeg: ByteArray): String? {
        val image = runCatching { javax.imageio.ImageIO.read(jpeg.inputStream()) }.getOrNull() ?: return null
        val thumb = BookMetadata.jpeg(image, CoverSize.THUMB.maxPx)
        coverDir.resolve("${contentId}_thumb.jpg").writeBytes(thumb)
        coverDir.resolve("${contentId}_full.jpg").writeBytes(BookMetadata.jpeg(image, CoverSize.FULL.maxPx))
        return contentIdOf(thumb.inputStream()).take(16)
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

        fun extensionOf(format: Format) = if (format == Format.PDF) "pdf" else "epub"

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
