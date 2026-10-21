package com.perform.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.perform.desktop.ThemeAccent
import com.perform.desktop.ThemeBase

/**
 * The organizer's colors, the same values the phone uses (app/.../ui/Theme.kt), so the
 * two look like one product. Keep them in step when the phone's change.
 */
data class Palette(
    val dark: Boolean,
    val background: Color,
    /** Cards, the sidebar and panels: a step up from the page. */
    val raised: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
) {
    /** Hairlines and dividers. */
    val line: Color get() = text.copy(alpha = if (dark) 0.10f else 0.12f)

    /** A selected row or card. */
    val selected: Color get() = accent.copy(alpha = if (dark) 0.18f else 0.14f).compositeOver(raised)

    val danger: Color get() = if (dark) Color(0xFFEF6B73) else Color(0xFFC62F3A)
    val ok: Color get() = if (dark) Color(0xFF8FD18A) else Color(0xFF2F7D3A)
}

private data class Neutrals(val background: Color, val raised: Color, val text: Color, val muted: Color)

private val ThemeBase.neutrals: Neutrals
    get() = when (this) {
        ThemeBase.MONO -> Neutrals(Color(0xFFFFFFFF), Color(0xFFF1F1F1), Color(0xFF000000), Color(0xFF5E5E5E))
        ThemeBase.PAPER -> Neutrals(Color(0xFFF7F5F0), Color(0xFFECE8E0), Color(0xFF1D1F24), Color(0xFF6A6E76))
        ThemeBase.PARCHMENT -> Neutrals(Color(0xFFF3E9D2), Color(0xFFE9DCBD), Color(0xFF3B2F25), Color(0xFF7A6650))
        ThemeBase.NOIR -> Neutrals(Color(0xFF000000), Color(0xFF121212), Color(0xFFFFFFFF), Color(0xFF8C8C8C))
        ThemeBase.INK -> Neutrals(Color(0xFF131416), Color(0xFF1C1E21), Color(0xFFE4E2DD), Color(0xFF93979E))
        ThemeBase.SLATE -> Neutrals(Color(0xFF1A1E24), Color(0xFF232830), Color(0xFFE1E5EA), Color(0xFF8B949E))
    }

/** An accent's shade for dark or light bases, with the text color that sits on it. */
private fun ThemeAccent.shade(dark: Boolean): Pair<Color, Color> = if (dark) {
    when (this) {
        ThemeAccent.MONO -> Color(0xFFFFFFFF) to Color(0xFF000000)
        ThemeAccent.RED -> Color(0xFFEF6B73) to Color(0xFF2B0508)
        ThemeAccent.PINK -> Color(0xFFF28AB4) to Color(0xFF33081B)
        ThemeAccent.YELLOW -> Color(0xFFF2D263) to Color(0xFF2A2200)
        ThemeAccent.GREEN -> Color(0xFF8FD18A) to Color(0xFF0B2209)
        ThemeAccent.TEAL -> Color(0xFF5FD0C8) to Color(0xFF022523)
        ThemeAccent.BLUE -> Color(0xFF8AB4F8) to Color(0xFF0A1A33)
        ThemeAccent.INDIGO -> Color(0xFFA3A6FF) to Color(0xFF11124A)
        ThemeAccent.VIOLET -> Color(0xFFD29BF0) to Color(0xFF2A0B3A)
    }
} else {
    when (this) {
        ThemeAccent.MONO -> Color(0xFF000000) to Color(0xFFFFFFFF)
        ThemeAccent.RED -> Color(0xFFC62F3A) to Color(0xFFFFFFFF)
        ThemeAccent.PINK -> Color(0xFFB83C72) to Color(0xFFFFFFFF)
        ThemeAccent.YELLOW -> Color(0xFF8F7100) to Color(0xFFFFFFFF)
        ThemeAccent.GREEN -> Color(0xFF2F7D3A) to Color(0xFFFFFFFF)
        ThemeAccent.TEAL -> Color(0xFF12786F) to Color(0xFFFFFFFF)
        ThemeAccent.BLUE -> Color(0xFF2457C5) to Color(0xFFFFFFFF)
        ThemeAccent.INDIGO -> Color(0xFF4B4FC4) to Color(0xFFFFFFFF)
        ThemeAccent.VIOLET -> Color(0xFF7A3FC0) to Color(0xFFFFFFFF)
    }
}

fun ThemeAccent.colorOn(base: ThemeBase): Color = shade(base.dark).first

fun palette(base: ThemeBase, accent: ThemeAccent): Palette {
    val n = base.neutrals
    val (a, onA) = accent.shade(base.dark)
    return Palette(base.dark, n.background, n.raised, n.text, n.muted, a, onA)
}

val ThemeBase.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)
val ThemeAccent.label: String get() = name.lowercase().replaceFirstChar(Char::uppercase)

val LocalPalette = staticCompositionLocalOf { palette(ThemeBase.INK, ThemeAccent.BLUE) }

/** The current palette, for colors Material's scheme doesn't name. */
val colors: Palette @Composable get() = LocalPalette.current

/**
 * Secondary text actions (Cancel, Find online, Organize…): the muted ink. The accent is kept
 * for the way forward; give an action the accent by setting its text colour explicitly.
 */
@Composable
fun quietButton() = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = colors.text)

@Composable
fun PerformTheme(base: ThemeBase, accent: ThemeAccent, content: @Composable () -> Unit) {
    val p = palette(base, accent)
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text,
            surfaceVariant = p.raised, onSurfaceVariant = p.muted,
            surfaceContainer = p.raised, surfaceContainerHigh = p.raised, surfaceContainerHighest = p.raised,
            surfaceContainerLow = p.raised, outline = p.muted, outlineVariant = p.line,
            secondaryContainer = p.selected, onSecondaryContainer = p.text, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.background, onBackground = p.text,
            surface = p.background, onSurface = p.text,
            surfaceVariant = p.raised, onSurfaceVariant = p.muted,
            surfaceContainer = p.raised, surfaceContainerHigh = p.raised, surfaceContainerHighest = p.raised,
            surfaceContainerLow = p.raised, outline = p.muted, outlineVariant = p.line,
            secondaryContainer = p.selected, onSecondaryContainer = p.text, error = p.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
