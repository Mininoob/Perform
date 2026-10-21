package com.perform.reader.library

import com.perform.sync.OrganizeChange
import com.perform.sync.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Organizing on the phone: shelves change at once, and each library gets its own changes, in order. */
class OrganizerTest {

    /** Just enough of the database for organizing, in memory. */
    private class Books(vararg initial: BookEntity) : BookDao {
        val rows = initial.associateBy { it.id }.toMutableMap()
        val members = mutableListOf<LibraryBookEntity>()
        val folderRows = mutableMapOf<Pair<String, String>, FolderEntity>()
        val queue = mutableListOf<PendingChangeEntity>()

        fun member(library: String, bookId: Long, folder: String) {
            members.removeAll { it.serverId == library && it.bookId == bookId }
            members += LibraryBookEntity(library, bookId, folder)
        }

        fun folderIn(library: String, bookId: Long) = members.single { it.serverId == library && it.bookId == bookId }

        override fun observeMemberships(): Flow<List<LibraryBookEntity>> = flowOf(members.toList())
        override suspend fun memberships(bookId: Long) = members.filter { it.bookId == bookId }
        override suspend fun membershipsIn(serverId: String) = members.filter { it.serverId == serverId }
        override suspend fun saveMembership(membership: LibraryBookEntity) = member(membership.serverId, membership.bookId, membership.folder)
        override suspend fun removeMembership(serverId: String, bookId: Long) { members.removeAll { it.serverId == serverId && it.bookId == bookId } }
        override suspend fun removeMemberships(serverId: String) { members.removeAll { it.serverId == serverId } }
        override suspend fun orphanServerBooks() = rows.values.filter { b -> b.source == BookSource.SERVER && members.none { it.bookId == b.id } }
        override suspend fun setMembershipFolder(serverId: String, bookIds: List<Long>, folder: String, dirty: Boolean) {
            members.replaceAll { if (it.serverId == serverId && it.bookId in bookIds) it.copy(folder = folder, folderDirty = dirty) else it }
        }
        override suspend fun dirtyMemberships(serverId: String) = members.filter { it.serverId == serverId && it.folderDirty }.map { it.bookId }
        override suspend fun clearMembershipDirty(serverId: String, bookIds: List<Long>) {
            members.replaceAll { if (it.serverId == serverId && it.bookId in bookIds) it.copy(folderDirty = false) else it }
        }
        override suspend fun setShelf(ids: List<Long>, shelf: String) = ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(shelf = shelf) } }
        override suspend fun importsUnder(path: String) =
            rows.values.filter { it.source == BookSource.IMPORTED && (it.shelf == path || it.shelf.startsWith("$path/")) }
        override suspend fun membershipsUnder(serverId: String, path: String) =
            members.filter { it.serverId == serverId && (it.folder == path || it.folder.startsWith("$path/")) }
        override fun observeFolders(): Flow<List<FolderEntity>> = flowOf(folderRows.values.toList())
        override suspend fun folders() = folderRows.values.toList()
        override suspend fun saveFolder(folder: FolderEntity) { folderRows[folder.serverId to folder.path] = folder }
        override suspend fun removeFolders(serverId: String) { folderRows.keys.removeAll { it.first == serverId } }
        override suspend fun queue(change: PendingChangeEntity): Long { queue += change.copy(id = queue.size + 1L); return queue.size.toLong() }
        override suspend fun pending(serverId: String) = queue.filter { it.serverId == serverId }
        override suspend fun clearPending(ids: List<Long>) { queue.removeAll { it.id in ids } }
        override suspend fun clearPendingFor(serverId: String) { queue.removeAll { it.serverId == serverId } }
        override suspend fun bySource(source: BookSource) = rows.values.filter { it.source == source }

        override fun observeVisible() = flowOf(rows.values.toList())
        override fun observe(id: Long) = flowOf(rows[id])
        override suspend fun get(id: Long) = rows[id]
        override suspend fun findImport(title: String, size: Long): BookEntity? = null
        override suspend fun insert(book: BookEntity): Long = TODO()
        override suspend fun update(book: BookEntity) { rows[book.id] = book }
        override suspend fun delete(ids: List<Long>) = ids.forEach { rows.remove(it) }
        override suspend fun updatePosition(id: Long, locatorJson: String, progression: Double, openedAt: Long) = Unit
        override suspend fun markOpened(id: Long, openedAt: Long) = Unit
        override suspend fun updateOverride(id: Long, json: String?) = Unit
        override suspend fun setHidden(id: Long, hidden: Boolean) = Unit
        override suspend fun setBookMode(id: Long, on: Boolean) = Unit
        override fun observeServerBooks() = flowOf(rows.values.filter { it.source == BookSource.SERVER })
        override suspend fun serverBooks() = rows.values.filter { it.source == BookSource.SERVER }
        override suspend fun serverBook(contentId: String) = rows.values.firstOrNull { it.source == BookSource.SERVER && it.contentId == contentId }
        override suspend fun setLocalPath(id: Long, path: String?) = Unit
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun localBooksAwaitingLookup(limit: Int) = emptyList<BookEntity>()
        override suspend fun localBooksToHash(limit: Int) = emptyList<BookEntity>()
        override suspend fun setHash(id: Long, contentId: String, size: Long, modified: Long) = Unit
        override suspend fun localBooksToUpload(limit: Int) = emptyList<BookEntity>()
        override suspend fun localBook(contentId: String): BookEntity? = null
        override suspend fun setOnPc(id: Long) = Unit
        override suspend fun serverBooksToDownload() = emptyList<BookEntity>()

        fun changes(library: String) = queue.filter { it.serverId == library }.map { Protocol.json.decodeFromString(OrganizeChange.serializer(), it.json) }
    }

    private fun book(id: Long, shelf: String = "", source: BookSource = BookSource.SERVER) = BookEntity(
        id = id,
        uri = "perform-server:id$id",
        source = source,
        format = BookFormat.EPUB,
        title = "Book $id",
        author = null,
        sizeBytes = 1,
        lastModified = 0,
        coverThumbPath = null,
        coverFullPath = null,
        coverColor = null,
        rightToLeft = false,
        addedAt = 0,
        contentId = "id$id",
        shelf = shelf,
    )

    private var now = 1_000L

    private fun organizer(dao: Books, paired: Set<String> = setOf("pc", "mac")) = LibraryOrganizer(dao, paired = { paired }, clock = { now++ })

    @Test
    fun `moving a book inside one library changes its folder there and queues the move for that library only`() = runBlocking {
        val dao = Books(book(1), book(3, "Phone", BookSource.FOLDER))
        dao.member("pc", 1, "Fiction")
        dao.member("mac", 1, "Novels")
        val moved = organizer(dao).move(dao.rows.values.toList(), "Classics", library = "pc")
        assertEquals("Folder books follow their real folder", 1, moved)
        assertEquals("Classics", dao.folderIn("pc", 1).folder)
        assertTrue(dao.folderIn("pc", 1).folderDirty)
        assertEquals("Novels", dao.folderIn("mac", 1).folder)
        assertEquals(listOf("id1"), dao.changes("pc").map { (it as OrganizeChange.BookFolder).contentId })
        assertTrue(dao.changes("mac").isEmpty())
        assertEquals("Phone", dao.rows[3]!!.shelf)
    }

    @Test
    fun `a move from All books goes to every library that has the book`() = runBlocking {
        val dao = Books(book(1))
        dao.member("pc", 1, "Fiction")
        dao.member("mac", 1, "Novels")
        organizer(dao).move(listOf(dao.rows[1]!!), "Classics", library = null)
        assertEquals("Classics", dao.folderIn("pc", 1).folder)
        assertEquals("Classics", dao.folderIn("mac", 1).folder)
        assertEquals(1, dao.changes("pc").size)
        assertEquals(1, dao.changes("mac").size)
    }

    @Test
    fun `imported books move on the phone and aren't sent`() = runBlocking {
        val dao = Books(book(1, "", BookSource.IMPORTED))
        organizer(dao).move(dao.rows.values.toList(), "Poetry", library = "pc")
        assertEquals("Poetry", dao.rows[1]!!.shelf)
        assertTrue(dao.queue.isEmpty())
    }

    @Test
    fun `a new folder belongs to its library, shows as an empty shelf there and is sent`() = runBlocking {
        val dao = Books(book(1))
        dao.member("pc", 1, "Fiction")
        val path = organizer(dao).makeFolder("pc", "Fiction", "  To Read ")
        assertEquals("Fiction/To Read", path)
        val folders = dao.folderRows.values.filter { it.serverId == "pc" }.map { it.path }
        val page = folderPage(dao.rows.values.toList(), "Fiction", folders, "pc|") { b -> dao.members.firstOrNull { m -> m.bookId == b.id && m.serverId == "pc" }?.folder }
        assertEquals(listOf("pc|Fiction/To Read"), page.shelves.map { it.key })
        assertTrue(page.shelves.single().books.isEmpty())
        assertEquals(OrganizeChange.Folder("Fiction/To Read", removed = false, at = dao.changes("pc").single().at), dao.changes("pc").single())
        assertTrue(dao.changes("mac").isEmpty())
    }

    @Test
    fun `renaming a folder carries its books and sub-folders in that library, sent as one rename`() = runBlocking {
        val dao = Books(book(1), book(2), book(3))
        dao.member("pc", 1, "Fiction")
        dao.member("pc", 2, "Fiction/Fantasy")
        dao.member("pc", 3, "Fictional")
        dao.member("mac", 1, "Fiction")
        organizer(dao).renameFolder("pc", "Fiction", "Novels")
        assertEquals("Novels", dao.folderIn("pc", 1).folder)
        assertEquals("Novels/Fantasy", dao.folderIn("pc", 2).folder)
        assertEquals("A name that only starts the same is left alone", "Fictional", dao.folderIn("pc", 3).folder)
        assertEquals("Another library's folder of the same name is its own", "Fiction", dao.folderIn("mac", 1).folder)
        val rename = dao.changes("pc").single() as OrganizeChange.FolderRename
        assertEquals("Fiction" to "Novels", rename.from to rename.to)
    }

    @Test
    fun `removing a folder moves its books up a level`() = runBlocking {
        val dao = Books(book(1), book(2))
        dao.member("pc", 1, "Fiction/Fantasy")
        dao.member("pc", 2, "Fiction/Fantasy/Epic")
        organizer(dao).removeFolder("pc", "Fiction/Fantasy")
        assertEquals("Fiction", dao.folderIn("pc", 1).folder)
        assertEquals("Fiction", dao.folderIn("pc", 2).folder)
        assertTrue(dao.folderRows["pc" to "Fiction/Fantasy"]!!.removed)
        assertEquals(OrganizeChange.Folder("Fiction/Fantasy", removed = true, at = dao.changes("pc").single().at), dao.changes("pc").single())
    }

    @Test
    fun `nothing is queued for a library that isn't paired, and names are made safe`() = runBlocking {
        val dao = Books(book(1, ""))
        organizer(dao, paired = emptySet()).makeFolder("pc", "", "Sci/Fi?")
        assertTrue(dao.queue.isEmpty())
        assertTrue(dao.folderRows.containsKey("pc" to "Sci_Fi_"))
        assertTrue(runCatching { organizer(dao).makeFolder("pc", "", "  ..  ") }.exceptionOrNull() is OrganizeError)
        assertTrue("Names match whatever the case", runCatching { organizer(dao).makeFolder("pc", "", "sci_fi_") }.exceptionOrNull() is OrganizeError)
    }

    @Test
    fun `move to lists every folder once, parents included`() {
        assertEquals(listOf("Empty", "Fiction", "Fiction/Fantasy", "Science"), allFolders(listOf("Fiction/Fantasy", "Science", "Empty", "Fiction")))
    }
}
