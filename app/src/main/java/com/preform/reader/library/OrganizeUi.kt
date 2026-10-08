package com.preform.reader.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.preform.reader.ui.Literata
import com.preform.reader.ui.LiterataDisplay

/** Organizing callbacks: moving books, folders, selection. */
class OrganizeActions(
    val onMove: (List<BookEntity>, String) -> Unit,
    /** Makes a folder in a parent of a library (null: the one being browsed); the books given go into it. */
    val onNewFolder: (parent: String, name: String, thenMove: List<BookEntity>, library: String?) -> Unit,
    val onRenameFolder: (path: String, name: String) -> Unit,
    val onRemoveFolder: (path: String) -> Unit,
    val onSelect: (BookEntity) -> Unit,
    val onToggle: (BookEntity) -> Unit,
    val onClearSelection: () -> Unit,
)

/** What the folder-name dialog is for. */
sealed interface FolderNaming {
    data class New(val parent: String, val thenMove: List<BookEntity> = emptyList(), val library: String? = null) : FolderNaming
    data class Rename(val path: String) : FolderNaming
}

/**
 * "Move to": every folder as an indented list, the top of the library first, and a new
 * folder at the end. The folder the books are in now is marked and does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(
    books: List<BookEntity>,
    folders: List<String>,
    /** Where a new folder is made: the shelf being browsed. */
    here: String,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
    onNewFolder: () -> Unit,
) {
    val current = books.map { it.shelf }.distinct().singleOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                if (books.size == 1) "Move “${books.single().title}”" else "Move ${books.size} books",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = LiterataDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item(key = "") { FolderRow("Top of the library", depth = 0, isHere = current == "") { onMove("") } }
                items(folders, key = { it }) { path ->
                    FolderRow(path.substringAfterLast('/'), depth = path.count { it == '/' } + 1, isHere = current == path) { onMove(path) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onNewFolder).padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(FolderGlyphs.New, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    if (here.isEmpty()) "New folder…" else "New folder in ${here.substringAfterLast('/')}…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FolderRow(name: String, depth: Int, isHere: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isHere, onClick = onClick)
            .padding(start = 24.dp + (depth.coerceAtMost(6) * 16).dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FolderGlyphs.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = Literata,
            color = if (isHere) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isHere) Text("Here now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Naming a new folder, or renaming one. A new folder with several libraries paired and
 * none being browsed asks which library it's for (or the phone's own books).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FolderNameDialog(naming: FolderNaming, libraries: List<LibraryInfo> = emptyList(), onDismiss: () -> Unit, onDone: (String, String?) -> Unit) {
    val initial = (naming as? FolderNaming.Rename)?.path?.substringAfter('|')?.substringAfterLast('/').orEmpty()
    var name by remember { mutableStateOf(initial) }
    val choosing = naming is FolderNaming.New && naming.library == null && libraries.size > 1
    var library by remember { mutableStateOf((naming as? FolderNaming.New)?.library ?: libraries.firstOrNull()?.id) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val title = when (naming) {
        is FolderNaming.Rename -> "Rename folder"
        is FolderNaming.New -> if (naming.parent.isEmpty()) "New folder" else "New folder in ${naming.parent.substringAfterLast('/')}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = LiterataDisplay) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    singleLine = true,
                    placeholder = { Text("Name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (choosing) {
                    Spacer(Modifier.height(12.dp))
                    Text("In", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        (libraries.map { it.id to it.name } + (LibraryOrganizer.PHONE to "This phone")).forEach { (id, label) ->
                            androidx.compose.material3.FilterChip(selected = library == id, onClick = { library = id }, label = { Text(label) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (library == LibraryOrganizer.PHONE && choosing) "For books added on this phone."
                    else "The library follows on the next sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(name, if (choosing) library else (naming as? FolderNaming.New)?.library) }, enabled = name.isNotBlank() && name.trim() != initial) {
                Text(if (naming is FolderNaming.Rename) "Rename" else "Make folder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun RemoveFolderDialog(key: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val path = key.substringAfter('|')
    val parent = path.substringBeforeLast('/', "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove “${path.substringAfterLast('/')}”?", fontFamily = LiterataDisplay) },
        text = {
            Text(
                "Its books move up to ${if (parent.isEmpty()) "the top of the library" else "“${parent.substringAfterLast('/')}”"}, " +
                    "here and in the library. No book is deleted.",
            )
        },
        confirmButton = { TextButton(onClick = onRemove) { Text("Remove folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hairline folder glyphs in the series' icon weight. */
object FolderGlyphs {
    private fun glyph(name: String, extra: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // A folder: tab on the left, body below.
                moveTo(3.5f, 7f)
                verticalLineTo(18f)
                arcTo(1.5f, 1.5f, 0f, false, false, 5f, 19.5f)
                horizontalLineTo(19f)
                arcTo(1.5f, 1.5f, 0f, false, false, 20.5f, 18f)
                verticalLineTo(9.5f)
                arcTo(1.5f, 1.5f, 0f, false, false, 19f, 8f)
                horizontalLineTo(11.5f)
                lineTo(9.5f, 5.5f)
                horizontalLineTo(5f)
                arcTo(1.5f, 1.5f, 0f, false, false, 3.5f, 7f)
                close()
                extra()
            }
        }.build()

    val Folder: ImageVector by lazy { glyph("Folder") {} }

    val New: ImageVector by lazy {
        glyph("NewFolder") {
            moveTo(12f, 11f)
            verticalLineTo(17f)
            moveTo(9f, 14f)
            horizontalLineTo(15f)
        }
    }
}
