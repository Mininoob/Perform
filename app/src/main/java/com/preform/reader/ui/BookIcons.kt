package com.preform.reader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Book Mode's toggle: a closed book, outlined when off and filled with a ribbon when on. */
object BookIcons {

    val Off: ImageVector by lazy {
        ImageVector.Builder("BookModeOff", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Cover.
                moveTo(7f, 3f)
                horizontalLineTo(18f)
                arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 4.5f)
                verticalLineTo(19.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 18f, 21f)
                horizontalLineTo(7f)
                arcTo(2.5f, 2.5f, 0f, false, true, 4.5f, 18.5f)
                verticalLineTo(5.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 7f, 3f)
                close()
                // Spine.
                moveTo(8f, 3f)
                verticalLineTo(21f)
            }
        }.build()
    }

    val On: ImageVector by lazy {
        ImageVector.Builder("BookModeOn", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // Cover, with the ribbon cut out of it.
                moveTo(7f, 3f)
                horizontalLineTo(18f)
                arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 4.5f)
                verticalLineTo(19.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 18f, 21f)
                horizontalLineTo(7f)
                arcTo(2.5f, 2.5f, 0f, false, true, 4.5f, 18.5f)
                verticalLineTo(5.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 7f, 3f)
                close()
                // Ribbon.
                moveTo(12.5f, 3f)
                verticalLineTo(10.5f)
                lineTo(14.25f, 9f)
                lineTo(16f, 10.5f)
                verticalLineTo(3f)
                close()
            }
        }.build()
    }
}

/** Marks a book that lives on the PC and downloads when opened. */
object CloudIcon {
    val Outline: ImageVector by lazy {
        ImageVector.Builder("Cloud", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 18f)
                arcTo(4f, 4f, 0f, false, true, 6.5f, 10.05f)
                arcTo(5.5f, 5.5f, 0f, false, true, 17.2f, 9f)
                arcTo(4.5f, 4.5f, 0f, false, true, 17.5f, 18f)
                close()
            }
        }.build()
    }
}
