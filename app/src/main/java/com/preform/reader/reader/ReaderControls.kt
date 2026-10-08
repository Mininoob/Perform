package com.preform.reader.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.reader.settings.ReadingPreferences
import com.preform.reader.settings.ReadingSettingsPanel
import com.preform.reader.settings.SettingsFormat
import com.preform.reader.ui.BookIcons
import kotlin.math.roundToInt

/**
 * The page's colors, so the controls sit on the page instead of fighting it, with the
 * app theme's accent for anything highlighted.
 */
data class ChromeColors(val background: Color, val content: Color, val accent: Color)

fun ReadingColors.toChrome(accent: Color) = ChromeColors(
    background = Color(background).copy(alpha = 0.96f),
    content = Color(text),
    accent = accent,
)

@Composable
fun ReaderTopBar(
    title: String,
    bookMode: Boolean,
    colors: ChromeColors,
    onBack: () -> Unit,
    onToggleBookMode: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to library", tint = colors.content)
        }
        Text(
            title,
            color = colors.content,
            fontFamily = com.preform.reader.ui.Literata,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleBookMode) {
            Icon(
                imageVector = if (bookMode) BookIcons.On else BookIcons.Off,
                contentDescription = if (bookMode) "Book Mode on" else "Book Mode off",
                tint = if (bookMode) colors.accent else colors.content.copy(alpha = 0.7f),
            )
        }
        TextButton(onClick = onOpenSettings) {
            Text("Aa", color = colors.content, fontFamily = com.preform.reader.ui.Literata, fontSize = 18.sp)
        }
    }
}

@Composable
fun ReaderBottomBar(
    chapter: String?,
    progression: Double,
    colors: ChromeColors,
    onSeek: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ProgressLine(chapter, progression, colors)
        SeekSlider(progression, colors, onSeek)
    }
}

/**
 * Tabletop: the page sits above the hinge, and the bottom half becomes a control deck
 * that is always visible, reachable with a thumb without touching the page.
 */
@Composable
fun FlexControls(
    chapter: String?,
    progression: Double,
    colors: ChromeColors,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(colors.background.copy(alpha = 1f))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProgressLine(chapter, progression, colors)
        SeekSlider(progression, colors, onSeek)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Library", color = colors.content) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page", tint = colors.content)
                }
                Spacer(Modifier.width(24.dp))
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page", tint = colors.content)
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text("Aa", color = colors.content, fontFamily = com.preform.reader.ui.Literata, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun ProgressLine(chapter: String?, progression: Double, colors: ChromeColors) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            chapter.orEmpty(),
            color = colors.content.copy(alpha = 0.75f),
            fontFamily = com.preform.reader.ui.Literata,
            fontStyle = FontStyle.Italic,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${(progression * 100).roundToInt()}%",
            color = colors.content.copy(alpha = 0.75f),
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
        )
    }
}

/**
 * The book's progress rule, made draggable: the same 2dp line as in the library, with a
 * small accent thumb. Moves freely while dragging and jumps only on release, so the book
 * isn't re-located every frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekSlider(progression: Double, colors: ChromeColors, onSeek: (Double) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(progression.toFloat()) }
    if (!dragging) value = progression.toFloat()
    Slider(
        value = value,
        onValueChange = { dragging = true; value = it },
        onValueChangeFinished = { dragging = false; onSeek(value.toDouble()) },
        thumb = {
            Box(Modifier.size(if (dragging) 18.dp else 12.dp).background(colors.accent, CircleShape))
        },
        track = { state ->
            val fraction = (state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.content.copy(alpha = 0.18f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(colors.accent))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    prefs: ReadingPreferences,
    format: SettingsFormat,
    editingThisBook: Boolean,
    bookHasOverrides: Boolean,
    onScopeChange: (thisBook: Boolean) -> Unit,
    onResetBook: () -> Unit,
    onChange: ((ReadingPreferences) -> ReadingPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !editingThisBook,
                    onClick = { onScopeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("All books") }
                SegmentedButton(
                    selected = editingThisBook,
                    onClick = { onScopeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("This book") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editingThisBook) "Changes apply to this book only." else "Changes apply to every book without its own settings.",
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (editingThisBook && bookHasOverrides) {
                    TextButton(onClick = onResetBook) { Text("Reset") }
                }
            }
            Spacer(Modifier.height(16.dp))
            ReadingSettingsPanel(prefs = prefs, format = format, onChange = onChange)
        }
    }
}
