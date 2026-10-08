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

    /**
     * Extra copies of books already in the library (same bytes, another path), by content
     * id, from the last scan. They aren't separate books; the Duplicates check lists them.
     */
    @Volatile
    var identicalCopies: Map<String, List<String>> = emptyMap()
        private set

    /** Hashes of those extra copies by path, so rescans don't re-read them. */
    private val copyHashes = HashMap<String, Triple<Long, Long, String>>()

    @Synchronized
    fun scan(now: Long = System.currentTimeMillis()): ScanResult {
        val rootPath = root()
        if (!rootPath.isDirectory()) return ScanResult(0, 0, 0)

        val known = store.liveBooks()
        val byPath = known.associateBy { it.relativePath }
        val seen = HashSet<String>()
        val copies = HashMap<String, MutableList<String>>()
        var added = 0
        var moved = 0

        Files.walk(rootPath).use { paths ->
            paths.filter { it.isRegularFile() && BookMetadata.formatOf(it) != null && !isHidden(rootPath, it) }.forEach { file ->
                val format = BookMetadata.formatOf(file)!!
                val relative = rootPath.relativize(file).invariantSeparatorsPathString
                val size = file.fileSize()
                val modified = file.getLastModifiedTime().toMillis()

                // Unchanged since last time: reuse the hash instead of re-reading the file.
                val cached = byPath[relative]?.takeIf { it.catalog.sizeBytes == size && it.modified == modified }
                    ?.catalog?.contentId
                    ?: copyHashes[relative]?.takeIf { it.first == size && it.second == modified }?.third
                val contentId = cached
                    ?: runCatching { file.inputStream().use(::contentIdOf) }.getOrNull()
                    ?: return@forEach

                val existing = store.book(contentId)
                when {
                    existing == null -> {
                        add(file, contentId, relative, format, size, modified, now)
                        added++
                    }
                    existing.relativePath == relative -> Unit
                    // Same bytes at a second path while the first is still there: a copy, not a move.
                    contentId in seen || rootPath.resolve(existing.relativePath).isRegularFile() -> {
                        copies.getOrPut(contentId) { mutableListOf() } += relative
                        copyHashes[relative] = Triple(size, modified, contentId)
                    }
                    else -> {
                        store.movePath(contentId, relative, modified)
                        moved++
                    }
                }
                seen += contentId
            }
        }

        val gone = known.filter { it.catalog.contentId !in seen }
        gone.forEach { store.markRemoved(it.catalog.contentId) }
        identicalCopies = copies
        copyHashes.keys.retainAll(copies.values.flatten().toSet())
        return ScanResult(added, moved, gone.size)
    }

    private fun isHidden(root: Path, file: Path) =
        root.relativize(file).invariantSeparatorsPathString.split('/').any { it.startsWith('.') }

    // Organizing. Each keeps the store in step, so phones follow without waiting for a scan.

    /** Moves books into [folder] ("" for the top of the library); returns how many moved. */
    @Synchronized
    fun moveBooks(contentIds: Collection<String>, folder: String): Int {
        val target = folderPath(folder).createDirectories()
        var moved = 0
        for (id in contentIds) {
            val book = store.book(id) ?: continue
            val file = file(book) ?: continue
            if (file.parent == target) continue
            val destination = uniqueName(target, file.fileName.toString())
            Files.move(file, destination)
            store.movePath(id, relativeOf(destination), destination.getLastModifiedTime().toMillis())
            moved++
        }
        return moved
    }

    /** What [applyMoves] actually did, for undoing it. */
    data class AppliedMove(val contentId: String, val from: String, val to: String)

    /**
     * Carries out an auto-organize plan: each book to its new path (made unique if something
     * appeared there since planning). Folders left empty by the moves are removed. Books
     * that changed or vanished since planning are skipped.
     */
    @Synchronized
    fun applyMoves(moves: List<PlannedMove>): List<AppliedMove> {
        val applied = mutableListOf<AppliedMove>()
        val emptied = HashSet<Path>()
        for (move in moves) {
            val book = store.book(move.contentId) ?: continue
            if (book.relativePath != move.from) continue
            val file = file(book) ?: continue
            val wanted = pathInside(move.to) ?: continue
            wanted.parent.createDirectories()
            val destination = if (wanted.exists()) uniqueName(wanted.parent, wanted.fileName.toString()) else wanted
            Files.move(file, destination)
            store.movePath(move.contentId, relativeOf(destination), destination.getLastModifiedTime().toMillis())
            applied += AppliedMove(move.contentId, move.from, relativeOf(destination))
            emptied.add(file.parent)
        }
        removeEmptyFolders(emptied)
        return applied
    }

    /** Puts books back where [applied] found them. */
    @Synchronized
    fun undoMoves(applied: List<AppliedMove>) {
        val emptied = HashSet<Path>()
        for (move in applied.asReversed()) {
            val book = store.book(move.contentId) ?: continue
            if (book.relativePath != move.to) continue
            val file = file(book) ?: continue
            val back = pathInside(move.from) ?: continue
            back.parent.createDirectories()
            val destination = if (back.exists()) uniqueName(back.parent, back.fileName.toString()) else back
            Files.move(file, destination)
            store.movePath(move.contentId, relativeOf(destination), destination.getLastModifiedTime().toMillis())
            emptied.add(file.parent)
        }
        removeEmptyFolders(emptied)
    }

    /** Removes these folders, and parents up to the library, while they're completely empty. */
    private fun removeEmptyFolders(folders: Collection<Path>) {
        val rootPath = root().toAbsolutePath().normalize()
        folders.sortedByDescending { it.nameCount }.forEach { start ->
            var dir: Path? = start.toAbsolutePath().normalize()
            while (dir != null && dir != rootPath && dir.startsWith(rootPath) && dir.isDirectory() &&
                Files.list(dir).use { it.findAny().isEmpty }
            ) {
                Files.delete(dir)
                dir = dir.parent
            }
        }
    }

    /** Renames a book's file (the extension is kept); returns its new file name. */
    @Synchronized
    fun renameFile(contentId: String, newName: String): String {
        val book = store.book(contentId) ?: throw OrganizeException("That book is no longer in the library.")
        val file = file(book) ?: throw OrganizeException("The book's file can't be found.")
        val wanted = safeFileName(newName, book.catalog.format)
        if (wanted == file.fileName.toString()) return wanted
        val destination = if (wanted.equals(file.fileName.toString(), ignoreCase = true)) {
            file.resolveSibling(wanted) // Only the case changes: same file on Windows and macOS.
        } else {
            uniqueName(file.parent, wanted)
        }
        Files.move(file, destination)
        store.movePath(contentId, relativeOf(destination), destination.getLastModifiedTime().toMillis())
        return destination.fileName.toString()
    }

    /** Makes a folder inside [parent]; returns its path. */
    @Synchronized
    fun createFolder(parent: String, name: String): String {
        val clean = safeFolderName(name)
        val dir = folderPath(parent).resolve(clean)
        if (dir.exists()) throw OrganizeException("There's already a folder called “$clean” here.")
        dir.createDirectories()
        return relativeOf(dir)
    }

    /** Renames a folder in place; every book inside keeps its identity. Returns the new path. */
    @Synchronized
    fun renameFolder(path: String, newName: String): String {
        if (path.isEmpty()) throw OrganizeException("The library itself can't be renamed here.")
        val dir = folderPath(path)
        if (!dir.isDirectory()) throw OrganizeException("That folder no longer exists.")
        val clean = safeFolderName(newName)
        val destination = dir.resolveSibling(clean)
        val caseOnly = clean.equals(dir.fileName.toString(), ignoreCase = true)
        if (destination.exists() && !caseOnly) throw OrganizeException("There's already a folder called “$clean” here.")
        if (caseOnly) {
            // Windows and macOS see "fiction" and "Fiction" as the same name: go via a temporary one.
            val temp = dir.resolveSibling(".${clean}.preform-rename")
            Files.move(dir, temp)
            Files.move(temp, destination)
        } else {
            Files.move(dir, destination)
        }
        val newPath = relativeOf(destination)
        store.renamePathPrefix(path, newPath)
        return newPath
    }

    /**
     * Removes a folder. Its books (and sub-folders' books) move up into the parent first;
     * the folder itself is only deleted once nothing at all is left in it.
     */
    @Synchronized
    fun deleteFolder(path: String) {
        if (path.isEmpty()) throw OrganizeException("The library itself can't be deleted.")
        val dir = folderPath(path)
        if (!dir.isDirectory()) return
        val parent = path.substringBeforeLast('/', "")
        val inside = store.liveBooks().filter { it.catalog.folder == path || it.catalog.folder.startsWith("$path/") }
        moveBooks(inside.map { it.catalog.contentId }, parent)
        // Bottom-up: empty sub-folders first, then the folder. Anything else left keeps it.
        Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).toList() }.forEach { p ->
            if (p.isDirectory()) runCatching { Files.delete(p) }
        }
        if (dir.exists()) throw OrganizeException("The books moved out, but the folder still has other files in it, so it was kept.")
    }

    data class ImportResult(val added: Int, val alreadyThere: Int, val notBooks: Int)

    /** Copies books from anywhere into [folder]; books already in the library are skipped. */
    @Synchronized
    fun import(files: List<Path>, folder: String, now: Long = System.currentTimeMillis()): ImportResult {
        val target = folderPath(folder).createDirectories()
        var added = 0
        var already = 0
        var notBooks = 0
        for (source in files) {
            val format = BookMetadata.formatOf(source)
            if (format == null || !source.isRegularFile()) {
                notBooks++
                continue
            }
            val contentId = source.inputStream().use(::contentIdOf)
            if (store.book(store.resolve(contentId)) != null) {
                already++
                continue
            }
            val destination = uniqueName(target, safeFileName(source.fileName.toString(), format))
            Files.copy(source, destination)
            add(destination, contentId, relativeOf(destination), format, destination.fileSize(), destination.getLastModifiedTime().toMillis(), now)
            added++
        }
        return ImportResult(added, already, notBooks)
    }

    /**
     * Sends books to the Recycle Bin / Trash with [trash], never deleting outright; returns
     * how many went. A book whose file couldn't be moved there stays in the library.
     */
    @Synchronized
    fun trash(contentIds: Collection<String>, trash: (Path) -> Boolean = ::moveToTrash): Int {
        var trashed = 0
        for (id in contentIds) {
            val book = store.book(id) ?: continue
            val file = file(book)
            if (file != null && !trash(file)) continue
            store.markRemoved(id)
            trashed++
        }
        return trashed
    }

    /** Sends an extra identical copy (not itself a library book) to the Recycle Bin / Trash. */
    @Synchronized
    fun trashCopy(relativePath: String, trash: (Path) -> Boolean = ::moveToTrash): Boolean {
        val file = pathInside(relativePath) ?: return false
        if (!file.isRegularFile() || !trash(file)) return false
        identicalCopies = identicalCopies.mapValues { (_, paths) -> paths - relativePath }.filterValues { it.isNotEmpty() }
        copyHashes.remove(relativePath)
        return true
    }

    /** A folder of the library by relative path, refusing anything that would land outside it. */
    private fun folderPath(folder: String): Path {
        val rootPath = root().toAbsolutePath().normalize()
        val dir = rootPath.resolve(folder).normalize()
        if (!dir.startsWith(rootPath)) throw OrganizeException("That folder is outside the library.")
        return dir
    }

    private fun pathInside(relative: String): Path? {
        val rootPath = root().toAbsolutePath().normalize()
        return rootPath.resolve(relative).normalize().takeIf { it.startsWith(rootPath) }
    }

    private fun relativeOf(path: Path): String =
        root().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).invariantSeparatorsPathString

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
                    description = meta.description,
                    year = meta.year,
                    series = meta.series,
                    seriesIndex = meta.seriesIndex,
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

    /** Saves a cover (found online, or chosen by hand); returns its etag. */
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

        /** A folder name that's safe on Windows and macOS. */
        fun safeFolderName(name: String): String {
            val cleaned = name.replace(Regex("""[<>:"/\\|?*\x00-\x1F]"""), "_").trim().trimEnd('.').trimStart('.').take(120)
            if (cleaned.isBlank()) throw OrganizeException("Give the folder a name.")
            return cleaned
        }

        /** [safeFolderName], or null for a missing or unusable name. */
        fun safeFolderNameOrNull(name: String?): String? = name?.let { runCatching { safeFolderName(it) }.getOrNull() }

        /** Moves a file to the Recycle Bin (Windows) or Trash (macOS); false if that isn't possible. */
        fun moveToTrash(file: Path): Boolean = runCatching {
            val desktop = java.awt.Desktop.getDesktop()
            desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) && desktop.moveToTrash(file.toFile())
        }.getOrDefault(false)

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
