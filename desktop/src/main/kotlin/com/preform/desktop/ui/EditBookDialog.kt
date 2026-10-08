package com.preform.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.preform.desktop.BookEdits
import com.preform.desktop.LibraryBook
import com.preform.sync.Genres
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import java.awt.FileDialog
import java.awt.Frame

/** Edit a book's details by hand. Saved into the catalog, and into the file when that's on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBookDialog(
    book: LibraryBook,
    covers: CoverCache,
    actions: Actions,
    writesIntoFiles: Boolean,
    onDismiss: () -> Unit,
) {
    val c = book.catalog
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(c.title) }
    var author by remember { mutableStateOf(c.author.orEmpty()) }
    var series by remember { mutableStateOf(c.series.orEmpty()) }
    var seriesIndex by remember { mutableStateOf(c.seriesIndex?.let(::indexText).orEmpty()) }
    var year by remember { mutableStateOf(c.year?.toString().orEmpty()) }
    var genres by remember { mutableStateOf(c.genres) }
    var description by remember { mutableStateOf(c.description.orEmpty()) }
    var newCover by remember { mutableStateOf<ByteArray?>(null) }
    var newCoverImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var finding by remember { mutableStateOf(false) }
    var coverNote by remember { mutableStateOf<String?>(null) }

    fun useCover(bytes: ByteArray?) {
        val image = bytes?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
        if (image == null) {
            coverNote = "That image couldn't be read."
            return
        }
        newCover = bytes
        newCoverImage = image
        coverNote = null
    }

    val yearValue = year.trim().toIntOrNull()
    val indexValue = seriesIndex.trim().replace(',', '.').toDoubleOrNull()
    val valid = title.isNotBlank() && (year.isBlank() || yearValue != null) && (seriesIndex.isBlank() || indexValue != null)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = colors.raised,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(760.dp).heightIn(max = 720.dp),
        ) {
            Column(Modifier.padding(28.dp)) {
                Text("Edit details", fontFamily = LiterataDisplay, fontSize = 22.sp, color = colors.text)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Cover
                    Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val preview = newCoverImage
                        if (preview != null) {
                            Image(
                                preview,
                                null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)),
                            )
                        } else {
                            BookCover(book, covers, Modifier.fillMaxWidth(), full = true)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { chooseImage()?.let(::useCover) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose image…", fontSize = 13.sp)
                        }
                        TextButton(
                            enabled = !finding,
                            onClick = {
                                finding = true
                                coverNote = null
                                scope.launch {
                                    val found = actions.findCover(title, author.ifBlank { null })
                                    finding = false
                                    if (found == null) coverNote = "No cover found online." else useCover(found)
                                }
                            },
                        ) { Text(if (finding) "Searching…" else "Find online", fontSize = 13.sp) }
                        coverNote?.let { Text(it, color = colors.muted, fontSize = 12.sp) }
                    }

                    // Fields
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field("Title", title, { title = it })
                        Field("Author", author, { author = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Field("Series", series, { series = it }, Modifier.weight(1f))
                            Field("Number", seriesIndex, { seriesIndex = it }, Modifier.width(96.dp), error = seriesIndex.isNotBlank() && indexValue == null)
                            Field("Year", year, { year = it.filter(Char::isDigit).take(4) }, Modifier.width(96.dp))
                        }
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Genres", color = colors.muted, fontSize = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Genres.ALL.forEach { genre ->
                                val on = genre in genres
                                Text(
                                    genre,
                                    color = if (on) colors.onAccent else colors.text,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (on) colors.accent else colors.background)
                                        .border(1.dp, if (on) colors.accent else colors.line, RoundedCornerShape(12.dp))
                                        .clickable { genres = if (on) genres - genre else genres + genre }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (writesIntoFiles) "Saved into the book file too; the original is kept."
                        else "Saved in the library only (writing into files is off in Settings).",
                        color = colors.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = valid,
                        onClick = {
                            onDismiss()
                            actions.saveEdits(
                                book,
                                BookEdits(title, author, series, indexValue, yearValue, genres, description, newCover),
                            )
                        },
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), error: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error,
        modifier = modifier,
    )
}

/** "2" for 2.0, "2.5" otherwise. */
fun indexText(index: Double): String = if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()

private fun chooseImage(): ByteArray? {
    val dialog = FileDialog(null as Frame?, "Choose a cover image", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
        isVisible = true
    }
    return dialog.files.firstOrNull()?.readBytes()
}
