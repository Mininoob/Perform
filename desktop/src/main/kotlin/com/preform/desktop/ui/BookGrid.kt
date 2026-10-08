package com.preform.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.preform.desktop.LibraryBook
import com.preform.sync.Genres

/** The toolbar and the cover grid for the current place. */
@Composable
fun BookGrid(
    state: WindowState,
    books: List<LibraryBook>,
    covers: CoverCache,
    libraryEmpty: Boolean,
    libraryFound: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Toolbar(state, books.size, onRescan)
        when {
            !libraryFound -> Empty("Your library folder can't be found.", "Choose it again in Settings.") { state.go(Place.Settings) }
            libraryEmpty -> Empty("No books yet.", "Add EPUB or PDF files to your library folder, or drop them here.", null)
            books.isEmpty() && state.query.isNotBlank() -> Empty("Nothing matches “${state.query}”.", null, null)
            books.isEmpty() -> Empty("Nothing here yet.", null, null)
            else -> Grid(state, books, covers)
        }
    }
}

@Composable
private fun Toolbar(state: WindowState, count: Int, onRescan: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(placeTitle(state.place), fontFamily = FontFamily.Serif, fontSize = 26.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 book" else "$count books", color = colors.muted, fontSize = 13.sp)
        }
        SearchField(state.query, onChange = { state.query = it })
        Spacer(Modifier.width(8.dp))
        SortMenu(state)
        IconButton(onClick = onRescan) { Icon(Glyphs.Refresh, "Check the folder for changes", tint = colors.muted) }
    }
}

fun placeTitle(place: Place): String = when (place) {
    Place.All -> "All books"
    Place.Recent -> "Recently added"
    is Place.Folder -> place.path.substringAfterLast('/')
    is Place.Genre -> place.name
    Place.Phones -> "Phones"
    Place.Settings -> "Settings"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .width(240.dp)
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
        TextButton(onClick = { open = true }) {
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
private fun Grid(state: WindowState, books: List<LibraryBook>, covers: CoverCache) {
    val grid = rememberLazyGridState()
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
            BookCard(
                book = book,
                covers = covers,
                selected = id in state.selection,
                modifier = Modifier
                    // Plain click selects one; Ctrl/Cmd adds or removes; Shift selects a range.
                    .onClick { state.selection = setOf(id); state.anchor = id }
                    .onClick(keyboardModifiers = { isCtrlPressed || isMetaPressed }) {
                        state.selection = if (id in state.selection) state.selection - id else state.selection + id
                        state.anchor = id
                    }
                    .onClick(keyboardModifiers = { isShiftPressed }) {
                        val from = books.indexOfFirst { it.catalog.contentId == state.anchor }.takeIf { it >= 0 } ?: 0
                        val to = books.indexOf(book)
                        state.selection = books.subList(minOf(from, to), maxOf(from, to) + 1).mapTo(HashSet()) { it.catalog.contentId }
                    },
            )
        }
    }
}

@Composable
private fun BookCard(book: LibraryBook, covers: CoverCache, selected: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .background(if (selected) colors.selected else Color.Transparent, shape)
            .border(1.5.dp, if (selected) colors.accent.copy(alpha = 0.7f) else Color.Transparent, shape)
            .padding(10.dp),
    ) {
        BookCover(book, covers, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            book.catalog.title,
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.catalog.author ?: "Unknown author",
            fontSize = 12.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Empty(title: String, detail: String?, action: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Glyphs.Books, null, tint = colors.muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontFamily = FontFamily.Serif, fontSize = 20.sp, color = colors.text)
        detail?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = colors.muted, fontSize = 13.sp)
        }
        action?.let {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = it) { Text("Open Settings") }
        }
    }
}

/** "Fantasy · Children's", or nothing. */
fun genreLine(book: LibraryBook): String? = book.catalog.genres.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    ?.takeIf { it != Genres.UNCATEGORIZED }
