package com.preform.reader.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.preform.reader.reader.ReadingColors
import com.preform.reader.settings.AppTheme
import com.preform.reader.settings.ThemeAccent
import com.preform.reader.settings.ThemeBase

/** The handful of colors a theme is designed from; everything else derives from them. */
data class ThemePalette(
    val dark: Boolean,
    /** The page itself. */
    val background: Color,
    /** Cards and sheets, a step up from the page. */
    val raised: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    /** Text drawn on the accent color. */
    val onAccent: Color,
)

/** A base's neutrals: page, raised surface, text, muted text. */
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

/**
 * An accent's two shades: a deeper one that reads on light bases and a lighter one
 * that reads on dark bases, each with the text color that sits on it.
 */
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

/** This accent as it would look on [base]: what the accent picker shows. */
fun ThemeAccent.colorOn(base: ThemeBase): Color = shade(base.dark).first

val AppTheme.palette: ThemePalette
    get() {
        val n = base.neutrals
        val (accentColor, onAccentColor) = accent.shade(base.dark)
        return ThemePalette(base.dark, n.background, n.raised, n.text, n.muted, accentColor, onAccentColor)
    }

val ThemeBase.label: String
    get() = when (this) {
        ThemeBase.MONO -> "Mono"
        ThemeBase.PAPER -> "Paper"
        ThemeBase.PARCHMENT -> "Parchment"
        ThemeBase.NOIR -> "Noir"
        ThemeBase.INK -> "Ink"
        ThemeBase.SLATE -> "Slate"
    }

val ThemeAccent.label: String
    get() = when (this) {
        ThemeAccent.MONO -> "Mono"
        ThemeAccent.RED -> "Red"
        ThemeAccent.PINK -> "Pink"
        ThemeAccent.YELLOW -> "Yellow"
        ThemeAccent.GREEN -> "Green"
        ThemeAccent.TEAL -> "Teal"
        ThemeAccent.BLUE -> "Blue"
        ThemeAccent.INDIGO -> "Indigo"
        ThemeAccent.VIOLET -> "Violet"
    }

/** The page and text colors a book borrows when its reading theme is "App theme". */
fun ThemePalette.readingColors() = ReadingColors(background.toArgb(), text.toArgb())

private fun ThemePalette.toColorScheme(): ColorScheme {
    val outline = text.copy(alpha = 0.32f).compositeOver(background)
    val outlineVariant = text.copy(alpha = 0.14f).compositeOver(background)
    val raisedHigher = text.copy(alpha = 0.06f).compositeOver(raised)
    val container = accent.copy(alpha = if (dark) 0.22f else 0.16f).compositeOver(raised)
    return if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = muted, onSecondary = background,
            background = background, onBackground = text,
            surface = background, onSurface = text,
            surfaceVariant = raised, onSurfaceVariant = muted,
            surfaceContainerLowest = background, surfaceContainerLow = raised,
            surfaceContainer = raised, surfaceContainerHigh = raisedHigher,
            surfaceContainerHighest = raisedHigher,
            secondaryContainer = container, onSecondaryContainer = text,
            outline = outline, outlineVariant = outlineVariant,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = onAccent,
            secondary = muted, onSecondary = background,
            background = background, onBackground = text,
            surface = background, onSurface = text,
            surfaceVariant = raised, onSurfaceVariant = muted,
            surfaceContainerLowest = background, surfaceContainerLow = raised,
            surfaceContainer = raised, surfaceContainerHigh = raisedHigher,
            surfaceContainerHighest = raisedHigher,
            secondaryContainer = container, onSecondaryContainer = text,
            outline = outline, outlineVariant = outlineVariant,
        )
    }
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
    )
}

@Composable
fun PreformTheme(theme: AppTheme, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = theme.palette.toColorScheme(), typography = preformTypography(), content = content)
}
