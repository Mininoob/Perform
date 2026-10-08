package com.preform.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.runtime.DisposableEffect
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
import com.preform.desktop.CheckKind
import com.preform.desktop.LibraryBook
import com.preform.desktop.LibraryIndex
import com.preform.desktop.Status
import com.preform.sync.Genres

@Composable
fun Sidebar(
    state: WindowState,
    actions: Actions,
    books: List<LibraryBook>,
    folders: List<String>,
    checks: Map<CheckKind, List<LibraryBook>>,
    status: Status,
    activity: String?,
    customGenres: List<String> = emptyList(),
    peers: List<com.preform.desktop.PeerView> = emptyList(),
    peerBooks: Map<String, List<LibraryBook>> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(colors.raised)) {
        // The mark and name live in the title bar above.
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            Heading("Library")
            // Dropping books on "All books" moves them to the top of the library.
            Item(Glyphs.Books, "All books", books.size, state.place == Place.All, drag = state.drag, dropPath = "") { state.go(Place.All) }
            Item(Glyphs.Recent, "Recently added", null, state.place == Place.Recent) { state.go(Place.Recent) }

            Heading("Folders", action = "New folder") { state.dialog = Dialog.NewFolder("") }
            if (folders.isEmpty()) {
                Text("No folders yet.", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
            } else {
                FolderTree(folders, books, state, actions)
            }

            val genres = remember(books, customGenres) { genreCounts(books, customGenres) }
            Heading("Genres", action = "New genre") { state.dialog = Dialog.NewGenre(emptyList()) }
            genres.forEach { (genre, count) ->
                // Dropping books on a genre gives them that genre.
                Item(
                    Glyphs.Tag, genre, count, state.place == Place.Genre(genre),
                    drag = state.drag,
                    dropPath = (GENRE_DROP + genre).takeIf { genre != Genres.UNCATEGORIZED },
                    menu = if (genre in customGenres) {
                        {
                            listOf(
                                ContextMenuItem("Rename…") { state.dialog = Dialog.RenameGenre(genre) },
                                ContextMenuItem("Remove genre…") { state.dialog = Dialog.RemoveGenre(genre, count) },
                            )
                        }
                    } else {
                        null
                    },
                ) { state.go(Place.Genre(genre)) }
            }
            if (peers.isNotEmpty()) {
                Heading("Other libraries")
                peers.forEach { view -> PeerItems(view, peerBooks[view.peer.peerId].orEmpty(), books, state) }
            }
            Heading("Checks")
            val found = checks.filter { it.value.isNotEmpty() }
            if (found.isEmpty()) {
                Text("All tidy.", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
            }
            found.forEach { (kind, list) ->
                Item(Glyphs.Check, kind.label, list.size, state.place == Place.Check(kind)) { state.go(Place.Check(kind)) }
            }
            Spacer(Modifier.height(12.dp))
        }

        HorizontalDivider(color = colors.line)
        Column(Modifier.padding(10.dp)) {
            Item(Glyphs.Phone, "Devices", status.devices.size.takeIf { it > 0 }, state.place == Place.Devices) { state.go(Place.Devices) }
            Item(Glyphs.Gear, "Settings", null, state.place == Place.Settings) { state.go(Place.Settings) }
        }
        StatusLine(status, activity)
    }
}

/**
 * Genres with their book counts, fullest first, "Uncategorized" last. The library's own
 * genres show even while empty, so books can be dragged onto them.
 */
private fun genreCounts(books: List<LibraryBook>, custom: List<String>): List<Pair<String, Int>> {
    val counts = HashMap<String, Int>()
    custom.forEach { counts[it] = 0 }
    books.forEach { b -> b.catalog.genres.ifEmpty { listOf(Genres.UNCATEGORIZED) }.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
    return counts.entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.key == Genres.UNCATEGORIZED }.thenByDescending { it.value }.thenBy { it.key })
        .map { it.key to it.value }
}

@Composable
private fun FolderTree(folders: List<String>, books: List<LibraryBook>, state: WindowState, actions: Actions) {
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
                drag = state.drag,
                dropPath = path,
                menu = {
                    listOf(
                        ContextMenuItem("New folder inside…") { state.dialog = Dialog.NewFolder(path) },
                        ContextMenuItem("Rename…") { state.dialog = Dialog.RenameFolder(path) },
                        ContextMenuItem("Show in ${actions.platform.fileManagerName}") { actions.revealFolder(path) },
                        ContextMenuItem("Remove folder…") { state.dialog = Dialog.DeleteFolder(path) },
                    )
                },
            ) { state.go(Place.Folder(path)) }
            if (open) Branch(path, depth + 1)
        }
    }
    Branch("", 0)
}

@Composable
private fun Heading(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            color = colors.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                "+",
                color = colors.muted,
                fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp),
            )
        }
    }
}

@Composable
fun Item(
    icon: ImageVector,
    label: String,
    count: Int?,
    selected: Boolean,
    indent: Dp = 0.dp,
    /** A small status dot after the name: another library being online. */
    dot: Color? = null,
    /** Null: no expander; otherwise whether the branch is open. */
    expander: Boolean? = null,
    onExpand: () -> Unit = {},
    /** While books are dragged, this row accepts them for [dropPath]. */
    drag: DragState? = null,
    dropPath: String? = null,
    menu: (() -> List<ContextMenuItem>)? = null,
    onClick: () -> Unit,
) {
    val hovered = drag != null && dropPath != null && drag.hover == dropPath
    if (drag != null && dropPath != null) {
        DisposableEffect(drag, dropPath) { onDispose { drag.targets.remove(dropPath) } }
    }
    val row = @Composable {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (drag != null && dropPath != null) {
                    Modifier.onGloballyPositioned { drag.targets[dropPath] = it.boundsInWindow() }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    hovered -> colors.accent.copy(alpha = 0.30f)
                    selected -> colors.selected
                    else -> colors.raised
                },
            )
            .border(1.dp, if (hovered) colors.accent else Color.Transparent, RoundedCornerShape(8.dp))
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
        // Next to the count, so the name keeps the room it needs.
        dot?.let {
            Box(Modifier.size(6.dp).background(it, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        count?.let { Text("$it", color = colors.muted, fontSize = 12.sp) }
    }
    }
    if (menu != null) ContextMenuArea(items = menu) { row() } else row()
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

/**
 * Another library in the sidebar: its name with an online dot and count; once opened,
 * what it has that this library doesn't (and the reverse), and its top folders.
 */
@Composable
private fun PeerItems(view: com.preform.desktop.PeerView, theirs: List<LibraryBook>, ours: List<LibraryBook>, state: WindowState) {
    val id = view.peer.peerId
    val place = state.place
    val open = (place is Place.Peer && place.peerId == id) || (place is Place.Compare && place.peerId == id)
    Item(
        if (view.peer.kind == com.preform.sync.PeerKind.PHONE) Glyphs.Phone else Glyphs.Books,
        view.peer.name,
        view.bookCount,
        place == Place.Peer(id),
        dot = if (view.online) colors.ok else colors.line,
    ) { state.go(Place.Peer(id)) }
    if (!open) return
    val notInYours = remember(theirs, ours) { LibraryIndex(ours).let { index -> theirs.count { !index.has(it) } } }
    val onlyHere = remember(theirs, ours) { LibraryIndex(theirs).let { index -> ours.count { !index.has(it) } } }
    Item(Glyphs.Books, "Not in yours", notInYours, place == Place.Compare(id, theirs = true), indent = 14.dp) { state.go(Place.Compare(id, theirs = true)) }
    Item(Glyphs.Books, "Only here", onlyHere, place == Place.Compare(id, theirs = false), indent = 14.dp) { state.go(Place.Compare(id, theirs = false)) }
    val folders = remember(theirs) {
        theirs.map { it.catalog.folder.substringBefore('/') }.filter(String::isNotEmpty).groupingBy { it }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }
    folders.forEach { (folder, count) ->
        Item(Glyphs.Folder, folder, count, place == Place.Peer(id, folder), indent = 14.dp) { state.go(Place.Peer(id, folder)) }
    }
}

/** Drop-target keys for genres, kept apart from folder paths. */
const val GENRE_DROP = "genre:"
