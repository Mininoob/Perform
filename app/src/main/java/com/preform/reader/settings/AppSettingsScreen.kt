package com.preform.reader.settings

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.preform.reader.ui.palette
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.preform.reader.ui.colorOn
import com.preform.reader.ui.label
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppSettingsState(
    val folderLabel: String?,
    /** The paired PC's name, or null when not paired. */
    val privateLibrary: String?,
    val appTheme: AppTheme,
    val themeSchedule: ThemeSchedule,
    val keepScreenOn: Boolean,
    val coverControls: CoverControls,
    val backSwipeForNext: SwipeDirection,
    val showDiagnostics: Boolean,
)

class AppSettingsActions(
    val onBack: () -> Unit,
    val onChooseFolder: () -> Unit,
    val onRescan: () -> Unit,
    val onRemoveFolder: () -> Unit,
    val onPrivateLibrary: () -> Unit,
    val onThemeBase: (ThemeBase) -> Unit,
    val onThemeAccent: (ThemeAccent) -> Unit,
    val onScheduleChange: ((ThemeSchedule) -> ThemeSchedule) -> Unit,
    val onKeepScreenOn: (Boolean) -> Unit,
    val onReadingDefaults: () -> Unit,
    val onCoverControls: (CoverControls) -> Unit,
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

            Group("Private library") {
                Link(
                    label = state.privateLibrary?.let { "Connected to $it" } ?: "Pair with your PC",
                    detail = if (state.privateLibrary == null) "Read the books on your PC, at home or away" else "Sync, downloads and storage",
                    onClick = actions.onPrivateLibrary,
                )
            }

            Group("Theme") {
                val schedule = state.themeSchedule
                val accent = state.appTheme.accent
                Toggle(
                    label = "Switch by time of day",
                    detail = if (schedule.enabled) {
                        "Light from ${schedule.dayStartsAt.display()}, dark from ${schedule.nightStartsAt.display()}."
                    } else {
                        "A light base by day and a dark one at night."
                    },
                    checked = schedule.enabled,
                    onChange = { on -> actions.onScheduleChange { it.copy(enabled = on) } },
                )
                if (schedule.enabled) {
                    ScheduleHeader("Day", schedule.dayStartsAt) { time ->
                        actions.onScheduleChange { it.copy(dayStartsAt = time) }
                    }
                    BaseRow(
                        bases = ThemeBase.entries.filter { !it.dark },
                        selected = schedule.day,
                        accent = accent,
                        onSelect = { base -> actions.onScheduleChange { it.copy(day = base) } },
                    )
                    ScheduleHeader("Night", schedule.nightStartsAt) { time ->
                        actions.onScheduleChange { it.copy(nightStartsAt = time) }
                    }
                    BaseRow(
                        bases = ThemeBase.entries.filter { it.dark },
                        selected = schedule.night,
                        accent = accent,
                        onSelect = { base -> actions.onScheduleChange { it.copy(night = base) } },
                    )
                } else {
                    BaseRow(ThemeBase.entries.filter { !it.dark }, state.appTheme.base, accent, actions.onThemeBase)
                    BaseRow(ThemeBase.entries.filter { it.dark }, state.appTheme.base, accent, actions.onThemeBase)
                }
                Spacer(Modifier.height(4.dp))
                Text("Accent  ·  ${accent.label}", style = MaterialTheme.typography.titleSmall)
                AccentPicker(selected = accent, base = state.appTheme.base, onSelect = actions.onThemeAccent)
                Hint("The library, settings and reading controls. Books in the App reading theme follow it too.")
            }

            Group("Reading") {
                Link("Default reading settings", "Mode, theme, text size, font, spacing", actions.onReadingDefaults)
                Toggle(
                    label = "Keep screen on while reading",
                    detail = "Uses noticeably more battery.",
                    checked = state.keepScreenOn,
                    onChange = actions.onKeepScreenOn,
                )
            }

            Group("Book Mode") {
                Text("Cover screen controls", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "With the phone open, turn pages from the cover on the back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CoverControls.OFF to "Off",
                        CoverControls.SWIPE to "Swipe",
                        CoverControls.TAP to "Tap",
                        CoverControls.SWIPE_AND_TAP to "Swipe & tap",
                    ).forEach { (controls, label) ->
                        FilterChip(
                            selected = state.coverControls == controls,
                            onClick = { actions.onCoverControls(controls) },
                            label = { Text(label) },
                        )
                    }
                }
                if (state.coverControls.tap) {
                    Hint("Tap the left or right third of the back. The middle is left alone so holding the phone doesn't turn pages.")
                }
                val flipped = state.backSwipeForNext == SwipeDirection.RIGHT
                val swipeText = if (flipped) "swipe right or tap the left side" else "swipe left or tap the right side"
                Toggle(
                    label = "Flip direction",
                    detail = "Next page: $swipeText, as you see it from the front. Right-to-left books go the other way.",
                    checked = flipped,
                    enabled = state.coverControls != CoverControls.OFF,
                    onChange = { flip ->
                        actions.onBackSwipeForNext(if (flip) SwipeDirection.RIGHT else SwipeDirection.LEFT)
                    },
                )
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
private fun Toggle(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .toggleable(value = checked, enabled = enabled, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(start = 12.dp))
    }
}

/** "Day · from 7:00 AM", where tapping the time opens a time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleHeader(label: String, startsAt: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { picking = true }) { Text("from ${startsAt.display()}") }
    }
    if (picking) {
        val picker = rememberTimePickerState(
            initialHour = startsAt.hour,
            initialMinute = startsAt.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("$label starts at") },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(picker.hour, picker.minute))
                    picking = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

/** A time the way this phone shows times: 7:00 AM, or 07:00. */
private fun LocalTime.display(): String = format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** One row of base themes, each a small preview card wearing the current accent. */
@Composable
private fun BaseRow(bases: List<ThemeBase>, selected: ThemeBase, accent: ThemeAccent, onSelect: (ThemeBase) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        bases.forEach { base ->
            BaseCard(AppTheme(base, accent), base == selected, { onSelect(base) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BaseCard(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = theme.palette
    Column(modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(12.dp))
                .background(p.background)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Aa", color = p.text, fontFamily = FontFamily.Serif, fontSize = 18.sp)
                Box(Modifier.fillMaxWidth(0.8f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.muted))
                Box(Modifier.fillMaxWidth(0.55f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.muted))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(width = 22.dp, height = 10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(p.accent),
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = p.accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp),
                )
            }
        }
        Text(
            theme.base.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

/** The accent palette: a dot per color, shown as it will look on the current base. */
@Composable
private fun AccentPicker(selected: ThemeAccent, base: ThemeBase, onSelect: (ThemeAccent) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeAccent.entries.forEach { accent ->
            val chosen = accent == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .border(
                        width = if (chosen) 2.5.dp else 1.dp,
                        color = if (chosen) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .selectable(selected = chosen, onClick = { onSelect(accent) }, role = Role.RadioButton)
                    .semantics { contentDescription = accent.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accent.colorOn(base)),
                )
            }
        }
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
