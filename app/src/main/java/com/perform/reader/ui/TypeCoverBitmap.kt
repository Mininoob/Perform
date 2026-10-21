package com.perform.reader.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.perform.reader.R
import com.perform.sync.CoverCloth

/**
 * The series' typeset cover drawn as a bitmap, for the surfaces that show a book's cover as
 * an image: Book Mode's cover and the outer cover screen. Same design as [TypeCover], so a
 * book without a cover wears the same jacket everywhere.
 */
object TypeCoverBitmap {

    fun render(context: Context, title: String, author: String?, genres: List<String>, width: Int = 1000): Bitmap {
        val height = width * 3 / 2
        val cloth = CoverCloth.of(genres, title)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(cloth.ground.toInt())

        val scale = width / 92f
        val pad = 10 * scale
        val ink = cloth.ink.toInt()
        val inkAt = { alpha: Float -> (ink and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24) }

        var y = pad
        author?.let {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkAt(0.85f)
                textSize = 6.5f * scale
                letterSpacing = 0.2f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val line = android.text.TextUtils.ellipsize(it.uppercase(), paint, width - 2 * pad, android.text.TextUtils.TruncateAt.END)
            y += paint.textSize
            canvas.drawText(line, 0, line.length, pad, y, paint)
            y += 5 * scale
        }
        canvas.drawRect(pad, y, width - pad, y + 0.75f * scale, Paint().apply { color = inkAt(0.6f) })

        // The title, as large as the longest word allows, set from the foot upward.
        val italic = ResourcesCompat.getFont(context, R.font.literata_italic) ?: Typeface.SERIF
        val longest = title.split(' ').maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val size = minOf(15f * scale, (width - 2 * pad) / (longest * 0.62f))
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = size
            typeface = Typeface.create(italic, Typeface.ITALIC)
        }
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (width - 2 * pad).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(6)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkAt(0.55f)
            textSize = 5.5f * scale
            letterSpacing = 0.36f
        }
        val markBaseline = height - pad
        canvas.drawText("PERFORM", pad, markBaseline, markPaint)

        canvas.save()
        canvas.translate(pad, markBaseline - markPaint.textSize - 8 * scale - layout.height)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}
