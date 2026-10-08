package com.preform.desktop.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/**
 * The app's mark: a closed book in the accent color, drawn rather than shipped as a file.
 * [monochrome] draws it in one color for the macOS menu bar, which tints template icons.
 */
class BookMark(private val accent: Color, private val monochrome: Boolean = false) : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.08f)
        val pages = if (monochrome) accent.copy(alpha = 0.55f) else Color(0xFFE6E8EE)
        val spine = if (monochrome) accent else lerp(accent, Color.Black, 0.25f)
        // Pages peeking out on the right.
        drawRoundRect(pages, Offset(w * 0.22f, h * 0.10f), Size(w * 0.64f, h * 0.80f), r)
        // Cover.
        drawRoundRect(accent, Offset(w * 0.14f, h * 0.06f), Size(w * 0.64f, h * 0.84f), r)
        // Spine.
        drawRect(spine, Offset(w * 0.14f, h * 0.06f), Size(w * 0.10f, h * 0.84f))
        // Title band.
        if (!monochrome) drawRect(Color.Black.copy(alpha = 0.35f), Offset(w * 0.34f, h * 0.24f), Size(w * 0.32f, h * 0.06f))
    }

    companion object {
        /** The cloth and lettering of the series mark (claret, as on the Classics shelf). */
        val Cloth = Color(0xFF7A1F2B)
        val Lettering = Color(0xFFF1E6D4)
    }
}

/**
 * The mark as a book of the series: the same closed-book silhouette, bound in claret cloth
 * and set like a typeset cover (author line, hairline rule, two title lines at the foot).
 * The app icons and the Windows tray use it.
 */
class SeriesMark : Painter() {
    override val intrinsicSize = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.06f)
        val cloth = BookMark.Cloth
        val ink = BookMark.Lettering
        // Page edges, then the cover and its spine.
        drawRoundRect(Color(0xFFE9E1D2), Offset(w * 0.24f, h * 0.10f), Size(w * 0.62f, h * 0.80f), r)
        drawRoundRect(cloth, Offset(w * 0.16f, h * 0.06f), Size(w * 0.62f, h * 0.84f), r)
        drawRect(Color(0xFF5E1621), Offset(w * 0.16f, h * 0.06f), Size(w * 0.08f, h * 0.84f))
        // Author line, hairline rule, and the title set low, as on every typeset cover.
        val left = w * 0.31f
        val right = w * 0.71f
        drawRect(ink.copy(alpha = 0.75f), Offset(left, h * 0.15f), Size((right - left) * 0.55f, h * 0.022f))
        drawRect(ink.copy(alpha = 0.6f), Offset(left, h * 0.215f), Size(right - left, h * 0.012f))
        drawRect(ink, Offset(left, h * 0.62f), Size((right - left) * 0.9f, h * 0.05f))
        drawRect(ink, Offset(left, h * 0.70f), Size((right - left) * 0.6f, h * 0.05f))
    }
}

private fun lerp(a: Color, b: Color, t: Float) =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)
