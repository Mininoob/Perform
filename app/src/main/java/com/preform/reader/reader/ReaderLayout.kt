package com.preform.reader.reader

import com.preform.reader.library.BookFormat
import com.preform.reader.settings.PdfFit
import com.preform.reader.settings.ReadingAlign
import com.preform.reader.settings.ReadingFont
import com.preform.reader.settings.ReadingMode
import com.preform.reader.settings.ReadingPreferences
import com.preform.reader.settings.ReadingTheme
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

/** Below this window width a spread's pages get too narrow to read, so it falls back to one page. */
const val SPREAD_MIN_WIDTH_DP = 600f

/** How the book is laid out right now: the reading mode, resolved against the device's shape. */
data class ReaderLayout(
    val scroll: Boolean,
    val spread: Boolean,
    val autoScroll: Boolean,
    /** Half-open like a laptop: page on the top half, controls on the bottom. */
    val tabletop: Boolean,
)

/**
 * Resolves the chosen [mode] against the format and the device.
 *
 * Spread only happens on a wide window (the Fold's inner screen), never on the cover
 * screen, and never for PDFs: Readium's free PDF engine has no spread support.
 * Tabletop is posture-driven rather than a menu choice.
 */
fun resolveLayout(
    mode: ReadingMode,
    format: BookFormat,
    windowWidthDp: Float,
    tabletop: Boolean,
): ReaderLayout {
    val canSpread = format == BookFormat.EPUB && windowWidthDp >= SPREAD_MIN_WIDTH_DP
    return when (mode) {
        ReadingMode.PAGES -> ReaderLayout(scroll = false, spread = false, autoScroll = false, tabletop = tabletop)
        ReadingMode.SPREAD -> ReaderLayout(scroll = false, spread = canSpread, autoScroll = false, tabletop = tabletop)
        ReadingMode.SCROLL -> ReaderLayout(scroll = true, spread = false, autoScroll = false, tabletop = tabletop)
        ReadingMode.AUTO_SCROLL -> ReaderLayout(scroll = true, spread = false, autoScroll = true, tabletop = tabletop)
    }
}

/** Page and text colors for a theme, ARGB; also used to tint the reader's own controls. */
data class ReadingColors(val background: Int, val text: Int)

fun ReadingPreferences.colors(): ReadingColors = when (theme ?: ReadingTheme.DARK) {
    ReadingTheme.LIGHT -> ReadingColors(0xFFFFFFFF.toInt(), 0xFF121212.toInt())
    ReadingTheme.SEPIA -> ReadingColors(0xFFFAF4E8.toInt(), 0xFF121212.toInt())
    ReadingTheme.DARK -> ReadingColors(0xFF12100E.toInt(), 0xFFDDD3C6.toInt())
    ReadingTheme.BLACK -> ReadingColors(0xFF000000.toInt(), 0xFFCFCFCF.toInt())
    ReadingTheme.CUSTOM -> ReadingColors(
        backgroundColor ?: 0xFF12100E.toInt(),
        textColor ?: 0xFFDDD3C6.toInt(),
    )
}

fun ReadingColors.isDark(): Boolean {
    val r = (background shr 16 and 0xFF) / 255.0
    val g = (background shr 8 and 0xFF) / 255.0
    val b = (background and 0xFF) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.5
}

/** The single boundary where our settings become Readium's EPUB preferences. */
fun ReadingPreferences.toEpubPreferences(layout: ReaderLayout): EpubPreferences {
    val colors = colors()
    // Readium only honours line height and alignment once the book's own styles are off.
    val overridesBookStyles = lineHeight != null || textAlign != null
    return EpubPreferences(
        theme = if (colors.isDark()) Theme.DARK else when (theme) {
            ReadingTheme.SEPIA -> Theme.SEPIA
            else -> Theme.LIGHT
        },
        backgroundColor = Color(colors.background),
        textColor = Color(colors.text),
        fontSize = fontSize,
        fontFamily = when (fontFamily) {
            ReadingFont.SERIF -> FontFamily.SERIF
            ReadingFont.SANS_SERIF -> FontFamily.SANS_SERIF
            ReadingFont.MONOSPACE -> FontFamily.MONOSPACE
            ReadingFont.ORIGINAL, null -> null
        },
        lineHeight = lineHeight,
        pageMargins = pageMargins,
        textAlign = when (textAlign) {
            ReadingAlign.START -> TextAlign.START
            ReadingAlign.JUSTIFY -> TextAlign.JUSTIFY
            null -> null
        },
        publisherStyles = !overridesBookStyles,
        scroll = layout.scroll,
        spread = if (layout.spread) Spread.ALWAYS else Spread.NEVER,
    )
}

/** PDFs: page-by-page sideways, or a continuous vertical scroll. */
fun ReadingPreferences.toPdfiumPreferences(layout: ReaderLayout): PdfiumPreferences = PdfiumPreferences(
    fit = if (pdfFit == PdfFit.PAGE) Fit.CONTAIN else Fit.WIDTH,
    pageSpacing = 8.0,
    scrollAxis = if (layout.scroll) Axis.VERTICAL else Axis.HORIZONTAL,
)
