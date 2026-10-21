package com.perform.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perform.desktop.CheckKind
import com.perform.desktop.LibraryBook
import com.perform.sync.Genres

/** The toolbar and the cover grid for the current place. */
@Composable
fun BookGrid(
    state: WindowState,
    books: List<LibraryBook>,
    covers: CoverCache,
    libraryEmpty: Boolean,
    libraryFound: Boolean,
    actions: Actions,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Toolbar(state, books.size, actions, onRescan)
        (state.place as? Place.Check)?.let { CheckBanner(it.kind, books, state, actions) }
        if ((state.place as? Place.Genre)?.name == Genres.UNCATEGORIZED && books.isNotEmpty()) UncategorizedBanner(books, state, actions)
        (state.place as? Place.Compare)?.let { CompareBanner(it, books, state) }
        when {
            !libraryFound -> Empty("Your library folder can't be found.", "Choose it again in Settings.") { state.go(Place.Settings) }
            libraryEmpty -> Empty("No books yet.", "Add EPUB or PDF files to your library folder, or drop them here.", null)
            books.isEmpty() && state.query.isNotBlank() -> Empty("Nothing matches “${state.query}”.", null, null)
            books.isEmpty() -> Empty("Nothing here yet.", null, null)
            else -> Grid(state, books, covers, actions)
        }
    }
}

@Composable
private fun Toolbar(state: WindowState, count: Int, actions: Actions, onRescan: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                placeTitle(state.place, state.peerNames),
                fontFamily = LiterataDisplay,
                fontSize = 26.sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Text(if (count == 1) "1 book" else "$count books", color = colors.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                SearchField(state.query, onChange = { state.query = it }, modifier = Modifier.weight(1f).widthIn(max = 340.dp))
                Spacer(Modifier.width(4.dp))
                SortMenu(state)
            }
            if (!state.browsingTheirs) {
                TextButton(colors = quietButton(), onClick = { state.dialog = Dialog.Organize }) { Text("Organize…", color = colors.text, fontSize = 13.sp) }
                TextButton(colors = quietButton(), onClick = actions::chooseFilesToAdd) { Text("Add books", color = colors.accent, fontSize = 13.sp) }
            }
            IconButton(onClick = onRescan) { Icon(Glyphs.Refresh, "Check the folder for changes", tint = colors.muted, modifier = Modifier.size(18.dp)) }
        }
    }
}

fun placeTitle(place: Place, peerNames: Map<String, String> = emptyMap()): String = when (place) {
    is Place.Check -> place.kind.label
    Place.All -> "All books"
    Place.Recent -> "Recently added"
    is Place.Folder -> place.path.substringAfterLast('/')
    is Place.Genre -> place.name
    Place.Devices -> "Devices"
    is Place.Peer -> place.folder.substringAfterLast('/').ifEmpty { peerNames[place.peerId] ?: "Their library" }
    is Place.Compare -> if (place.theirs) "Not in yours" else "Only here"
    Place.Settings -> "Settings"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(colors.raised, RoundedCornerShape(20.dp))
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.Search, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search title, author, file", color = colors.muted, fontSize = 13.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 13.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Glyphs.Close,
                "Clear search",
                tint = colors.muted,
                modifier = Modifier.size(16.dp).onClick { onChange("") }.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}

@Composable
private fun SortMenu(state: WindowState) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(colors = quietButton(), onClick = { open = true }) {
            Text(state.sort.label, color = colors.text, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Glyphs.ChevronDown, "Sort by", tint = colors.muted, modifier = Modifier.size(14.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { state.sort = option; open = false })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Grid(state: WindowState, books: List<LibraryBook>, covers: CoverCache, actions: Actions) {
    val grid = rememberLazyGridState()
    val current by rememberUpdatedState(books)
    // Their books are marked against ours.
    val ours = remember(state.allBooks) { com.perform.desktop.LibraryIndex(state.allBooks) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = grid,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books, key = { it.catalog.contentId }) { book ->
            val id = book.catalog.contentId
            var place by remember { mutableStateOf<LayoutCoordinates?>(null) }
            ContextMenuArea(items = { bookMenu(book, state, actions) }) {
            BookCard(
                book = book,
                covers = covers,
                selected = id in state.selection,
                progress = state.progress[id]?.progression,
                mark = if (book.peerId != null) inYours(ours.match(book)) else null,
                modifier = Modifier
                    .onGloballyPositioned { place = it }
                    // Drag onto a folder in the sidebar to move; drags the whole selection.
                    .pointerInput(id) {
                        detectDragGestures(
                            onDragStart = { at ->
                                if (id !in state.selection) {
                                    state.selection = setOf(id)
                                    state.anchor = id
                                }
                                state.drag.ids = state.selection.toList()
                                place?.let { state.drag.pointer = it.localToWindow(at) }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                place?.let { state.drag.pointer = it.localToWindow(change.position) }
                            },
                            onDragEnd = {
                                val target = state.drag.hover
                                val ids = state.drag.ids.toSet()
                                state.drag.ids = emptyList()
                                val dropped = current.filter { it.catalog.contentId in ids }
                                when {
                                    target == null -> Unit
                                    // Another library's books are copied in, never moved.
                                    dropped.firstOrNull()?.peerId != null -> if (!target.startsWith(GENRE_DROP)) actions.copyFromPeer(dropped, target)
                                    target.startsWith(GENRE_DROP) -> actions.addGenre(dropped, target.removePrefix(GENRE_DROP))
                                    else -> actions.moveBooks(dropped, target)
                                }
                            },
                            onDragCancel = { state.drag.ids = emptyList() },
                        )
                    }
                    // Selection happens on mouse-down, so it feels instant (a click handler
                    // would wait to rule out a double-click first).
                    .pointerInput(id) {
                        var lastPress = 0L
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val event = currentEvent
                            if (!event.buttons.isPrimaryPressed) return@awaitEachGesture
                            val mods = event.keyboardModifiers
                            val now = System.currentTimeMillis()
                            val double = now - lastPress < viewConfiguration.doubleTapTimeoutMillis
                            lastPress = now
                            val plain = !mods.isShiftPressed && !mods.isCtrlPressed && !mods.isMetaPressed
                            val partOfMany = id in state.selection && state.selection.size > 1
                            when {
                                mods.isShiftPressed -> {
                                    val list = current
                                    val from = list.indexOfFirst { it.catalog.contentId == state.anchor }.takeIf { it >= 0 } ?: 0
                                    val to = list.indexOfFirst { it.catalog.contentId == id }
                                    state.selection = list.subList(minOf(from, to), maxOf(from, to) + 1).mapTo(HashSet()) { it.catalog.contentId }
                                }
                                mods.isCtrlPressed || mods.isMetaPressed -> {
                                    state.selection = if (id in state.selection) state.selection - id else state.selection + id
                                    state.anchor = id
                                }
                                // Pressing an already-selected book keeps the selection, so it can be dragged.
                                id !in state.selection -> {
                                    state.selection = setOf(id)
                                    state.anchor = id
                                }
                            }
                            if (double) state.dialog = Dialog.Edit(book)
                            // Released without dragging: just this book, as in Explorer and Finder.
                            if (plain && partOfMany && waitForUpOrCancellation() != null) {
                                state.selection = setOf(id)
                                state.anchor = id
                            }
                        }
                    },
            )
            }
        }
    }
}

/** Right-click on a book: acts on the selection, or on just this book if it isn't selected. */
private fun bookMenu(book: LibraryBook, state: WindowState, actions: Actions): List<ContextMenuItem> {
    val id = book.catalog.contentId
    if (id !in state.selection) {
        state.selection = setOf(id)
        state.anchor = id
    }
    val targets = state.visible.filter { it.catalog.contentId in state.selection }.ifEmpty { listOf(book) }
    val one = targets.singleOrNull()
    if (book.peerId != null) return listOf(ContextMenuItem("Copy to my library…") { state.dialog = Dialog.CopyTo(targets) })
    return buildList {
        add(ContextMenuItem("Move to…") { state.dialog = Dialog.MoveTo(targets) })
        if (one != null) {
            add(ContextMenuItem("Edit details…") { state.dialog = Dialog.Edit(one) })
            add(ContextMenuItem("Rename file…") { state.dialog = Dialog.RenameFile(one) })
            add(ContextMenuItem("Show in ${actions.platform.fileManagerName}") { actions.reveal(one) })
        }
        add(ContextMenuItem("Sort into genres…") { state.dialog = Dialog.Sort(targets) })
        add(ContextMenuItem("Add to a new genre…") { state.dialog = Dialog.NewGenre(targets) })
        add(ContextMenuItem("Look up again") { actions.lookUpAgain(targets) })
        add(ContextMenuItem("Move to ${actions.platform.trashName}…") { state.dialog = Dialog.Trash(targets) })
    }
}

@Composable
private fun BookCard(book: LibraryBook, covers: CoverCache, selected: Boolean, progress: Double?, modifier: Modifier, mark: String? = null) {
    Column(modifier.padding(10.dp)) {
        // Selection outlines the cover itself; no tile, no card.
        Box {
            BookCover(book, covers, Modifier.fillMaxWidth())
            if (selected) {
                Box(Modifier.matchParentSize().border(2.dp, colors.accent, RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            book.catalog.title,
            fontFamily = Literata,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The same furniture under every cover: author and year, then the reading rule.
        Text(
            listOfNotNull(book.catalog.author ?: "Unknown author", book.catalog.year?.toString()).joinToString(" · "),
            fontSize = 12.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (progress != null && progress > 0.005) {
            Spacer(Modifier.height(6.dp))
            ProgressRule(progress, Modifier.fillMaxWidth())
        }
        // Another library's book: whether yours has it, as a muted word, never a badge.
        mark?.let {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Tick, null, tint = colors.muted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(it, fontSize = 11.sp, color = colors.muted, maxLines = 1)
            }
        }
    }
}

/** For another library's book: "In yours", "Another edition in yours", or nothing. */
fun inYours(match: com.perform.sync.BookMatch): String? = when (match) {
    com.perform.sync.BookMatch.SAME -> "In yours"
    com.perform.sync.BookMatch.EDITION -> "Another edition in yours"
    com.perform.sync.BookMatch.NONE -> null
}

/** Reading progress as one 2dp rule in the accent: the line is the state. */
@Composable
fun ProgressRule(progress: Double, modifier: Modifier = Modifier) {
    Box(modifier.height(2.dp).background(colors.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).background(colors.accent))
    }
}

/** What a check means, and the fix that applies to all of its books at once. */
@Composable
private fun CheckBanner(kind: CheckKind, books: List<LibraryBook>, state: WindowState, actions: Actions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kind.hint, color = colors.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (books.isEmpty()) return@Row
        when (kind) {
            CheckKind.IDENTICAL -> TextButton(colors = quietButton(), onClick = { actions.trashExtraCopies(books) }) {
                Text("Move extra copies to the ${actions.platform.trashName}", color = colors.accent, fontSize = 13.sp)
            }
            CheckKind.NO_GENRE -> SortButtons(books, state, actions)
            CheckKind.TITLE_FROM_FILE, CheckKind.NO_AUTHOR, CheckKind.NO_COVER ->
                TextButton(colors = quietButton(), onClick = { actions.lookUpAgain(books) }) { Text("Look them all up again", color = colors.accent, fontSize = 13.sp) }
            else -> Unit
        }
    }
}

/** Above the Uncategorized shelf: the way to give these books a home. */
@Composable
private fun UncategorizedBanner(books: List<LibraryBook>, state: WindowState, actions: Actions) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Books without a genre. Let Perform suggest genres, sort them one by one, or drag them onto a genre.",
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        SortButtons(books, state, actions)
    }
}

/** What Compare shows, and for their books the way to bring them all over. */
@Composable
private fun CompareBanner(place: Place.Compare, books: List<LibraryBook>, state: WindowState) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (place.theirs) "Books there that aren't in your library in any edition. Nothing is copied until you ask."
            else "Books in your library that the other one doesn't have.",
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (place.theirs && books.isNotEmpty()) {
            TextButton(colors = quietButton(), onClick = { state.dialog = Dialog.CopyTo(books) }) {
                Text("Copy all to my library…", color = colors.accent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SortButtons(books: List<LibraryBook>, state: WindowState, actions: Actions) {
    TextButton(colors = quietButton(), onClick = { state.dialog = Dialog.Sort(books) }) { Text("Sort them…", color = colors.text, fontSize = 13.sp) }
    TextButton(colors = quietButton(), onClick = { state.dialog = Dialog.AutoGenre(books) }) { Text("Auto genre…", color = colors.accent, fontSize = 13.sp) }
}

@Composable
private fun Empty(title: String, detail: String?, action: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Glyphs.Books, null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontFamily = LiterataDisplay, fontSize = 20.sp, color = colors.text)
        detail?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = colors.muted, fontSize = 13.sp)
        }
        action?.let {
            Spacer(Modifier.height(12.dp))
            TextButton(colors = quietButton(), onClick = it) { Text("Open Settings") }
        }
    }
}

/** "Fantasy · Children's", or nothing. */
fun genreLine(book: LibraryBook): String? = book.catalog.genres.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    ?.takeIf { it != Genres.UNCATEGORIZED }
