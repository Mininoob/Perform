package com.perform.reader.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.perform.reader.R

/**
 * Literata, the series' voice: book titles, shelf names, page titles and typeset covers.
 * Controls, labels and data stay in the platform sans. Both cuts are variable: weight,
 * and optical size so large titles get the tighter display drawing.
 */
@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int, italic: Boolean, opticalSize: Float) = Font(
    resId = if (italic) R.font.literata_italic else R.font.literata,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Text sizes: titles under a cover, a book's description, the reading-font sample. */
val Literata = FontFamily(
    literata(400, italic = false, opticalSize = 12f),
    literata(500, italic = false, opticalSize = 12f),
    literata(400, italic = true, opticalSize = 12f),
)

/** Display sizes: the page title, the book in hand, shelf names. */
val LiterataDisplay = FontFamily(
    literata(400, italic = false, opticalSize = 36f),
    literata(500, italic = false, opticalSize = 36f),
    literata(400, italic = true, opticalSize = 36f),
)

/** Material's type scale with the series' voice in the display, headline and title roles. */
fun performTypography(): Typography {
    val base = Typography()
    fun TextStyle.serif(family: FontFamily, tracking: Float = 0f) =
        copy(fontFamily = family, fontWeight = FontWeight.Normal, letterSpacing = tracking.sp)
    return base.copy(
        displayLarge = base.displayLarge.serif(LiterataDisplay, -0.5f),
        displayMedium = base.displayMedium.serif(LiterataDisplay, -0.4f),
        displaySmall = base.displaySmall.serif(LiterataDisplay, -0.3f),
        headlineLarge = base.headlineLarge.serif(LiterataDisplay, -0.3f),
        headlineMedium = base.headlineMedium.serif(LiterataDisplay, -0.2f),
        headlineSmall = base.headlineSmall.serif(LiterataDisplay),
        titleLarge = base.titleLarge.serif(LiterataDisplay),
        titleMedium = base.titleMedium.serif(Literata),
    )
}
