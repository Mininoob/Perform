package com.perform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.perform.desktop.LibraryBook
import com.perform.desktop.guessGenres
import com.perform.sync.Genres

/**
 * Sorting books into genres, one at a time: the book on the left, every genre on the
 * right with the likely ones first. Number keys pick, Enter saves and moves on, → skips.
 * Each book is saved as it's done, so closing part-way keeps what was sorted.
 */
@Composable
fun SortDialog(books: List<LibraryBook>, covers: CoverCache, actions: Actions, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var sorted by remember { mutableIntStateOf(0) }
    val book = books.getOrNull(index)
    var all by remember { mutableStateOf(actions.genres()) }
    val hints = remember { actions.genreHints() }
    val guesses = remember(book) { book?.let { guessGenres(it, hints) }.orEmpty() }
    // The likely genres first, then the rest in the library's usual order.
    val order = remember(guesses, all) { guesses + (all - guesses.toSet()) }
    var naming by remember { mutableStateOf(false) }
    var chosen by remember(book) { mutableStateOf(book?.catalog?.genres.orEmpty().toSet()) }
    val focus = remember { FocusRequester() }

    fun next() {
        index++
    }

    fun save() {
        val b = book ?: return
        if (chosen.isEmpty()) return
        // Keep the series' order rather than the order they were clicked.
        actions.setGenres(listOf(b to all.filter { it in chosen }))
        sorted++
        next()
    }

    fun toggle(genre: String) {
        chosen = if (genre in chosen) chosen - genre else chosen + genre
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Inside the dialog: its window has to exist before it can take the keys.
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Surface(
            color = colors.raised,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(820.dp)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    // While a new genre is being named, keys are for typing.
                    if (e.type != KeyEventType.KeyDown || book == null || naming) return@onPreviewKeyEvent false
                    val digit = DIGITS.indexOf(e.key)
                    when {
                        digit >= 0 && digit < order.size -> toggle(order[digit]).let { true }
                        e.key == Key.Enter || e.key == Key.NumPadEnter -> save().let { true }
                        e.key == Key.DirectionRight -> next().let { true }
                        else -> false
                    }
                },
        ) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Sort into genres", fontFamily = LiterataDisplay, fontSize = 22.sp, color = colors.text, modifier = Modifier.weight(1f))
                    if (book != null) Text("${index + 1} of ${books.size}", color = colors.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))
                if (book == null) {
                    Finished(sorted, books.size, onDismiss)
                    return@Column
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.width(200.dp)) {
                        BookCover(book, covers, Modifier.fillMaxWidth(), full = true)
                        Spacer(Modifier.height(14.dp))
                        Text(book.catalog.title, fontFamily = Literata, fontSize = 17.sp, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(book.catalog.author ?: "Unknown author", book.catalog.year?.toString()).joinToString(" · "),
                            color = colors.muted,
                            fontSize = 13.sp,
                            maxLines = 2,
                        )
                        if (book.catalog.folder.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("In ${shelfPath(book.catalog.folder)}", color = colors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (guesses.isEmpty()) "GENRES" else "LIKELY FIRST",
                            color = colors.muted,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Two columns of genres, read down then across.
                        val half = (order.size + 1) / 2
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(order.take(half), order.drop(half)).forEach { column ->
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    column.forEach { genre ->
                                        val position = order.indexOf(genre)
                                        GenreRow(
                                            genre = genre,
                                            key = DIGIT_LABELS.getOrNull(position),
                                            likely = genre in guesses,
                                            chosen = genre in chosen,
                                        ) { toggle(genre) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        NewGenreField(
                            onFocus = { naming = it },
                            onMake = { name ->
                                actions.newGenre(name)?.let { made ->
                                    all = all + made
                                    chosen = chosen + made
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (naming) "Enter makes the genre" else "Number keys choose · Enter saves · → skips", color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(colors = quietButton(), onClick = onDismiss) { Text("Done for now", color = colors.text, fontSize = 13.sp) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(colors = quietButton(), onClick = ::next) { Text("Skip", color = colors.text, fontSize = 13.sp) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = ::save,
                        enabled = chosen.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                    ) { Text(if (index == books.lastIndex) "Save" else "Save and next", fontSize = 13.sp) }
                }
            }
        }
    }
}

/** "+ New genre": type a name, Enter makes it and picks it for this book. */
@Composable
private fun NewGenreField(onFocus: (Boolean) -> Unit, onMake: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+", color = colors.muted, fontSize = 14.sp, modifier = Modifier.width(24.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocus(it.isFocused) }
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter) && text.isNotBlank()) {
                        onMake(text)
                        text = ""
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) Text("New genre", color = colors.muted, fontSize = 14.sp)
                    field()
                }
            },
        )
    }
}

@Composable
private fun GenreRow(genre: String, key: String?, likely: Boolean, chosen: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (chosen) colors.selected else Color.Transparent, shape)
            .border(1.dp, if (chosen) colors.accent.copy(alpha = 0.6f) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(18.dp)) {
            if (key != null) Text(key, color = colors.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(6.dp))
        Text(genre, color = colors.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // A likely genre carries a small accent point, never a badge.
        if (likely) Box(Modifier.size(5.dp).background(colors.accent, RoundedCornerShape(50)))
    }
}

@Composable
private fun Finished(sorted: Int, total: Int, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when (sorted) {
                0 -> "Nothing sorted this time."
                total -> if (total == 1) "Sorted." else "All $total sorted."
                else -> "$sorted of $total sorted. The rest are still in Uncategorized."
            },
            fontFamily = Literata,
            fontSize = 17.sp,
            color = colors.text,
            modifier = Modifier.widthIn(max = 460.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent)) {
            Text("Close", fontSize = 13.sp)
        }
    }
}

private val DIGITS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine, Key.Zero)
private val DIGIT_LABELS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
