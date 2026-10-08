package com.preform.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Line icons in the phone's style (1.8 stroke, round joins) for what the core set lacks. */
object Glyphs {

    private fun line(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()

    val Folder by lazy {
        line("Folder") {
            moveTo(3.5f, 7f)
            arcTo(1.5f, 1.5f, 0f, false, true, 5f, 5.5f)
            horizontalLineTo(9.5f)
            lineTo(11.5f, 7.5f)
            horizontalLineTo(19f)
            arcTo(1.5f, 1.5f, 0f, false, true, 20.5f, 9f)
            verticalLineTo(17.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 19f, 19f)
            horizontalLineTo(5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 3.5f, 17.5f)
            close()
        }
    }

    val Books by lazy {
        line("Books") {
            // Two spines and a leaning third.
            moveTo(5f, 4.5f); verticalLineTo(19.5f); horizontalLineTo(8.5f); verticalLineTo(4.5f); close()
            moveTo(10.5f, 6.5f); verticalLineTo(19.5f); horizontalLineTo(14f); verticalLineTo(6.5f); close()
            moveTo(15.6f, 7.4f); lineTo(19.2f, 6.6f); lineTo(21f, 19f); lineTo(17.4f, 19.8f); close()
        }
    }

    val Recent by lazy {
        line("Recent") {
            moveTo(12f, 3.5f)
            arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
            moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15f, 14f)
        }
    }

    val Tag by lazy {
        line("Tag") {
            moveTo(3.5f, 4.5f); verticalLineTo(11f); lineTo(12.5f, 20f); lineTo(20f, 12.5f); lineTo(11f, 3.5f)
            horizontalLineTo(4.5f)
            arcTo(1f, 1f, 0f, false, false, 3.5f, 4.5f)
            close()
            moveTo(8f, 7.5f); lineTo(8.01f, 7.5f)
        }
    }

    val Sparkle by lazy {
        line("Sparkle") {
            moveTo(12f, 3f); lineTo(13.8f, 10.2f); lineTo(21f, 12f); lineTo(13.8f, 13.8f)
            lineTo(12f, 21f); lineTo(10.2f, 13.8f); lineTo(3f, 12f); lineTo(10.2f, 10.2f); close()
        }
    }

    val Check by lazy {
        line("Check") {
            moveTo(12f, 3f); lineTo(19.5f, 6f); verticalLineTo(11.5f)
            curveTo(19.5f, 16f, 16.3f, 19.6f, 12f, 21f)
            curveTo(7.7f, 19.6f, 4.5f, 16f, 4.5f, 11.5f)
            verticalLineTo(6f); close()
            moveTo(8.8f, 12f); lineTo(11f, 14.2f); lineTo(15.4f, 9.8f)
        }
    }

    val Phone by lazy {
        line("Phone") {
            moveTo(7.5f, 2.5f); horizontalLineTo(16.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 18f, 4f)
            verticalLineTo(20f)
            arcTo(1.5f, 1.5f, 0f, false, true, 16.5f, 21.5f)
            horizontalLineTo(7.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 6f, 20f)
            verticalLineTo(4f)
            arcTo(1.5f, 1.5f, 0f, false, true, 7.5f, 2.5f)
            close()
            moveTo(11f, 18.5f); horizontalLineTo(13f)
        }
    }

    val Search by lazy {
        line("Search") {
            moveTo(10.5f, 4f)
            arcTo(6.5f, 6.5f, 0f, true, true, 10.49f, 4f)
            moveTo(15.2f, 15.2f); lineTo(20f, 20f)
        }
    }

    val Close by lazy {
        line("Close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
    }

    val Refresh by lazy {
        line("Refresh") {
            moveTo(19.5f, 12f)
            arcTo(7.5f, 7.5f, 0f, true, true, 17.3f, 6.7f)
            moveTo(19.5f, 3.5f); verticalLineTo(7.5f); horizontalLineTo(15.5f)
        }
    }

    val Gear by lazy {
        line("Gear") {
            moveTo(12f, 9f)
            arcTo(3f, 3f, 0f, true, true, 11.99f, 9f)
            // Eight short teeth around a ring.
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(a).toFloat()
                val s = kotlin.math.sin(a).toFloat()
                moveTo(12f + 6f * c, 12f + 6f * s); lineTo(12f + 8.5f * c, 12f + 8.5f * s)
            }
            moveTo(12f, 6f)
            arcTo(6f, 6f, 0f, true, true, 11.99f, 6f)
        }
    }

    /** A plain hairline tick: "in yours". */
    val Tick by lazy { line("Tick") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }

    val ChevronRight by lazy { line("ChevronRight") { moveTo(9.5f, 6f); lineTo(15.5f, 12f); lineTo(9.5f, 18f) } }

    val ChevronDown by lazy { line("ChevronDown") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) } }

    val Reveal by lazy {
        line("Reveal") {
            moveTo(14f, 4f); horizontalLineTo(20f); verticalLineTo(10f)
            moveTo(20f, 4f); lineTo(11f, 13f)
            moveTo(18f, 14f); verticalLineTo(19f)
            arcTo(1f, 1f, 0f, false, true, 17f, 20f)
            horizontalLineTo(5f)
            arcTo(1f, 1f, 0f, false, true, 4f, 19f)
            verticalLineTo(7f)
            arcTo(1f, 1f, 0f, false, true, 5f, 6f)
            horizontalLineTo(10f)
        }
    }
}
