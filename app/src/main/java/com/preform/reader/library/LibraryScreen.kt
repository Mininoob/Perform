package com.preform.reader.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.preform.reader.ui.CloudIcon
import com.preform.reader.ui.Literata
import com.preform.reader.ui.LiterataDisplay
import com.preform.reader.ui.TypeCover
import com.preform.reader.ui.rememberCover
import org.json.JSONObject
import kotlin.math.roundToInt

/** The page margin every row, title and grid aligns to. */
private val Margin = 20.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onOpen: (BookEntity) -> Unit,
    onRemove: (BookEntity) -> Unit,
    onDownloadAction: (BookEntity, DownloadAction) -> Unit,
    shelfActions: ShelfActions,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onAddBook: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<BookEntity?>(null) }
    // Back climbs out of a shelf before it leaves the library.
    BackHandler(enabled = state.location != null && state.query.isBlank(), onBack = shelfActions.onUp)
    BackHandler(enabled = searching) { searching = false; onQueryChange("") }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        scrolledContainerColor = MaterialTheme.colorScheme.background,
    )
    val actions: @Composable () -> Unit = {
        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, contentDescription = "Search") }
        IconButton(onClick = onAddBook) { Icon(Icons.Default.Add, contentDescription = "Add book") }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (searching) {
                TopAppBar(
                    colors = barColors,
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Title or author") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        IconButton(onClick = { searching = false; onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                )
            } else {
                LargeTopAppBar(
                    colors = barColors,
                    scrollBehavior = scroll,
                    title = { Text(pageTitle(state)) },
                    navigationIcon = {
                        if (state.location != null) {
                            IconButton(onClick = shelfActions.onUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = { actions() },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                !state.loaded -> Unit
                state.books.isEmpty() && state.query.isBlank() && state.scan == ScanState.Idle ->
                    EmptyLibrary(state.folderLabel, onChooseFolder, onAddBook)
                else -> BookGrid(state, onOpen, onRemove, onDownloadAction, shelfActions, onSortChange, onDetails = { details = it })
            }
        }
    }

    details?.let { book -> BookSheet(book, onDismiss = { details = null }, onOpen = { details = null; onOpen(book) }) }
}

/** "Library" at the top; inside a shelf or genre, its name. */
private fun pageTitle(state: LibraryUiState): String {
    val location = state.location ?: return "Library"
    return if (state.view == LibraryView.SHELVES) location.substringAfterLast('/') else location
}

@Composable
private fun BookGrid(
    state: LibraryUiState,
    onOpen: (BookEntity) -> Unit,
    onRemove: (BookEntity) -> Unit,
    onDownloadAction: (BookEntity, DownloadAction) -> Unit,
    shelfActions: ShelfActions,
    onSortChange: (LibrarySort) -> Unit,
    onDetails: (BookEntity) -> Unit,
) {
    val page = state.page
    val atTop = state.location == null
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Margin, end = Margin, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        (state.scan as? ScanState.Scanning)?.let { scan ->
            item(span = { GridItemSpan(maxLineSpan) }) { ScanProgress(scan) }
        }

        val hero = state.continueReading
        if (hero != null && state.query.isBlank() && atTop) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "in-hand") {
                InHand(hero, opening = state.opening == hero.id, onOpen = { onOpen(hero) }, onDetails = { onDetails(hero) })
            }
        }

        if (state.query.isBlank() && atTop) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "views") {
                ViewTabs(state.view, shelfActions.onViewChange, Modifier.bleed(Margin))
            }
        }

        val tiles = page?.loose ?: state.books
        page?.shelves?.forEach { shelf ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "shelf:${shelf.key}") {
                ShelfRow(shelf, state, onOpen, onDetails, onEnter = { shelfActions.onEnter(shelf.key) })
            }
        }

        if (tiles.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "grid-head") {
                GridHead(
                    title = when {
                        page == null -> null
                        page.shelves.isEmpty() -> null
                        atTop -> "Not on a shelf"
                        else -> "In this folder"
                    },
                    count = tiles.size,
                    sort = state.sort,
                    onSortChange = onSortChange,
                )
            }
        }

        items(tiles, key = { it.id }) { book ->
            BookTile(
                book = book,
                opening = state.opening == book.id,
                download = state.downloads[book.id],
                onOpen = { onOpen(book) },
                onRemove = { onRemove(book) },
                onDetails = { onDetails(book) },
                onDownloadAction = { onDownloadAction(book, it) },
            )
        }

        if (state.books.isEmpty() && state.query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nothing matches “${state.query}”.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

/** Lets a full-width row run past the page margin to the screen edges, as a scrolling shelf should. */
private fun Modifier.bleed(margin: Dp) = layout { measurable, constraints ->
    val extra = (margin * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-margin.roundToPx(), 0) }
}

@Composable
private fun ScanProgress(scan: ScanState.Scanning) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = if (scan.total == 0) "Scanning library folder…" else "Adding books… ${scan.done} of ${scan.total}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (scan.total == 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { scan.done / scan.total.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The book being read, at the top of the size ramp: tap Continue, or the cover for details. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InHand(book: BookEntity, opening: Boolean, onOpen: () -> Unit, onDetails: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        Cover(
            book,
            opening,
            Modifier.width(132.dp).combinedClickable(onClick = onOpen, onLongClick = onDetails),
            large = true,
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = Literata,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            ProgressRule(book.progression, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull("${(book.progression * 100).roundToInt()}%", chapterOf(book)).joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onOpen) { Text("Continue") }
        }
    }
}

/** The chapter the reader left off in, from the saved position. */
private fun chapterOf(book: BookEntity): String? =
    book.locatorJson?.let { runCatching { JSONObject(it).optString("title").ifBlank { null } }.getOrNull() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewTabs(view: LibraryView, onChange: (LibraryView) -> Unit, modifier: Modifier = Modifier) {
    val selected = LibraryView.entries.indexOf(view)
    SecondaryScrollableTabRow(
        selectedTabIndex = selected,
        modifier = modifier,
        edgePadding = Margin - 16.dp,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        indicator = {
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(selected, matchContentSize = true),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
    ) {
        LibraryView.entries.forEachIndexed { i, option ->
            Tab(
                selected = i == selected,
                onClick = { onChange(option) },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(option.label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Composable
private fun GridHead(title: String?, count: Int, sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (count == 1) "1 book" else "$count books",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { open = true }) { Text(sort.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { onSortChange(option); open = false })
                }
            }
        }
    }
}

/** Browsing callbacks for shelves and genres. */
class ShelfActions(
    val onViewChange: (LibraryView) -> Unit,
    /** Opens a shelf by its folder path or genre. */
    val onEnter: (String) -> Unit,
    val onUp: () -> Unit,
)

/** A shelf: its name and count over a row of covers that runs to the screen's edges. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    shelf: Shelf,
    state: LibraryUiState,
    onOpen: (BookEntity) -> Unit,
    onDetails: (BookEntity) -> Unit,
    onEnter: () -> Unit,
) {
    Column(Modifier.bleed(Margin)) {
        Row(Modifier.fillMaxWidth().padding(start = Margin, end = Margin - 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shelf.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${shelf.books.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnter) { Text("See all") }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = Margin),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.books.take(SHELF_PREVIEW), key = { it.id }) { book ->
                Column(Modifier.width(96.dp).combinedClickable(onClick = { onOpen(book) }, onLongClick = { onDetails(book) })) {
                    Cover(book, state.opening == book.id, Modifier.fillMaxWidth(), state.downloads[book.id])
                    Caption(book)
                }
            }
        }
    }
}

private const val SHELF_PREVIEW = 20

/** Under every cover, the same furniture in the same place: title, then author and year, then progress. */
@Composable
private fun Caption(book: BookEntity) {
    Spacer(Modifier.height(10.dp))
    Text(
        book.title,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = Literata,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    furniture(book)?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (book.progression > 0) {
        Spacer(Modifier.height(6.dp))
        ProgressRule(book.progression, Modifier.fillMaxWidth())
    }
}

/** "Austen · 1815": the author's surname and the year. */
private fun furniture(book: BookEntity): String? =
    listOfNotNull(
        book.author?.split(',', '&', ';')?.first()?.trim()?.substringAfterLast(' ')?.ifBlank { null },
        book.year?.toString(),
    ).joinToString(" · ").ifBlank { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(
    book: BookEntity,
    opening: Boolean,
    download: Float?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
    onDownloadAction: (DownloadAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Cover(book, opening, Modifier.fillMaxWidth(), download)
            Caption(book)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Book info") }, onClick = { menu = false; onDetails() })
            if (book.source == BookSource.SERVER) {
                if (book.pinned) {
                    DropdownMenuItem(text = { Text("Don't keep downloaded") }, onClick = { menu = false; onDownloadAction(DownloadAction.UNPIN) })
                } else {
                    DropdownMenuItem(text = { Text("Keep downloaded") }, onClick = { menu = false; onDownloadAction(DownloadAction.PIN) })
                }
                if (book.localPath != null) {
                    DropdownMenuItem(text = { Text("Remove download") }, onClick = { menu = false; onDownloadAction(DownloadAction.REMOVE_DOWNLOAD) })
                }
            }
            DropdownMenuItem(text = { Text("Remove from library") }, onClick = { menu = false; onRemove() })
        }
    }
}

/** A 2:3 cover; a book without one gets the series' typeset cover in its genre's cloth. */
@Composable
private fun Cover(book: BookEntity, opening: Boolean, modifier: Modifier, download: Float? = null, large: Boolean = false) {
    val image = rememberCover(if (large) book.coverFullPath ?: book.coverThumbPath else book.coverThumbPath)
    val shape = RoundedCornerShape(if (large) 4.dp else 3.dp)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .shadow(if (large) 12.dp else 3.dp, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (book.coverThumbPath == null) {
            TypeCover(book.title, book.author, book.genreList)
        }
        if (!book.downloaded && download == null) {
            // On the PC, not on the phone yet: downloads when opened.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color(0x99000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CloudIcon.Outline, contentDescription = "On your PC", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (download != null) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                if (download > 0f) {
                    CircularProgressIndicator(
                        progress = { download },
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        trackColor = Color(0x44FFFFFF),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                }
            }
        } else if (opening) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
            }
        }
    }
}

enum class DownloadAction { PIN, UNPIN, REMOVE_DOWNLOAD }

/** Reading progress as one rule: the line is the state, no badge or percentage pill. */
@Composable
private fun ProgressRule(progress: Double, modifier: Modifier) {
    Box(modifier.height(2.dp).background(MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary))
    }
}

/** The book's full card: the third fold of the same object, after spine-row and cover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSheet(book: BookEntity, onDismiss: () -> Unit, onOpen: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row {
                Cover(book, opening = false, modifier = Modifier.width(112.dp), large = true)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.headlineSmall)
                    book.author?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = Literata,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    book.series?.let { series ->
                        val number = book.seriesIndex?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
                        Spacer(Modifier.height(6.dp))
                        Text(series + (number?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (book.progression > 0) {
                        ProgressRule(book.progression, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(book.progression * 100).roundToInt()}% read",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(onClick = onOpen) { Text(if (book.progression > 0) "Continue" else "Read") }
                }
            }
            book.description?.let {
                Spacer(Modifier.height(22.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, fontFamily = Literata, maxLines = 12, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Fact("Genre", book.genreList.joinToString(", ").ifBlank { null })
            Fact("Published", book.year?.toString())
            Fact("Format", "${book.format.name} · ${"%.1f".format(book.sizeBytes / 1_048_576.0)} MB")
            Fact(
                "Where",
                when (book.source) {
                    BookSource.FOLDER -> "In your library folder"
                    BookSource.IMPORTED -> "Added to Preform"
                    BookSource.SERVER -> when {
                        book.localPath == null -> "On your PC · downloads when opened"
                        book.pinned -> "On your PC · kept downloaded"
                        else -> "On your PC · downloaded"
                    }
                },
            )
            if (book.rightToLeft) Fact("Reads", "Right to left")
        }
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    value ?: return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyLibrary(folderLabel: String?, onChooseFolder: () -> Unit, onAddBook: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // An empty series: three blank cloth covers waiting for books.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Your", "books", "here").forEach {
                Box(Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(3.dp))) {
                    TypeCover(it, null, emptyList())
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (folderLabel == null) {
                "Choose the folder where you keep your books, add one at a time, or pair with your PC in Settings. EPUB and PDF."
            } else {
                "No EPUB or PDF books found in “$folderLabel”. Add some there, or add a book directly."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onChooseFolder) { Text(if (folderLabel == null) "Choose library folder" else "Choose a different folder") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAddBook) { Text("Add a book") }
    }
}
