package com.preform.reader.library

import com.preform.sync.OrganizeChange
import com.preform.sync.Protocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Organizing on the phone: shelves change at once, and the PC gets the same changes, in order. */
class OrganizerTest {

    /** Just enough of the database for organizing, in memory. */
    private class Books(vararg initial: BookEntity) : BookDao {
        val rows = initial.associateBy { it.id }.toMutableMap()
        val folderRows = mutableMapOf<String, FolderEntity>()
        val queue = mutableListOf<PendingChangeEntity>()

        override suspend fun setShelf(ids: List<Long>, shelf: String, dirty: Boolean) {
            ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(shelf = shelf, shelfDirty = dirty) } }
        }
        override suspend fun movableUnder(path: String) =
            rows.values.filter { it.source != BookSource.FOLDER && (it.shelf == path || it.shelf.startsWith("$path/")) }
        override suspend fun dirtyShelves() = rows.values.filter { it.shelfDirty }.map { it.id }
        override suspend fun clearShelfDirty(ids: List<Long>) = ids.forEach { id -> rows[id]?.let { rows[id] = it.copy(shelfDirty = false) } }
        override fun observeFolders(): Flow<List<FolderEntity>> = flowOf(folderRows.values.toList())
        override suspend fun folders() = folderRows.values.toList()
        override suspend fun saveFolder(folder: FolderEntity) { folderRows[folder.path] = folder }
        override suspend fun queue(change: PendingChangeEntity): Long { queue += change.copy(id = queue.size + 1L); return queue.size.toLong() }
        override suspend fun pending() = queue.toList()
        override suspend fun clearPending(ids: List<Long>) { queue.removeAll { it.id in ids } }
        override suspend fun clearAllPending() = queue.clear()
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
        override fun observeServerBooks() = flowOf(bySourceNow(BookSource.SERVER))
        override suspend fun serverBooks() = bySourceNow(BookSource.SERVER)
        override suspend fun serverBook(contentId: String) = rows.values.firstOrNull { it.contentId == contentId }
        override suspend fun setLocalPath(id: Long, path: String?) = Unit
        override suspend fun setPinned(id: Long, pinned: Boolean) = Unit
        override suspend fun localBooksAwaitingLookup(limit: Int) = emptyList<BookEntity>()

        private fun bySourceNow(source: BookSource) = rows.values.filter { it.source == source }

        fun changes() = queue.map { Protocol.json.decodeFromString(OrganizeChange.serializer(), it.json) }
    }

    private fun book(id: Long, shelf: String, source: BookSource = BookSource.SERVER) = BookEntity(
        id = id,
        uri = "preform-server:id$id",
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

    private fun organizer(dao: Books, paired: Boolean = true) = LibraryOrganizer(dao, paired = { paired }, clock = { now++ })

    @Test
    fun `moving PC books changes their shelf here and queues the move for the PC`() = runBlocking {
        val dao = Books(book(1, "Fiction"), book(2, ""), book(3, "Phone", BookSource.FOLDER))
        val moved = organizer(dao).move(dao.rows.values.toList(), "Classics")
        assertEquals("Folder books follow their real folder", 2, moved)
        assertEquals("Classics", dao.rows[1]!!.shelf)
        assertTrue(dao.rows[1]!!.shelfDirty)
        assertEquals("Phone", dao.rows[3]!!.shelf)
        assertEquals(listOf("id1", "id2"), dao.changes().map { (it as OrganizeChange.BookFolder).contentId })
        assertTrue(dao.folderRows["Classics"]?.removed == false)
    }

    @Test
    fun `imported books move too, but only PC books are sent`() = runBlocking {
        val dao = Books(book(1, "", BookSource.IMPORTED))
        organizer(dao).move(dao.rows.values.toList(), "Poetry")
        assertEquals("Poetry", dao.rows[1]!!.shelf)
        assertTrue(dao.changes().isEmpty())
    }

    @Test
    fun `a new folder shows as an empty shelf and is sent`() = runBlocking {
        val dao = Books(book(1, "Fiction"))
        val path = organizer(dao).makeFolder("Fiction", "  To Read ")
        assertEquals("Fiction/To Read", path)
        val page = folderPage(dao.rows.values.toList(), "Fiction", dao.folderRows.keys)
        assertEquals(listOf("To Read"), page.shelves.map { it.name })
        assertTrue(page.shelves.single().books.isEmpty())
        assertEquals(OrganizeChange.Folder("Fiction/To Read", removed = false, at = dao.changes().single().at), dao.changes().single())
    }

    @Test
    fun `renaming a folder carries its books and sub-folders, and is sent as one rename`() = runBlocking {
        val dao = Books(book(1, "Fiction"), book(2, "Fiction/Fantasy"), book(3, "Fictional"))
        organizer(dao).renameFolder("Fiction", "Novels")
        assertEquals("Novels", dao.rows[1]!!.shelf)
        assertEquals("Novels/Fantasy", dao.rows[2]!!.shelf)
        assertEquals("A name that only starts the same is left alone", "Fictional", dao.rows[3]!!.shelf)
        val rename = dao.changes().single() as OrganizeChange.FolderRename
        assertEquals("Fiction" to "Novels", rename.from to rename.to)
    }

    @Test
    fun `removing a folder moves its books up a level`() = runBlocking {
        val dao = Books(book(1, "Fiction/Fantasy"), book(2, "Fiction/Fantasy/Epic"))
        organizer(dao).removeFolder("Fiction/Fantasy")
        assertEquals("Fiction", dao.rows[1]!!.shelf)
        assertEquals("Fiction", dao.rows[2]!!.shelf)
        assertTrue(dao.folderRows["Fiction/Fantasy"]!!.removed)
        assertEquals(OrganizeChange.Folder("Fiction/Fantasy", removed = true, at = dao.changes().single().at), dao.changes().single())
    }

    @Test
    fun `nothing is queued without a PC, and names are made safe`() = runBlocking {
        val dao = Books(book(1, ""))
        organizer(dao, paired = false).makeFolder("", "Sci/Fi?")
        assertTrue(dao.queue.isEmpty())
        assertTrue(dao.folderRows.containsKey("Sci_Fi_"))
        assertTrue(runCatching { organizer(dao).makeFolder("", "  ..  ") }.exceptionOrNull() is OrganizeError)
        assertTrue("Names match whatever the case", runCatching { organizer(dao).makeFolder("", "sci_fi_") }.exceptionOrNull() is OrganizeError)
    }

    @Test
    fun `move to lists every folder once, parents included`() {
        val books = listOf(book(1, "Fiction/Fantasy"), book(2, "Science"))
        assertEquals(listOf("Empty", "Fiction", "Fiction/Fantasy", "Science"), allFolders(books, listOf("Empty", "Fiction")))
    }
}
