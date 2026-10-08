package com.preform.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.desktop.LibraryBook
import com.preform.desktop.Status
import com.preform.sync.Genres

@Composable
fun Sidebar(
    state: WindowState,
    books: List<LibraryBook>,
    folders: List<String>,
    status: Status,
    activity: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(colors.raised)) {
        Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val accent = colors.accent
            Image(remember(accent) { BookMark(accent) }, null, Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("Preform Library", fontFamily = FontFamily.Serif, fontSize = 19.sp, color = colors.text)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            Heading("Library")
            Item(Glyphs.Books, "All books", books.size, state.place == Place.All) { state.go(Place.All) }
            Item(Glyphs.Recent, "Recently added", null, state.place == Place.Recent) { state.go(Place.Recent) }

            if (folders.isNotEmpty()) {
                Heading("Folders")
                FolderTree(folders, books, state)
            }

            val genres = remember(books) { genreCounts(books) }
            if (genres.isNotEmpty()) {
                Heading("Genres")
                genres.forEach { (genre, count) ->
                    Item(Glyphs.Tag, genre, count, state.place == Place.Genre(genre)) { state.go(Place.Genre(genre)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        HorizontalDivider(color = colors.line)
        Column(Modifier.padding(10.dp)) {
            Item(Glyphs.Phone, "Phones", status.devices.size.takeIf { it > 0 }, state.place == Place.Phones) { state.go(Place.Phones) }
            Item(Glyphs.Gear, "Settings", null, state.place == Place.Settings) { state.go(Place.Settings) }
        }
        StatusLine(status, activity)
    }
}

/** Genres with their book counts, fullest first, "Uncategorized" last. */
private fun genreCounts(books: List<LibraryBook>): List<Pair<String, Int>> {
    val counts = HashMap<String, Int>()
    books.forEach { b -> b.catalog.genres.ifEmpty { listOf(Genres.UNCATEGORIZED) }.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    return counts.entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.key == Genres.UNCATEGORIZED }.thenByDescending { it.value }.thenBy { it.key })
        .map { it.key to it.value }
}

@Composable
private fun FolderTree(folders: List<String>, books: List<LibraryBook>, state: WindowState) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val counts = remember(books) {
        val map = HashMap<String, Int>()
        books.forEach { b ->
            // A book counts toward its folder and every folder above it.
            var path = b.catalog.folder
            while (path.isNotEmpty()) {
                map[path] = (map[path] ?: 0) + 1
                path = path.substringBeforeLast('/', "")
            }
        }
        map
    }
    val children = remember(folders) { folders.groupBy { it.substringBeforeLast('/', "") } }

    @Composable
    fun Branch(parent: String, depth: Int) {
        children[parent].orEmpty().forEach { path ->
            val hasChildren = children[path].orEmpty().isNotEmpty()
            val open = path in expanded
            Item(
                icon = Glyphs.Folder,
                label = path.substringAfterLast('/'),
                count = counts[path] ?: 0,
                selected = state.place == Place.Folder(path),
                indent = (depth * 14).dp,
                expander = if (hasChildren) open else null,
                onExpand = { expanded = if (open) expanded - path else expanded + path },
            ) { state.go(Place.Folder(path)) }
            if (open) Branch(path, depth + 1)
        }
    }
    Branch("", 0)
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        color = colors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun Item(
    icon: ImageVector,
    label: String,
    count: Int?,
    selected: Boolean,
    indent: Dp = 0.dp,
    /** Null: no expander; otherwise whether the branch is open. */
    expander: Boolean? = null,
    onExpand: () -> Unit = {},
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.selected else colors.raised)
            .clickable(onClick = onClick)
            .padding(start = 8.dp + indent, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clickable(enabled = expander != null, onClick = onExpand), contentAlignment = Alignment.Center) {
            if (expander != null) {
                Icon(
                    if (expander) Glyphs.ChevronDown else Glyphs.ChevronRight,
                    contentDescription = if (expander) "Collapse" else "Expand",
                    tint = colors.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(icon, null, tint = if (selected) colors.accent else colors.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = colors.text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let { Text("$it", color = colors.muted, fontSize = 12.sp) }
    }
}

@Composable
private fun StatusLine(status: Status, activity: String?) {
    val sharing = status.error == null && status.libraryFound
    Row(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (sharing) colors.ok else colors.danger, CircleShape))
        Spacer(Modifier.width(8.dp))
        val phones = status.devices.size
        Text(
            when {
                status.error != null -> "Not sharing"
                !status.libraryFound -> "Library folder missing"
                activity != null -> activity
                phones == 0 -> "Sharing · no phones yet"
                else -> "Sharing · $phones ${if (phones == 1) "phone" else "phones"}"
            },
            color = colors.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
