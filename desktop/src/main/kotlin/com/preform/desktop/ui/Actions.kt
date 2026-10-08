package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.preform.desktop.LibraryBook
import com.preform.desktop.LibraryService
import com.preform.desktop.OrganizeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/** A short message at the bottom of the window, with Undo when the step can be reversed. */
data class Notice(val text: String, val undo: (suspend () -> Unit)? = null, val id: Long = System.nanoTime())

/** A dialog the window is showing. */
sealed interface Dialog {
    data object Organize : Dialog
    data class Edit(val book: LibraryBook) : Dialog
    data class MoveTo(val books: List<LibraryBook>) : Dialog
    data class RenameFile(val book: LibraryBook) : Dialog
    data class NewFolder(val parent: String) : Dialog
    data class RenameFolder(val path: String) : Dialog
    data class DeleteFolder(val path: String) : Dialog
    data class Trash(val books: List<LibraryBook>) : Dialog

    /** Going through books one at a time, giving each its genres. */
    data class Sort(val books: List<LibraryBook>) : Dialog

    /** Copying another library's books into a folder of this one. */
    data class CopyTo(val books: List<LibraryBook>) : Dialog

    /** Proposed genres for many books at once, to review and apply. */
    data class AutoGenre(val books: List<LibraryBook>) : Dialog

    /** Naming a new genre; [books] get it straight away. */
    data class NewGenre(val books: List<LibraryBook>) : Dialog
    data class RenameGenre(val name: String) : Dialog
    data class RemoveGenre(val name: String, val books: Int) : Dialog
}

/** Books being dragged onto a folder in the sidebar. */
@Stable
class DragState {
    var ids by mutableStateOf(emptyList<String>())
    var pointer by mutableStateOf(Offset.Zero)

    /** Drop targets by folder path ("" = top of the library), in window coordinates. */
    val targets = mutableStateMapOf<String, Rect>()

    val active: Boolean get() = ids.isNotEmpty()
    val hover: String? get() = if (active) targets.entries.firstOrNull { pointer in it.value }?.key else null
}

/**
 * Everything the organizer can do to the library, called from the grid, the sidebar,
 * menus and keys. Each step runs off the UI thread and reports back with a [Notice].
 */
class Actions(
    private val service: LibraryService,
    private val state: WindowState,
    private val scope: CoroutineScope,
) {
    val platform get() = service.platform

    private fun run(work: suspend () -> Unit) {
        scope.launch {
            try {
                work()
            } catch (e: OrganizeException) {
                state.notice = Notice(e.message ?: "That didn't work.")
            } catch (e: Exception) {
                state.notice = Notice("That didn't work: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun moveBooks(books: List<LibraryBook>, folder: String) = run {
        val from = books.associate { it.catalog.contentId to it.catalog.folder }
        val moved = service.moveBooks(from.keys, folder)
        if (moved == 0) return@run
        state.notice = Notice(
            "Moved ${count(moved)} to ${folderName(folder)}",
            undo = {
                // Back to where each one was.
                from.entries.groupBy({ it.value }, { it.key }).forEach { (original, ids) -> service.moveBooks(ids, original) }
            },
        )
    }

    fun renameFile(book: LibraryBook, name: String) = run {
        val old = book.relativePath.substringAfterLast('/')
        val new = service.renameFile(book.catalog.contentId, name)
        state.notice = Notice("Renamed to $new", undo = { service.renameFile(book.catalog.contentId, old.substringBeforeLast('.')) })
    }

    fun newFolder(parent: String, name: String) = run {
        val path = service.createFolder(parent, name)
        state.notice = Notice("Made ${folderName(path)}")
    }

    fun renameFolder(path: String, name: String) = run {
        val newPath = service.renameFolder(path, name)
        if (state.place == Place.Folder(path)) state.place = Place.Folder(newPath)
        state.notice = Notice(
            "Renamed to ${newPath.substringAfterLast('/')}",
            undo = { service.renameFolder(newPath, path.substringAfterLast('/')) },
        )
    }

    fun deleteFolder(path: String) = run {
        if (state.place is Place.Folder && (state.place as Place.Folder).path.let { it == path || it.startsWith("$path/") }) {
            state.go(Place.All)
        }
        service.deleteFolder(path)
        state.notice = Notice("Removed ${folderName(path)}; its books moved up a level")
    }

    fun trash(books: List<LibraryBook>) = run {
        val gone = service.trash(books.map { it.catalog.contentId })
        state.selection = state.selection - books.map { it.catalog.contentId }.toSet()
        state.notice = Notice(
            if (gone == books.size) "Moved ${count(gone)} to the ${platform.trashName}"
            else "Moved $gone of ${books.size} to the ${platform.trashName}; the rest couldn't be moved",
        )
    }

    fun saveEdits(book: LibraryBook, edits: com.preform.desktop.BookEdits) = run {
        val id = service.saveEdits(book, edits)
        // The id changes when the file is rewritten; keep the book selected.
        if (book.catalog.contentId in state.selection) state.selection = state.selection - book.catalog.contentId + id
        state.notice = Notice("Saved “${edits.title.trim()}”")
    }

    /** Gives [books] [genre] as well as any they have (a drop on a genre in the sidebar). */
    fun addGenre(books: List<LibraryBook>, genre: String) {
        val changes = books.filter { genre !in it.catalog.genres }.map { it to (it.catalog.genres + genre) }
        if (changes.isEmpty()) {
            state.notice = Notice(if (books.size == 1) "Already in $genre" else "All already in $genre")
            return
        }
        setGenres(changes, "Added ${count(changes.size)} to $genre")
    }

    /**
     * Auto genre's result: gives the reviewed guesses, and asks Open Library about the
     * books it had nothing for. One notice for both, so Undo stays offered.
     */
    fun autoGenre(changes: List<Pair<LibraryBook, List<String>>>, lookUp: List<LibraryBook>) {
        if (lookUp.isNotEmpty()) scope.launch { service.lookUpAgain(lookUp.map { it.catalog.contentId }) }
        val gave = if (changes.isEmpty()) null else "Gave ${count(changes.size)} a genre"
        val asked = if (lookUp.isEmpty()) null else "looking up ${count(lookUp.size)} online"
        val message = listOfNotNull(gave, asked).joinToString("; ").replaceFirstChar { it.uppercase() }
        if (changes.isEmpty()) state.notice = Notice(message) else setGenres(changes, message)
    }

    /** Sets each book's genres, with Undo putting the old ones back. */
    fun setGenres(changes: List<Pair<LibraryBook, List<String>>>, message: String? = null) = run {
        val before = HashMap<String, List<String>>()
        changes.forEach { (book, genres) ->
            val id = service.saveEdits(book, editsOf(book).copy(genres = genres.distinct()))
            before[id] = book.catalog.genres
        }
        if (message == null) return@run
        state.notice = Notice(message, undo = {
            val now = service.books.value.associateBy { it.catalog.contentId }
            before.forEach { (id, genres) -> now[id]?.let { service.saveEdits(it, editsOf(it).copy(genres = genres)) } }
        })
    }

    private fun editsOf(book: LibraryBook) = com.preform.desktop.BookEdits.of(book)

    /** Makes a genre; with [books], gives it to them too. Returns its name, or null if taken. */
    fun newGenre(name: String, books: List<LibraryBook> = emptyList()): String? {
        val made = service.addGenre(name)
        when {
            made == null -> state.notice = Notice("There's already a genre called “${name.trim()}”.")
            books.isEmpty() -> state.notice = Notice("Made the genre “$made”. Drag books onto it to add them.")
            else -> addGenre(books, made)
        }
        return made
    }

    fun renameGenre(old: String, new: String) = run {
        service.renameGenre(old, new)
        if (state.place == Place.Genre(old)) state.go(Place.Genre(new.trim()))
        state.notice = Notice("Renamed “$old” to “${new.trim()}”")
    }

    fun removeGenre(name: String) = run {
        val n = service.removeGenre(name)
        if (state.place == Place.Genre(name)) state.go(Place.All)
        state.notice = Notice(if (n == 0) "Removed “$name”" else "Removed “$name” from ${count(n)}")
    }

    fun genres() = service.genres()

    fun genreHints() = service.genreHints()

    /**
     * Copies another library's books into [folder] here: downloaded, checked against their
     * ids and filed, one by one. Nothing changes over there. Undo sends the copies to the bin.
     */
    fun copyFromPeer(books: List<LibraryBook>, folder: String) = run {
        val from = books.firstOrNull()?.peerId?.let { id -> service.peers.views.value.firstOrNull { it.peer.peerId == id }?.peer?.name } ?: "the other library"
        state.notice = Notice("Copying ${count(books.size)} from $from…")
        var added = 0
        var already = 0
        var failed = 0
        val copied = mutableListOf<String>()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            books.forEach { book ->
                runCatching { service.peers.copy(book, folder) }
                    .onSuccess { result ->
                        if (result is com.preform.desktop.CopyResult.Added) {
                            added += result.result.added
                            already += result.result.alreadyThere
                            if (result.result.added > 0) copied += book.catalog.contentId
                        }
                    }
                    .onFailure { failed++ }
            }
        }
        service.refreshLists()
        val parts = listOfNotNull(
            if (added > 0) "Copied ${count(added)} from $from into ${folderName(folder)}" else null,
            if (already > 0) "${count(already)} already here" else null,
            if (failed > 0) "${count(failed)} couldn't be copied" else null,
        )
        state.notice = Notice(
            parts.joinToString("; ").ifEmpty { "Nothing to copy" },
            undo = if (copied.isEmpty()) null else { { service.trash(copied) } },
        )
    }

    fun applyPlan(moves: List<com.preform.desktop.PlannedMove>) = run {
        val applied = service.applyPlan(moves)
        state.notice = Notice(
            if (applied.size == moves.size) "Organized ${count(applied.size)}" else "Organized ${applied.size} of ${moves.size}; some changed since",
            undo = { service.undoPlan(applied) },
        )
    }

    fun plan(books: List<LibraryBook>, scheme: com.preform.desktop.OrganizeScheme?, rename: Boolean, base: String) =
        service.plan(books, scheme, rename, base)

    suspend fun findCover(title: String, author: String?): ByteArray? = service.findCover(title, author)

    /** The extra identical copies of these books (never the books themselves) go to the bin. */
    fun trashExtraCopies(books: List<LibraryBook>) = run {
        val copies = books.flatMap { service.library.identicalCopies[it.catalog.contentId].orEmpty() }
        val gone = copies.count { service.trashCopy(it) }
        state.notice = Notice("Moved $gone extra ${if (gone == 1) "copy" else "copies"} to the ${platform.trashName}")
    }

    fun trashCopy(path: String) = run {
        if (service.trashCopy(path)) state.notice = Notice("Moved the copy to the ${platform.trashName}")
    }

    fun lookUpAgain(books: List<LibraryBook>) = run {
        service.lookUpAgain(books.map { it.catalog.contentId })
        state.notice = Notice("Looking up ${count(books.size)} again")
    }

    fun reveal(book: LibraryBook) {
        service.library.file(book)?.let(platform::reveal)
    }

    fun revealFolder(path: String) {
        runCatching { java.awt.Desktop.getDesktop().open(Path.of(service.config.libraryRoot).resolve(path).toFile()) }
    }

    /** Copies books from elsewhere into the folder being viewed (or the top of the library). */
    fun import(files: List<Path>) = run {
        val folder = (state.place as? Place.Folder)?.path ?: ""
        // A dropped folder brings every book inside it.
        val books = files.flatMap { path ->
            if (java.nio.file.Files.isDirectory(path)) {
                java.nio.file.Files.walk(path).use { s -> s.filter { java.nio.file.Files.isRegularFile(it) }.toList() }
            } else {
                listOf(path)
            }
        }
        val result = service.import(books, folder)
        state.notice = Notice(
            buildList {
                if (result.added > 0) add("Added ${count(result.added)} to ${folderName(folder)}")
                if (result.alreadyThere > 0) add("${count(result.alreadyThere)} already in the library")
                if (result.notBooks > 0) add("${result.notBooks} not EPUB or PDF")
            }.joinToString(" · ").ifEmpty { "Nothing to add" },
        )
    }

    fun chooseFilesToAdd() {
        val dialog = FileDialog(null as Frame?, "Add books", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.endsWith(".epub", true) || name.endsWith(".pdf", true) }
            if (!platform.isMac) file = "*.epub;*.pdf"
            isVisible = true
        }
        val files = dialog.files.map { it.toPath() }
        if (files.isNotEmpty()) import(files)
    }

    private fun count(n: Int) = if (n == 1) "1 book" else "$n books"

    private fun folderName(path: String) = if (path.isEmpty()) "the top of the library" else "“${path.substringAfterLast('/')}”"
}

/** The notice pill; fades after a few seconds. */
@Composable
fun NoticeBar(state: WindowState, scope: CoroutineScope, modifier: Modifier = Modifier) {
    val notice = state.notice ?: return
    LaunchedEffect(notice.id) {
        delay(if (notice.undo != null) 8_000 else 5_000)
        if (state.notice?.id == notice.id) state.notice = null
    }
    Row(
        modifier
            .background(colors.text, RoundedCornerShape(22.dp))
            .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(notice.text, color = colors.background, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        notice.undo?.let { undo ->
            Spacer(Modifier.width(8.dp))
            TextButton(colors = quietButton(), onClick = {
                state.notice = null
                scope.launch { runCatching { undo() } }
            }) { Text("Undo", color = colors.background, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(10.dp))
    }
}

/** Shows whichever dialog the window wants. */
@Composable
fun Dialogs(state: WindowState, actions: Actions, folders: List<String>, covers: CoverCache, writesIntoFiles: Boolean) {
    val dialog = state.dialog ?: return
    val close = { state.dialog = null }
    when (dialog) {
        is Dialog.Edit -> EditBookDialog(dialog.book, covers, actions, writesIntoFiles, onDismiss = close)
        Dialog.Organize -> OrganizeDialog(state, actions, onDismiss = close)
        is Dialog.Sort -> SortDialog(dialog.books, covers, actions, onDismiss = close)
        is Dialog.CopyTo -> MoveToDialog(
            folders,
            title = if (dialog.books.size == 1) "Copy “${dialog.books.single().catalog.title}” to…" else "Copy ${dialog.books.size} books to…",
            onDismiss = close,
        ) { folder ->
            close()
            actions.copyFromPeer(dialog.books, folder)
        }
        is Dialog.AutoGenre -> AutoGenreDialog(dialog.books, covers, actions, onDismiss = close)
        is Dialog.NewGenre -> NameDialog(
            title = if (dialog.books.isEmpty()) "New genre" else "New genre for ${if (dialog.books.size == 1) "“${dialog.books.single().catalog.title}”" else "${dialog.books.size} books"}",
            initial = "",
            confirm = "Make genre",
            onDismiss = close,
        ) { close(); actions.newGenre(it, dialog.books) }
        is Dialog.RenameGenre -> NameDialog(
            title = "Rename genre",
            initial = dialog.name,
            confirm = "Rename",
            onDismiss = close,
        ) { close(); actions.renameGenre(dialog.name, it) }
        is Dialog.RemoveGenre -> Confirm(
            title = "Remove “${dialog.name}”?",
            text = if (dialog.books == 0) "Nothing has this genre yet."
            else "It comes off ${if (dialog.books == 1) "the 1 book" else "the ${dialog.books} books"} that have it. The books themselves stay.",
            confirm = "Remove genre",
            onDismiss = close,
        ) { close(); actions.removeGenre(dialog.name) }
        is Dialog.MoveTo -> MoveToDialog(folders, onDismiss = close) { folder ->
            close()
            actions.moveBooks(dialog.books, folder)
        }
        is Dialog.RenameFile -> NameDialog(
            title = "Rename file",
            initial = dialog.book.relativePath.substringAfterLast('/').substringBeforeLast('.'),
            confirm = "Rename",
            onDismiss = close,
        ) { close(); actions.renameFile(dialog.book, it) }
        is Dialog.NewFolder -> NameDialog(
            title = if (dialog.parent.isEmpty()) "New folder" else "New folder in ${dialog.parent.substringAfterLast('/')}",
            initial = "",
            confirm = "Make folder",
            onDismiss = close,
        ) { close(); actions.newFolder(dialog.parent, it) }
        is Dialog.RenameFolder -> NameDialog(
            title = "Rename folder",
            initial = dialog.path.substringAfterLast('/'),
            confirm = "Rename",
            onDismiss = close,
        ) { close(); actions.renameFolder(dialog.path, it) }
        is Dialog.DeleteFolder -> Confirm(
            title = "Remove “${dialog.path.substringAfterLast('/')}”?",
            text = "Its books move up to ${dialog.path.substringBeforeLast('/', "").ifEmpty { "the top of the library" }}. " +
                "The folder is only removed if nothing else is left in it.",
            confirm = "Remove folder",
            onDismiss = close,
        ) { close(); actions.deleteFolder(dialog.path) }
        is Dialog.Trash -> Confirm(
            title = if (dialog.books.size == 1) "Move “${dialog.books.single().catalog.title}” to the ${actions.platform.trashName}?"
            else "Move ${dialog.books.size} books to the ${actions.platform.trashName}?",
            text = "They leave the library and your phones. You can still restore them from the ${actions.platform.trashName}.",
            confirm = "Move to ${actions.platform.trashName}",
            danger = true,
            onDismiss = close,
        ) { close(); actions.trash(dialog.books) }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(title, color = colors.text) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(colors = quietButton(), onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Confirm(title: String, text: String, confirm: String, onDismiss: () -> Unit, danger: Boolean = false, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(title, color = colors.text) },
        text = { Text(text, color = colors.muted) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (danger) ButtonDefaults.buttonColors(containerColor = colors.danger) else ButtonDefaults.buttonColors(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(colors = quietButton(), onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveToDialog(folders: List<String>, title: String = "Move to…", onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val options = remember(folders, filter) { listOf("") + folders.filter { filter.isBlank() || it.contains(filter, true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(title, color = colors.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    placeholder = { Text("Find a folder") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(options) { path ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(path) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Glyphs.Folder, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (path.isEmpty()) "Top of the library" else shelfPath(path), color = colors.text, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(colors = quietButton(), onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Fiction/Fantasy" → "Fiction › Fantasy". */
fun shelfPath(path: String) = path.split('/').joinToString(" › ")

/** The ghost that follows the pointer while books are dragged. */
@Composable
fun DragGhost(drag: DragState, books: List<LibraryBook>, covers: CoverCache) {
    if (!drag.active) return
    val dragged = books.filter { it.catalog.contentId in drag.ids }
    val first = dragged.firstOrNull() ?: return
    Box(Modifier.offset { IntOffset(drag.pointer.x.roundToInt() + 14, drag.pointer.y.roundToInt() + 14) }) {
        BookCover(first, covers, Modifier.width(64.dp))
        if (dragged.size > 1) {
            Text(
                "${dragged.size}",
                color = colors.onAccent,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopEnd).background(colors.accent, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}
