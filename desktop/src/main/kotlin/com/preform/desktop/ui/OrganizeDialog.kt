package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.preform.desktop.OrganizeScheme

private enum class Scope { LIBRARY, FOLDER, SELECTION }

/**
 * Auto-organize: pick a layout, see exactly what would move where, untick anything to
 * leave alone, then apply. Undo is offered afterwards.
 */
@Composable
fun OrganizeDialog(state: WindowState, actions: Actions, onDismiss: () -> Unit) {
    val folder = (state.place as? Place.Folder)?.path
    val selected = state.visible.filter { it.catalog.contentId in state.selection }
    var scheme by remember { mutableStateOf<OrganizeScheme?>(OrganizeScheme.BY_GENRE) }
    var rename by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf(if (selected.size > 1) Scope.SELECTION else if (folder != null) Scope.FOLDER else Scope.LIBRARY) }
    var skipped by remember { mutableStateOf(emptySet<String>()) }

    val books = when (scope) {
        Scope.SELECTION -> selected
        Scope.FOLDER -> state.visible
        Scope.LIBRARY -> state.allBooks
    }
    val base = if (scope == Scope.FOLDER) folder.orEmpty() else ""
    val plan = remember(books, scheme, rename, base) { actions.plan(books, scheme, rename, base) }
    val chosen = plan.filter { it.contentId !in skipped }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = colors.raised, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(940.dp).height(640.dp)) {
            Column(Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.Sparkle, null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Organize", fontFamily = LiterataDisplay, fontSize = 22.sp, color = colors.text)
                }
                Text("Nothing moves until you choose to. You can undo it afterwards.", color = colors.muted, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))

                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    // Choices
                    Column(Modifier.width(300.dp)) {
                        Label("Folders")
                        OrganizeScheme.entries.forEach { option ->
                            Choice(option.label, option.example, scheme == option) { scheme = option; skipped = emptySet() }
                        }
                        Choice("Keep current folders", null, scheme == null) { scheme = null; skipped = emptySet() }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { rename = !rename; skipped = emptySet() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(rename, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Rename files", color = colors.text, fontSize = 14.sp)
                                Text("Author - Title.epub", color = colors.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Label("Books")
                        Choice("Whole library", "${state.allBooks.size} books", scope == Scope.LIBRARY) { scope = Scope.LIBRARY; skipped = emptySet() }
                        if (folder != null) {
                            Choice("This folder", "${shelfPath(folder)} · ${state.visible.size} books", scope == Scope.FOLDER) {
                                scope = Scope.FOLDER
                                skipped = emptySet()
                            }
                        }
                        if (selected.isNotEmpty()) {
                            Choice("Selected books", "${selected.size} selected", scope == Scope.SELECTION) { scope = Scope.SELECTION; skipped = emptySet() }
                        }
                    }

                    // Preview
                    Column(Modifier.weight(1f).fillMaxSize().background(colors.background, RoundedCornerShape(12.dp)).padding(16.dp)) {
                        Text(
                            when {
                                plan.isEmpty() -> "Everything is already in place."
                                chosen.size == 1 -> "1 book will move"
                                else -> "${chosen.size} books will move"
                            },
                            color = colors.text,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(plan, key = { it.contentId }) { move ->
                                val on = move.contentId !in skipped
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { skipped = if (on) skipped + move.contentId else skipped - move.contentId }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = colors.accent))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(move.title, color = if (on) colors.text else colors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(move.from, color = colors.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("→ ${move.to}", color = if (on) colors.accent else colors.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = chosen.isNotEmpty(), onClick = { onDismiss(); actions.applyPlan(chosen) }) {
                        Text(if (chosen.size == 1) "Move 1 book" else "Move ${chosen.size} books")
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Choice(title: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = colors.accent))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = colors.text, fontSize = 14.sp)
            detail?.let { Text(it, color = colors.muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
