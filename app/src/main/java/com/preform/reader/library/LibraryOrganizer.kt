package com.preform.reader.library

import com.preform.sync.OrganizeChange
import com.preform.sync.Protocol

/**
 * Organizing on the phone: folders made, renamed and removed, books moved between them.
 * Each change shows at once and waits in a queue for the PC, which moves the real files
 * on the next sync (newest change wins there). Folder books aren't moved: they follow
 * their real folder, which Preform may only read.
 */
class LibraryOrganizer(
    private val dao: BookDao,
    /** Whether a PC is paired, so changes are worth queuing for one. */
    private val paired: suspend () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Moves [books] to [folder] ("" for the top); returns how many moved. */
    suspend fun move(books: List<BookEntity>, folder: String): Int {
        val target = clean(folder)
        val moving = books.filter { it.movable && it.shelf != target }
        if (moving.isEmpty()) return 0
        dao.setShelf(moving.map { it.id }, target, dirty = true)
        if (target.isNotEmpty()) dao.saveFolder(FolderEntity(target, removed = false, at = clock()))
        moving.filter { it.source == BookSource.SERVER }.forEach { book ->
            queue(OrganizeChange.BookFolder(book.contentId!!, target, clock()))
        }
        return moving.size
    }

    /** Makes a folder inside [parent]; returns its path. */
    suspend fun makeFolder(parent: String, name: String): String {
        val path = join(clean(parent), cleanName(name))
        // Windows and macOS see "Fiction" and "fiction" as one folder.
        if (existing().any { it.equals(path, ignoreCase = true) }) throw OrganizeError("There's already a folder called “${cleanName(name)}” here.")
        dao.saveFolder(FolderEntity(path, removed = false, at = clock()))
        queue(OrganizeChange.Folder(path, removed = false, at = clock()))
        return path
    }

    /** Renames a folder in place; its books and sub-folders go with it. Returns the new path. */
    suspend fun renameFolder(path: String, newName: String): String {
        val from = clean(path)
        val to = join(from.substringBeforeLast('/', ""), cleanName(newName))
        if (to == from) return from
        if (existing().any { it.equals(to, ignoreCase = true) && !it.equals(from, ignoreCase = true) }) {
            throw OrganizeError("There's already a folder called “${cleanName(newName)}” here.")
        }
        val now = clock()
        dao.movableUnder(from).groupBy { it.shelf }.forEach { (shelf, books) ->
            dao.setShelf(books.map { it.id }, to + shelf.removePrefix(from), dirty = true)
        }
        dao.folders().filter { !it.removed && (it.path == from || it.path.startsWith("$from/")) }.forEach {
            dao.saveFolder(it.copy(removed = true, at = now))
            dao.saveFolder(FolderEntity(to + it.path.removePrefix(from), removed = false, at = now))
        }
        dao.saveFolder(FolderEntity(to, removed = false, at = now))
        queue(OrganizeChange.FolderRename(from, to, now))
        return to
    }

    /** Removes a folder; its books move up into the parent, as on the computer. */
    suspend fun removeFolder(path: String) {
        val gone = clean(path)
        if (gone.isEmpty()) return
        val parent = gone.substringBeforeLast('/', "")
        val now = clock()
        val books = dao.movableUnder(gone)
        if (books.isNotEmpty()) dao.setShelf(books.map { it.id }, parent, dirty = true)
        dao.folders().filter { !it.removed && (it.path == gone || it.path.startsWith("$gone/")) }.forEach {
            dao.saveFolder(it.copy(removed = true, at = now))
        }
        dao.saveFolder(FolderEntity(gone, removed = true, at = now))
        queue(OrganizeChange.Folder(gone, removed = true, at = now))
    }

    /** Every folder there is: from books' shelves and from folders made here or on the PC. */
    private suspend fun existing(): Set<String> {
        val fromBooks = dao.bySource(BookSource.SERVER) + dao.bySource(BookSource.IMPORTED) + dao.bySource(BookSource.FOLDER)
        return (fromBooks.flatMap { prefixes(it.shelf) } + dao.folders().filter { !it.removed }.map { it.path }).toSet()
    }

    private suspend fun queue(change: OrganizeChange) {
        if (!paired()) return
        dao.queue(PendingChangeEntity(json = Protocol.json.encodeToString(OrganizeChange.serializer(), change)))
    }

    companion object {
        /** "Fiction/Fantasy" → ["Fiction", "Fiction/Fantasy"]. */
        fun prefixes(path: String): List<String> =
            path.split('/').filter(String::isNotBlank).runningReduce { a, b -> "$a/$b" }

        fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

        /** A folder name the PC will accept as it is: no slashes, no reserved characters, no leading dots. */
        fun cleanName(name: String): String {
            val cleaned = name.replace(Regex("""[<>:"/\\|?*\x00-\x1F]"""), "_").trim().trim('.').take(120)
            if (cleaned.isBlank()) throw OrganizeError("Give the folder a name.")
            return cleaned
        }

        fun clean(path: String): String = path.split('/').filter(String::isNotBlank).joinToString("/") { cleanName(it) }
    }
}

/** Something about a folder change that the person can fix. */
class OrganizeError(message: String) : Exception(message)
