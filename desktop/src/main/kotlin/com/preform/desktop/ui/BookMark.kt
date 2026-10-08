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

    private fun lerp(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha)
}
