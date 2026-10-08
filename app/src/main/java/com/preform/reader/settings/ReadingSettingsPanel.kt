package com.preform.reader.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.reader.reader.colors
import kotlin.math.roundToInt

/** Which format's options to show: one book's, or both (for the global defaults). */
enum class SettingsFormat { EPUB, PDF, ANY }

/**
 * Every reading setting, shared by the in-reader sheet and the defaults screen.
 *
 * [prefs] are the effective values to display; [onChange] receives an edit to apply
 * to whichever layer the caller is editing (global or this book).
 */
@Composable
fun ReadingSettingsPanel(
    prefs: ReadingPreferences,
    format: SettingsFormat,
    onChange: ((ReadingPreferences) -> ReadingPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    val epub = format != SettingsFormat.PDF
    val pdf = format != SettingsFormat.EPUB

    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Section("Reading mode") {
            Chips(
                options = buildList {
                    add(ReadingMode.PAGES to "Pages")
                    // Readium's free PDF engine can't lay out spreads.
                    if (epub) add(ReadingMode.SPREAD to "Two-page")
                    add(ReadingMode.SCROLL to "Scroll")
                },
                selected = prefs.mode,
                onSelect = { mode -> onChange { it.copy(mode = mode) } },
            )
            if (prefs.mode == ReadingMode.SPREAD && epub) {
                Hint("Two pages side by side when the phone is open; one page on the cover screen.")
            }
            if (pdf) {
                Toggle(
                    label = if (format == SettingsFormat.ANY) "Lock PDF pages" else "Lock pages",
                    detail = if (prefs.mode == ReadingMode.SCROLL) {
                        "Scroll up and down only, with no zooming or drifting sideways. Turn off to zoom freely."
                    } else {
                        "One whole page at a time. Swipe, or tap the left or right edge, to turn it. " +
                            "Turn off to zoom and drag freely."
                    },
                    checked = prefs.pageLock == true,
                    onChange = { lock -> onChange { it.copy(pageLock = lock) } },
                )
            }
        }

        Section("Theme") {
            ThemeRow(prefs.theme) { theme -> onChange { it.copy(theme = theme) } }
            if (prefs.theme == ReadingTheme.CUSTOM) {
                Spacer(Modifier.height(6.dp))
                SwatchRow("Page", ReadingSwatches.backgrounds, prefs.backgroundColor) { c ->
                    onChange { it.copy(backgroundColor = c) }
                }
                SwatchRow("Text", ReadingSwatches.texts, prefs.textColor) { c -> onChange { it.copy(textColor = c) } }
            }
        }

        if (epub) {
            Section("Text size") {
                LabeledSlider(
                    label = "Size",
                    value = (prefs.fontSize ?: 1.0).toFloat(),
                    range = 0.75f..2.0f,
                    display = { "${(it * 100).roundToInt()}%" },
                    onDone = { size -> onChange { it.copy(fontSize = size.toDouble()) } },
                )
            }

            Section("Font") {
                Chips(
                    options = listOf(
                        ReadingFont.ORIGINAL to "Book's own",
                        ReadingFont.SERIF to "Serif",
                        ReadingFont.SANS_SERIF to "Sans-serif",
                        ReadingFont.MONOSPACE to "Monospace",
                    ),
                    selected = prefs.fontFamily,
                    onSelect = { font -> onChange { it.copy(fontFamily = font) } },
                )
            }

            Section("Line spacing") {
                Chips(
                    options = listOf(null to "Book's own", 1.2 to "Tight", 1.5 to "Normal", 1.8 to "Loose"),
                    selected = prefs.lineHeight,
                    onSelect = { height -> onChange { it.copy(lineHeight = height) } },
                )
            }

            Section("Margins") {
                LabeledSlider(
                    label = "Margins",
                    value = (prefs.pageMargins ?: 1.0).toFloat(),
                    range = 0.5f..2.5f,
                    display = { "%.1f×".format(it) },
                    onDone = { m -> onChange { it.copy(pageMargins = m.toDouble()) } },
                )
            }

            Section("Alignment") {
                Chips(
                    options = listOf(null to "Book's own", ReadingAlign.START to "Left", ReadingAlign.JUSTIFY to "Justified"),
                    selected = prefs.textAlign,
                    onSelect = { align -> onChange { it.copy(textAlign = align) } },
                )
            }
        }

        if (pdf) {
            Section(if (format == SettingsFormat.ANY) "PDF page fit" else "Page fit") {
                Chips(
                    options = listOf(PdfFit.WIDTH to "Fit width", PdfFit.PAGE to "Fit whole page"),
                    selected = prefs.pdfFit,
                    onSelect = { fit -> onChange { it.copy(pdfFit = fit) } },
                )
                if (prefs.pageLock == true && prefs.mode != ReadingMode.SCROLL) {
                    Hint("Locked pages always show the whole page; this applies when scrolling.")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Toggle(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A single-choice chip row. Options may include null, meaning "defer to the book". */
@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

/** Slides freely, but only commits on release, so dragging doesn't re-lay-out the book every frame. */
@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onDone: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onDone(current) },
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
        )
        Spacer(Modifier.width(12.dp))
        Text(display(current), modifier = Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ThemeRow(selected: ReadingTheme?, onSelect: (ReadingTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ReadingTheme.entries.forEach { theme ->
            val colors = ReadingPreferences(theme = theme).colors()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(colors.background))
                        .border(
                            width = if (theme == selected) 2.5.dp else 1.dp,
                            color = if (theme == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(theme) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Aa", color = Color(colors.text), fontFamily = FontFamily.Serif, fontSize = 15.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(theme.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val ReadingTheme.label: String
    get() = when (this) {
        ReadingTheme.LIGHT -> "Light"
        ReadingTheme.SEPIA -> "Sepia"
        ReadingTheme.DARK -> "Dark"
        ReadingTheme.BLACK -> "Black"
        ReadingTheme.CUSTOM -> "Custom"
    }

@Composable
private fun SwatchRow(label: String, swatches: List<Int>, selected: Int?, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { swatch ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(swatch))
                        .border(
                            width = if (swatch == selected) 2.5.dp else 1.dp,
                            color = if (swatch == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onSelect(swatch) },
                )
            }
        }
    }
}
