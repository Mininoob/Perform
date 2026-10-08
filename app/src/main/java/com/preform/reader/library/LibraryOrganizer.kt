package com.preform.reader.library

import com.preform.sync.OrganizeChange
import com.preform.sync.Protocol

/**
 * Organizing on the phone: folders made, renamed and removed, books moved between them.
 * Each change shows at once and waits in a queue for the library it belongs to, which
 * moves the real files on the next sync (newest change wins there). [library] is a paired
 * library's id, or "" for the phone's own books. Folder books aren't moved: they follow
 * their real folder, which Preform may only read.
 */
class LibraryOrganizer(
    private val dao: BookDao,
    /** The paired libraries' ids, so changes are only queued for ones that exist. */
    private val paired: suspend () -> Set<String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Moves [books] to [folder] ("" for the top) in [library], or, with null, in every
     * library each book is in. Imports move on the phone. Returns how many moved.
     */
    suspend fun move(books: List<BookEntity>, folder: String, library: String? = null): Int {
        val target = clean(folder)
        val now = clock()
        var moved = 0
        for (book in books) {
            when (book.source) {
                BookSource.FOLDER -> Unit
                BookSource.IMPORTED -> if (book.shelf != target) {
                    dao.setShelf(listOf(book.id), target)
                    if (target.isNotEmpty()) dao.saveFolder(FolderEntity(PHONE, target, removed = false, at = now))
                    moved++
                }
                BookSource.SERVER -> {
                    val memberships = dao.memberships(book.id).filter { (library == null || it.serverId == library) && it.folder != target }
                    memberships.forEach { m ->
                        dao.setMembershipFolder(m.serverId, listOf(book.id), target, dirty = true)
                        if (target.isNotEmpty()) dao.saveFolder(FolderEntity(m.serverId, target, removed = false, at = now))
                        queue(m.serverId, OrganizeChange.BookFolder(book.contentId!!, target, now))
                    }
                    if (memberships.isNotEmpty()) moved++
                }
            }
        }
        return moved
    }

    /** Makes a folder inside [parent] of [library]; returns its path. */
    suspend fun makeFolder(library: String, parent: String, name: String): String {
        val path = join(clean(parent), cleanName(name))
        // Windows and macOS see "Fiction" and "fiction" as one folder.
        if (existing(library).any { it.equals(path, ignoreCase = true) }) throw OrganizeError("There's already a folder called “${cleanName(name)}” here.")
        val now = clock()
        dao.saveFolder(FolderEntity(library, path, removed = false, at = now))
        queue(library, OrganizeChange.Folder(path, removed = false, at = now))
        return path
    }

    /**
     * Renames a folder of [library] in place; its books and sub-folders go with it. With
     * [withPhone], the phone's imports in a folder of the same name follow (one library,
     * shown as one tree). Returns the new path.
     */
    suspend fun renameFolder(library: String, path: String, newName: String, withPhone: Boolean = false): String {
        val from = clean(path)
        val to = join(from.substringBeforeLast('/', ""), cleanName(newName))
        if (to == from) return from
        if (existing(library).any { it.equals(to, ignoreCase = true) && !it.equals(from, ignoreCase = true) }) {
            throw OrganizeError("There's already a folder called “${cleanName(newName)}” here.")
        }
        val now = clock()
        if (library != PHONE) {
            dao.membershipsUnder(library, from).groupBy { it.folder }.forEach { (folder, members) ->
                dao.setMembershipFolder(library, members.map { it.bookId }, to + folder.removePrefix(from), dirty = true)
            }
        }
        if (library == PHONE || withPhone) {
            dao.importsUnder(from).groupBy { it.shelf }.forEach { (shelf, books) -> dao.setShelf(books.map { it.id }, to + shelf.removePrefix(from)) }
        }
        dao.folders().filter { it.serverId == library && !it.removed && (it.path == from || it.path.startsWith("$from/")) }.forEach {
            dao.saveFolder(it.copy(removed = true, at = now))
            dao.saveFolder(FolderEntity(library, to + it.path.removePrefix(from), removed = false, at = now))
        }
        dao.saveFolder(FolderEntity(library, to, removed = false, at = now))
        queue(library, OrganizeChange.FolderRename(from, to, now))
        return to
    }

    /** Removes a folder of [library]; its books move up into the parent, as on the computer. */
    suspend fun removeFolder(library: String, path: String, withPhone: Boolean = false) {
        val gone = clean(path)
        if (gone.isEmpty()) return
        val parent = gone.substringBeforeLast('/', "")
        val now = clock()
        if (library != PHONE) {
            val members = dao.membershipsUnder(library, gone)
            if (members.isNotEmpty()) dao.setMembershipFolder(library, members.map { it.bookId }, parent, dirty = true)
        }
        if (library == PHONE || withPhone) {
            val imports = dao.importsUnder(gone)
            if (imports.isNotEmpty()) dao.setShelf(imports.map { it.id }, parent)
        }
        dao.folders().filter { it.serverId == library && !it.removed && (it.path == gone || it.path.startsWith("$gone/")) }.forEach {
            dao.saveFolder(it.copy(removed = true, at = now))
        }
        dao.saveFolder(FolderEntity(library, gone, removed = true, at = now))
        queue(library, OrganizeChange.Folder(gone, removed = true, at = now))
    }

    /** Every folder [library] has: from its books' folders and from folders made here or there. */
    private suspend fun existing(library: String): Set<String> {
        val fromBooks = if (library == PHONE) {
            (dao.bySource(BookSource.IMPORTED) + dao.bySource(BookSource.FOLDER)).map { it.shelf }
        } else {
            dao.membershipsIn(library).map { it.folder }
        }
        val made = dao.folders().filter { it.serverId == library && !it.removed }.map { it.path }
        return (fromBooks.flatMap(::prefixes) + made).toSet()
    }

    private suspend fun queue(library: String, change: OrganizeChange) {
        if (library == PHONE || library !in paired()) return
        dao.queue(PendingChangeEntity(serverId = library, json = Protocol.json.encodeToString(OrganizeChange.serializer(), change)))
    }

    companion object {
        /** The phone's own books, as a "library" of their own. */
        const val PHONE = ""

        /** "Fiction/Fantasy" → ["Fiction", "Fiction/Fantasy"]. */
        fun prefixes(path: String): List<String> =
            path.split('/').filter(String::isNotBlank).runningReduce { a, b -> "$a/$b" }

        fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

        /** A folder name every computer will accept as it is: no slashes, no reserved characters, no leading dots. */
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
