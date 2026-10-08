package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.desktop.LibraryBook
import com.preform.desktop.LibraryService
import com.preform.desktop.Reading
import java.text.DecimalFormat

/** The right-hand panel: one book in full, a summary of a multi-selection, or the place itself. */
@Composable
fun BookDetails(
    service: LibraryService,
    covers: CoverCache,
    selected: List<LibraryBook>,
    shown: List<LibraryBook>,
    place: Place,
    onEdit: (LibraryBook) -> Unit,
    onTrashCopy: (String) -> Unit,
    progress: Map<String, Reading> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
        when (selected.size) {
            0 -> PlaceSummary(place, shown)
            1 -> OneBook(
                service, covers, selected.single(),
                onEdit = { onEdit(selected.single()) },
                onTrashCopy = onTrashCopy,
                reading = progress[selected.single().catalog.contentId],
            )
            else -> ManyBooks(selected, covers)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.OneBook(
    service: LibraryService,
    covers: CoverCache,
    book: LibraryBook,
    onEdit: () -> Unit,
    onTrashCopy: (String) -> Unit,
    reading: Reading? = null,
) {
    val c = book.catalog
    BookCover(book, covers, Modifier.fillMaxWidth(0.78f).align(Alignment.CenterHorizontally), full = true)
    Spacer(Modifier.height(22.dp))
    Text(c.title, fontFamily = LiterataDisplay, fontSize = 22.sp, lineHeight = 28.sp, color = colors.text)
    Spacer(Modifier.height(4.dp))
    Text(c.author ?: "Unknown author", color = colors.muted, fontSize = 15.sp, fontFamily = Literata, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    c.series?.let {
        Spacer(Modifier.height(2.dp))
        Text(it + (c.seriesIndex?.let { n -> " · #${indexText(n)}" } ?: ""), color = colors.muted, fontSize = 13.sp)
    }
    c.year?.let {
        Spacer(Modifier.height(2.dp))
        Text("First published $it", color = colors.muted, fontSize = 13.sp)
    }

    reading?.let { r ->
        Spacer(Modifier.height(14.dp))
        ProgressRule(r.progression, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            "${(r.progression * 100).toInt()}% read" + (r.device?.let { " on $it" } ?: ""),
            color = colors.muted,
            fontSize = 12.sp,
        )
    }
    if (c.genres.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(c.genres.joinToString(" · "), color = colors.muted, fontSize = 13.sp)
    }

    c.description?.let {
        Spacer(Modifier.height(16.dp))
        Text(it, color = colors.text, fontSize = 13.sp, lineHeight = 19.sp)
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = colors.line)
    Spacer(Modifier.height(14.dp))
    Fact("Folder", c.folder.ifEmpty { "Library (top)" })
    Fact("File", book.relativePath.substringAfterLast('/'))
    Fact("Format", "${c.format.name} · ${size(c.sizeBytes)}")
    if (c.rightToLeft) Fact("Reads", "Right to left")
    if (book.unreadable) Fact("Note", "This file couldn't be opened as a book.")

    // The same file elsewhere in the library.
    service.library.identicalCopies[c.contentId].orEmpty().forEach { copy ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Copy", color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(64.dp))
            Text(copy, color = colors.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(colors = quietButton(), onClick = { onTrashCopy(copy) }) { Text("To ${service.platform.trashName}", fontSize = 12.sp, color = colors.danger) }
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onEdit) { Text("Edit") }
        OutlinedButton(onClick = { service.library.file(book)?.let(service.platform::reveal) }) {
            Icon(Glyphs.Reveal, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Show in ${service.platform.fileManagerName}")
        }
    }
}

@Composable
private fun ManyBooks(selected: List<LibraryBook>, covers: CoverCache) {
    Text("${selected.size} books selected", fontFamily = LiterataDisplay, fontSize = 22.sp, color = colors.text)
    Spacer(Modifier.height(16.dp))
    // A small fanned stack of the first few covers.
    Row(horizontalArrangement = Arrangement.spacedBy((-36).dp)) {
        selected.take(5).forEach { BookCover(it, covers, Modifier.width(84.dp)) }
    }
    Spacer(Modifier.height(16.dp))
    Text(size(selected.sumOf { it.catalog.sizeBytes }) + " in all", color = colors.muted, fontSize = 13.sp)
}

@Composable
private fun PlaceSummary(place: Place, shown: List<LibraryBook>) {
    Text(placeTitle(place), fontFamily = LiterataDisplay, fontSize = 22.sp, color = colors.text)
    Spacer(Modifier.height(8.dp))
    Text(
        "${shown.size} ${if (shown.size == 1) "book" else "books"} · ${size(shown.sumOf { it.catalog.sizeBytes })}",
        color = colors.muted,
        fontSize = 13.sp,
    )
    val authors = shown.mapNotNull { it.catalog.author }.toSet().size
    if (authors > 0) Text("$authors ${if (authors == 1) "author" else "authors"}", color = colors.muted, fontSize = 13.sp)
    Spacer(Modifier.height(18.dp))
    Text("Select a book to see its details.", color = colors.muted, fontSize = 13.sp)
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(64.dp))
        Text(value, color = colors.text, fontSize = 12.sp)
    }
}

fun size(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) DecimalFormat("0.0").format(mb / 1024) + " GB"
    else if (mb >= 1) DecimalFormat("0.0").format(mb) + " MB"
    else "${(bytes / 1024).coerceAtLeast(1)} KB"
}
