package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.preform.desktop.LibraryBook
import com.preform.desktop.LibraryService
import com.preform.sync.Genres

/** Where the window is: a slice of the library, or the Phones or Settings page. */
sealed interface Place {
    data object All : Place
    data object Recent : Place
    data class Folder(val path: String) : Place
    data class Genre(val name: String) : Place
    data object Phones : Place
    data object Settings : Place
}

enum class SortOrder(val label: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    ADDED("Recently added"),
    YEAR("Year"),
}

/** What the window remembers while it's open: where you are, what you've searched and selected. */
@Stable
class WindowState {
    var place by mutableStateOf<Place>(Place.All)
    var query by mutableStateOf("")
    var sort by mutableStateOf(SortOrder.TITLE)

    /** Selected books by content id. */
    var selection by mutableStateOf(emptySet<String>())

    /** Where a shift-click range starts. */
    var anchor by mutableStateOf<String?>(null)

    /** The pairing panel is showing (also opened from the tray's "Pair a phone"). */
    var pairing by mutableStateOf(false)

    var notice by mutableStateOf<Notice?>(null)
    var dialog by mutableStateOf<Dialog?>(null)
    val drag = DragState()

    /** The books on screen right now, for Select all and shift-click ranges. */
    var visible by mutableStateOf(emptyList<LibraryBook>())

    /** Set by the window, for keyboard shortcuts handled at the window level. */
    var actions: Actions? = null

    /** Window-level keys: select all, clear, rename, move to the bin. True if handled. */
    fun onKey(event: KeyEvent, mac: Boolean): Boolean =
        event.type == KeyEventType.KeyDown && onKey(event.key, if (mac) event.isMetaPressed else event.isCtrlPressed, mac)

    /** [command] is Ctrl on Windows, Cmd on the Mac. */
    fun onKey(key: Key, command: Boolean, mac: Boolean): Boolean {
        if (dialog != null) return false
        val selected = visible.filter { it.catalog.contentId in selection }
        return when {
            command && key == Key.A -> { selection = visible.mapTo(HashSet()) { it.catalog.contentId }; true }
            key == Key.Escape && selection.isNotEmpty() -> { selection = emptySet(); true }
            key == Key.F2 && selected.size == 1 -> { dialog = Dialog.RenameFile(selected.single()); true }
            (key == Key.Delete || (mac && command && key == Key.Backspace)) && selected.isNotEmpty() -> {
                dialog = Dialog.Trash(selected)
                true
            }
            else -> false
        }
    }

    fun go(to: Place) {
        place = to
        selection = emptySet()
        anchor = null
    }
}

/** The books [place] shows, searched and sorted. */
fun visibleBooks(books: List<LibraryBook>, place: Place, query: String, sort: SortOrder): List<LibraryBook> {
    val inPlace = when (place) {
        Place.All -> books
        Place.Recent -> books.sortedByDescending { it.catalog.addedAt }.take(RECENT_COUNT)
        is Place.Folder -> books.filter { it.catalog.folder == place.path || it.catalog.folder.startsWith(place.path + "/") }
        is Place.Genre -> books.filter { book ->
            if (place.name == Genres.UNCATEGORIZED) book.catalog.genres.isEmpty() else place.name in book.catalog.genres
        }
        Place.Phones, Place.Settings -> emptyList()
    }
    val searched = if (query.isBlank()) inPlace else {
        val q = query.trim()
        inPlace.filter { b ->
            b.catalog.title.contains(q, true) || b.catalog.author?.contains(q, true) == true ||
                b.relativePath.contains(q, true)
        }
    }
    if (place == Place.Recent && query.isBlank()) return searched
    return when (sort) {
        SortOrder.TITLE -> searched.sortedBy { sortableTitle(it.catalog.title) }
        SortOrder.AUTHOR -> searched.sortedWith(compareBy<LibraryBook> { it.catalog.author?.let(::surnameFirst) ?: "￿" }.thenBy { sortableTitle(it.catalog.title) })
        SortOrder.ADDED -> searched.sortedByDescending { it.catalog.addedAt }
        SortOrder.YEAR -> searched.sortedWith(compareBy<LibraryBook> { it.catalog.year ?: Int.MAX_VALUE }.thenBy { sortableTitle(it.catalog.title) })
    }
}

private const val RECENT_COUNT = 60

/** "The Hobbit" files under H. */
fun sortableTitle(title: String) = title.lowercase().removePrefix("the ").removePrefix("a ").removePrefix("an ")

/** "Frank Herbert" sorts as "herbert frank". */
private fun surnameFirst(author: String): String {
    val first = author.split(',', '&', ';').first().trim()
    return if (',' in author) first.lowercase() else first.split(' ').let { (it.takeLast(1) + it.dropLast(1)).joinToString(" ") }.lowercase()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryWindow(service: LibraryService, covers: CoverCache, state: WindowState) {
    val books by service.books.collectAsState()
    val folders by service.folders.collectAsState()
    val status by service.status.collectAsState()
    val activity by service.activity.collectAsState()
    val config by service.configFlow.collectAsState()

    val scope = rememberCoroutineScope()
    val actions = remember(service, state) { Actions(service, state, scope) }
    state.actions = actions
    val fileDrop = remember(actions) { FileDropTarget(actions) }

    Box(
        Modifier
            .fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = FileDropTarget::accepts, target = fileDrop),
    ) {
    Row(Modifier.fillMaxSize().background(colors.background)) {
        Sidebar(
            state = state,
            actions = actions,
            books = books,
            folders = folders,
            status = status,
            activity = activity,
            modifier = Modifier.width(248.dp).fillMaxHeight(),
        )
        VerticalDivider(color = colors.line)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val place = state.place) {
                Place.Phones -> PhonesScreen(service, state)
                Place.Settings -> SettingsScreen(service, config, status)
                else -> {
                    val shown = remember(books, place, state.query, state.sort) { visibleBooks(books, place, state.query, state.sort) }
                    state.visible = shown
                    Row(Modifier.fillMaxSize()) {
                        BookGrid(
                            state = state,
                            books = shown,
                            covers = covers,
                            libraryEmpty = books.isEmpty(),
                            libraryFound = status.libraryFound,
                            actions = actions,
                            onRescan = service::rescanSoon,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        VerticalDivider(color = colors.line)
                        BookDetails(
                            service = service,
                            covers = covers,
                            selected = books.filter { it.catalog.contentId in state.selection },
                            shown = shown,
                            place = place,
                            modifier = Modifier.width(320.dp).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
        DragGhost(state.drag, books, covers)
        NoticeBar(state, scope, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
        Dialogs(state, actions, folders)
    }
}

/** Books (or folders of books) dropped onto the window from Explorer or Finder are added. */
@OptIn(ExperimentalComposeUiApi::class)
private class FileDropTarget(private val actions: Actions) : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean {
        val files = files(event) ?: return false
        actions.import(files)
        return true
    }

    companion object {
        fun accepts(event: DragAndDropEvent): Boolean = files(event) != null

        private fun files(event: DragAndDropEvent): List<Path>? = runCatching {
            val t = event.awtTransferable
            if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null
            (t.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>().map { it.toPath() }
        }.getOrNull()
    }
}
