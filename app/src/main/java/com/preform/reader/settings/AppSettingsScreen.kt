package com.preform.reader.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppSettingsState(
    val folderLabel: String?,
    val appTheme: AppTheme,
    val keepScreenOn: Boolean,
    val backSwipeEnabled: Boolean,
    val backSwipeForNext: SwipeDirection,
    val showDiagnostics: Boolean,
)

class AppSettingsActions(
    val onBack: () -> Unit,
    val onChooseFolder: () -> Unit,
    val onRescan: () -> Unit,
    val onRemoveFolder: () -> Unit,
    val onAppTheme: (AppTheme) -> Unit,
    val onKeepScreenOn: (Boolean) -> Unit,
    val onReadingDefaults: () -> Unit,
    val onBackSwipeEnabled: (Boolean) -> Unit,
    val onBackSwipeForNext: (SwipeDirection) -> Unit,
    val onDiagnostics: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(state: AppSettingsState, actions: AppSettingsActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Settings", fontFamily = FontFamily.Serif) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Group("Library folder") {
                Text(
                    state.folderLabel?.let { "Reading books from “$it”." }
                        ?: "No folder chosen. Books in the folder you choose appear here automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onChooseFolder) {
                        Text(if (state.folderLabel == null) "Choose folder" else "Change")
                    }
                    if (state.folderLabel != null) {
                        TextButton(onClick = actions.onRescan) { Text("Rescan now") }
                        TextButton(onClick = actions.onRemoveFolder) { Text("Remove") }
                    }
                }
            }

            Group("Appearance") {
                Text("App theme", style = MaterialTheme.typography.bodyLarge)
                AppTheme.entries.forEach { theme ->
                    Choice(
                        label = when (theme) {
                            AppTheme.SYSTEM -> "Match system"
                            AppTheme.LIGHT -> "Light"
                            AppTheme.DARK -> "Dark"
                        },
                        selected = state.appTheme == theme,
                        onSelect = { actions.onAppTheme(theme) },
                    )
                }
                Hint("The library and settings. Books use their own reading theme.")
            }

            Group("Reading") {
                Link("Default reading settings", "Mode, theme, text size, font, spacing", actions.onReadingDefaults)
                Toggle(
                    label = "Keep screen on while reading",
                    detail = "Uses noticeably more battery. Auto-scroll always keeps the screen on.",
                    checked = state.keepScreenOn,
                    onChange = actions.onKeepScreenOn,
                )
            }

            Group("Book Mode") {
                Toggle(
                    label = "Swipe the back to turn pages",
                    detail = "With Book Mode on and the phone open, swipe across the cover on the back.",
                    checked = state.backSwipeEnabled,
                    onChange = actions.onBackSwipeEnabled,
                )
                Column(Modifier.alpha(if (state.backSwipeEnabled) 1f else 0.4f)) {
                    Choice(
                        label = "Swipe left for the next page",
                        selected = state.backSwipeForNext == SwipeDirection.LEFT,
                        enabled = state.backSwipeEnabled,
                        onSelect = { actions.onBackSwipeForNext(SwipeDirection.LEFT) },
                    )
                    Choice(
                        label = "Swipe right for the next page",
                        selected = state.backSwipeForNext == SwipeDirection.RIGHT,
                        enabled = state.backSwipeEnabled,
                        onSelect = { actions.onBackSwipeForNext(SwipeDirection.RIGHT) },
                    )
                }
                Hint("Directions are as you see them from the front, and flip for right-to-left books.")
            }

            if (state.showDiagnostics) {
                Group("Developer") {
                    Link("Diagnostics", "Posture, hinge, displays, battery current", actions.onDiagnostics)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Link(label: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDefaultsScreen(
    prefs: ReadingPreferences,
    onChange: ((ReadingPreferences) -> ReadingPreferences) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Default reading settings", fontFamily = FontFamily.Serif) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = onReset) { Text("Reset") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Hint("Used by every book that doesn't have its own settings. Change one book from inside it with Aa → This book.")
            Spacer(Modifier.height(20.dp))
            ReadingSettingsPanel(prefs = prefs, format = SettingsFormat.ANY, onChange = onChange)
            Spacer(Modifier.height(32.dp))
        }
    }
}
