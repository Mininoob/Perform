package com.preform.desktop.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Literata, the series' voice, the same face the phone uses: book titles, shelf and page
 * titles, typeset covers. Controls and data stay in the platform sans (Segoe UI, SF).
 */
@OptIn(ExperimentalTextApi::class)
private fun literata(weight: Int, italic: Boolean, opticalSize: Float) = Font(
    resource = if (italic) "fonts/Literata-Italic.ttf" else "fonts/Literata.ttf",
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Text sizes: titles under covers, descriptions. */
val Literata = FontFamily(
    literata(400, italic = false, opticalSize = 12f),
    literata(500, italic = false, opticalSize = 12f),
    literata(400, italic = true, opticalSize = 12f),
)

/** Display sizes: page titles, the book in the details panel, dialogs' titles. */
val LiterataDisplay = FontFamily(
    literata(400, italic = false, opticalSize = 36f),
    literata(500, italic = false, opticalSize = 36f),
    literata(400, italic = true, opticalSize = 36f),
)
