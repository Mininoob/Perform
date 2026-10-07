package com.preform.reader.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ReadingMode { PAGES, SPREAD, SCROLL, AUTO_SCROLL }

enum class ReadingTheme { LIGHT, SEPIA, DARK, BLACK, CUSTOM }

enum class ReadingFont { ORIGINAL, SERIF, SANS_SERIF, MONOSPACE }

enum class ReadingAlign { START, JUSTIFY }

enum class PdfFit { WIDTH, PAGE }

/**
 * Every reading setting, all nullable, so the same type serves three roles: the
 * built-in [DEFAULTS], the user's global settings, and one book's overrides. A null
 * means "not set here, fall through to the layer below".
 *
 * Kept free of Readium types; the mapping to EpubPreferences / PdfiumPreferences
 * happens at one boundary in the reader package.
 */
@Serializable
data class ReadingPreferences(
    val mode: ReadingMode? = null,
    val theme: ReadingTheme? = null,
    /** Multiplier on the book's own size; 1.0 is 100%. */
    val fontSize: Double? = null,
    val fontFamily: ReadingFont? = null,
    /** ARGB; only used by [ReadingTheme.CUSTOM]. */
    val textColor: Int? = null,
    val backgroundColor: Int? = null,
    /** Line height multiplier; null keeps the book's own spacing. */
    val lineHeight: Double? = null,
    /** Readium margin factor; 1.0 is its default margin. */
    val pageMargins: Double? = null,
    /** Null keeps the book's own alignment. */
    val textAlign: ReadingAlign? = null,
    val pdfFit: PdfFit? = null,
    /** Auto-scroll speed in CSS pixels per second. */
    val autoScrollSpeed: Double? = null,
) {
    /** This layer with [override]'s non-null fields laid on top. */
    infix fun overriddenBy(override: ReadingPreferences): ReadingPreferences = ReadingPreferences(
        mode = override.mode ?: mode,
        theme = override.theme ?: theme,
        fontSize = override.fontSize ?: fontSize,
        fontFamily = override.fontFamily ?: fontFamily,
        textColor = override.textColor ?: textColor,
        backgroundColor = override.backgroundColor ?: backgroundColor,
        lineHeight = override.lineHeight ?: lineHeight,
        pageMargins = override.pageMargins ?: pageMargins,
        textAlign = override.textAlign ?: textAlign,
        pdfFit = override.pdfFit ?: pdfFit,
        autoScrollSpeed = override.autoScrollSpeed ?: autoScrollSpeed,
    )

    val isEmpty: Boolean get() = this == ReadingPreferences()

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /**
         * What a reader sees before changing anything. Spread, so the open Fold reads
         * as a book; the warm dark theme the app has used from the start.
         */
        val DEFAULTS = ReadingPreferences(
            mode = ReadingMode.SPREAD,
            theme = ReadingTheme.DARK,
            fontSize = 1.0,
            fontFamily = ReadingFont.ORIGINAL,
            textColor = 0xFFDDD3C6.toInt(),
            backgroundColor = 0xFF12100E.toInt(),
            pageMargins = 1.0,
            pdfFit = PdfFit.WIDTH,
            autoScrollSpeed = 40.0,
        )

        /** Effective settings for one book: defaults, then global, then the book's own. */
        fun resolve(global: ReadingPreferences, book: ReadingPreferences): ReadingPreferences =
            DEFAULTS overriddenBy global overriddenBy book

        /** Lenient: a corrupt or outdated blob reads as "nothing set" rather than crashing. */
        fun fromJson(text: String?): ReadingPreferences =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
                ?: ReadingPreferences()

        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** Swatches for the Custom theme; deliberately a short curated list, not a color picker. */
object ReadingSwatches {
    val backgrounds = listOf(
        0xFF12100E, 0xFF000000, 0xFF1B2421, 0xFF1C1F2A, 0xFFF7F3EE, 0xFFF4ECD8, 0xFFE8EFE6, 0xFFE9ECF2,
    ).map { it.toInt() }
    val texts = listOf(
        0xFFDDD3C6, 0xFFD6D6D6, 0xFFB8C9B0, 0xFFC5CBE0, 0xFF1E1A16, 0xFF3B2F25, 0xFF233024, 0xFF1F2433,
    ).map { it.toInt() }
}
