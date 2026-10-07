package com.preform.reader.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.reader.ui.rememberCover
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onOpen: (BookEntity) -> Unit,
    onRemove: (BookEntity) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onRefresh: () -> Unit,
    onChooseFolder: () -> Unit,
    onAddBook: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (searching) {
                        TextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search title or author") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Library", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onAddBook) {
                            Icon(Icons.Default.Add, contentDescription = "Add book")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                !state.loaded -> Unit
                state.books.isEmpty() && state.query.isBlank() && state.scan == ScanState.Idle ->
                    EmptyLibrary(state.folderLabel, onChooseFolder, onAddBook)
                else -> BookGrid(state, onOpen, onRemove, onSortChange)
            }
        }
    }
}

@Composable
private fun BookGrid(
    state: LibraryUiState,
    onOpen: (BookEntity) -> Unit,
    onRemove: (BookEntity) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        (state.scan as? ScanState.Scanning)?.let { scan ->
            item(span = { GridItemSpan(maxLineSpan) }) { ScanProgress(scan) }
        }

        val hero = state.continueReading
        if (hero != null && state.query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReading(hero, opening = state.opening == hero.id, onOpen = { onOpen(hero) })
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SortRow(count = state.books.size, sort = state.sort, onSortChange = onSortChange)
        }

        items(state.books, key = { it.id }) { book ->
            BookTile(
                book = book,
                opening = state.opening == book.id,
                onOpen = { onOpen(book) },
                onRemove = { onRemove(book) },
            )
        }

        if (state.books.isEmpty() && state.query.isNotBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nothing matches “${state.query}”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
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

@Composable
private fun ContinueReading(book: BookEntity, opening: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(book, opening, Modifier.width(92.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "CONTINUE READING",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                book.title,
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            ProgressBar(book.progression, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                "${(book.progression * 100).roundToInt()}% read",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SortRow(count: Int, sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSortChange(option); open = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(book: BookEntity, opening: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }

    Box {
        Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true })) {
            Cover(book, opening, Modifier.fillMaxWidth())
            if (book.progression > 0) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(book.progression, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Serif,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Book info") }, onClick = { menu = false; info = true })
            DropdownMenuItem(text = { Text("Remove from library") }, onClick = { menu = false; onRemove() })
        }
    }

    if (info) BookInfoDialog(book, onDismiss = { info = false })
}

/** A 2:3 cover, or a colored title card when the book has none. */
@Composable
private fun Cover(book: BookEntity, opening: Boolean, modifier: Modifier) {
    val image = rememberCover(book.coverThumbPath)
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(book.coverColor ?: 0xFF3D2519.toInt())),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (book.coverThumbPath == null) {
            Text(
                book.title,
                color = Color(0xFFF3E4D2),
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
        if (opening) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Double, modifier: Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.toFloat().coerceIn(0f, 1f))
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun BookInfoDialog(book: BookEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(book.title, fontFamily = FontFamily.Serif) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                book.author?.let { Text("By $it") }
                Text("${book.format.name} · ${"%.1f".format(book.sizeBytes / 1_048_576.0)} MB")
                Text(if (book.source == BookSource.FOLDER) "In your library folder" else "Added to Preform")
                if (book.rightToLeft) Text("Reads right to left")
                Text("${(book.progression * 100).roundToInt()}% read")
            }
        },
    )
}

@Composable
private fun EmptyLibrary(folderLabel: String?, onChooseFolder: () -> Unit, onAddBook: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your library is empty", fontFamily = FontFamily.Serif, fontSize = 24.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (folderLabel == null) {
                "Choose the folder where you keep your books, or add one at a time. EPUB and PDF."
            } else {
                "No EPUB or PDF books found in “$folderLabel”. Add some there, or add a book directly."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onChooseFolder) { Text(if (folderLabel == null) "Choose library folder" else "Choose a different folder") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAddBook) { Text("Add a book") }
    }
}
