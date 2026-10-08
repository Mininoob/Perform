package com.preform.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.preform.desktop.LibraryBook
import com.preform.desktop.guessGenres

/**
 * Auto genre: a genre for each book from what the library already knows (the author's
 * other books, folder names, titles), shown for review first. Books with no good guess
 * can be looked up online instead. Nothing changes until the person applies it.
 */
@Composable
fun AutoGenreDialog(books: List<LibraryBook>, covers: CoverCache, actions: Actions, onDismiss: () -> Unit) {
    val hints = remember { actions.genreHints() }
    // One genre each (the best guess) keeps it predictable; the sorter is there for more.
    val proposals = remember(books) { books.map { it to guessGenres(it, hints, max = 1) } }
    val guessed = proposals.filter { it.second.isNotEmpty() }
    val unguessed = proposals.filter { it.second.isEmpty() }.map { it.first }
    var included by remember(books) { mutableStateOf(guessed.map { it.first.catalog.contentId }.toSet()) }
    var lookUpRest by remember { mutableStateOf(true) }
    val applying = guessed.filter { it.first.catalog.contentId in included }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = colors.raised, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(640.dp)) {
            Column(Modifier.padding(28.dp)) {
                Text("Auto genre", fontFamily = LiterataDisplay, fontSize = 22.sp, color = colors.text)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        guessed.isEmpty() -> "Nothing in the library points to a genre for these books."
                        else -> "From the author's other books, folder names and titles. Untick any that look wrong."
                    },
                    color = colors.muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))
                if (guessed.isNotEmpty()) {
                    HorizontalDivider(color = colors.line)
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(guessed, key = { it.first.catalog.contentId }) { (book, genres) ->
                            val id = book.catalog.contentId
                            val on = id in included
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { included = if (on) included - id else included + id }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(
                                    checked = on,
                                    onCheckedChange = { included = if (it) included + id else included - id },
                                    colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.muted, checkmarkColor = colors.onAccent),
                                )
                                BookCover(book, covers, Modifier.width(32.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(book.catalog.title, fontFamily = Literata, fontSize = 15.sp, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(book.catalog.author ?: "Unknown author", color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(genres.single(), color = if (on) colors.text else colors.muted, fontSize = 13.sp)
                            }
                        }
                    }
                    HorizontalDivider(color = colors.line)
                    Spacer(Modifier.height(12.dp))
                }
                if (unguessed.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { lookUpRest = !lookUpRest }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = lookUpRest,
                            onCheckedChange = { lookUpRest = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.muted, checkmarkColor = colors.onAccent),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Look up the ${if (unguessed.size == 1) "other book" else "other ${unguessed.size} books"} online (Open Library)",
                            color = colors.text,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(colors = quietButton(), onClick = onDismiss) { Text("Cancel", color = colors.text, fontSize = 13.sp) }
                    Spacer(Modifier.width(8.dp))
                    val lookingUp = lookUpRest && unguessed.isNotEmpty()
                    Button(
                        enabled = applying.isNotEmpty() || lookingUp,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.onAccent),
                        onClick = {
                            onDismiss()
                            actions.autoGenre(applying, if (lookingUp) unguessed else emptyList())
                        },
                    ) {
                        Text(
                            when {
                                applying.isNotEmpty() -> "Give ${if (applying.size == 1) "1 book" else "${applying.size} books"} a genre"
                                else -> "Look them up"
                            },
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}
