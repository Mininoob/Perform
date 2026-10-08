package com.preform.reader.foldable

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The book's cover for the outer screen, drawn as a plain View because it is handed to
 * the dual-screen session (`WindowAreaSessionPresenter.setContentView`), which has no
 * Compose lifecycle owners.
 *
 * Covers are about 2:3 and the Fold's outer panel about 3:7, so center-cropping would
 * throw most of the art away. Instead the cover is fitted and centered over a blurred,
 * darkened copy of itself. With no cover, it falls back to a title card.
 */
internal class CoverView(
    context: Context,
    private val cover: Bitmap?,
    private val title: String,
    /**
     * Back-of-phone page turning. Receives the swipe's horizontal travel in this
     * view's own coordinates and returns whether a page actually turned. Null leaves
     * the cover inert to touch.
     */
    private val onSwipe: ((dxPx: Float) -> Boolean)? = null,
    /**
     * Back-of-phone tapping. Receives where the tap landed across this view, 0..1 in
     * its own coordinates, and returns whether a page turned. Null ignores taps.
     */
    private val onTap: ((xFraction: Float) -> Boolean)? = null,
) : View(context) {

    private val dp = resources.displayMetrics.density
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadePaint = Paint().apply { color = 0x8C000000.toInt() }
    private val spinePaint = Paint().apply { color = 0x66000000 }
    private val cardShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
        setShadowLayer(18 * dp, 0f, 6 * dp, 0xCC000000.toInt())
    }
    private val gradientPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F3E4D2")
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SERIF, 300, false)
        } else {
            Typeface.SERIF
        }
        textAlign = Paint.Align.CENTER
    }

    /** A tiny copy of the cover; scaled back up with filtering it reads as a soft blur. */
    private val blurSource: Bitmap? = cover?.let {
        val w = max(1, it.width / 24)
        val h = max(1, it.height / 24)
        it.scale(w, h, filter = true)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF2B1B12.toInt(), 0xFF7A4A2B.toInt(), 0xFF3D2519.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (cover == null || blurSource == null) {
            drawTitleCard(canvas, w, h)
        } else {
            canvas.drawBitmap(blurSource, null, cropRect(blurSource, w, h), imagePaint)
            canvas.drawRect(0f, 0f, w, h, shadePaint)

            val fitted = fitRect(cover, w - 2 * MARGIN_DP * dp, h - 2 * MARGIN_DP * dp)
            canvas.drawRect(fitted, cardShadow)
            canvas.drawBitmap(cover, null, fitted, imagePaint)
        }

        // Hairline spine along the top edge, 3dp to match the Compose cover.
        canvas.drawRect(0f, 0f, w, 3 * dp, spinePaint)
    }

    private fun drawTitleCard(canvas: Canvas, w: Float, h: Float) {
        canvas.drawRect(0f, 0f, w, h, gradientPaint)
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26f, resources.displayMetrics)
        val lines = wrap(title.uppercase(), w - 48 * dp)
        val lineHeight = textPaint.textSize * (30f / 26f)
        val fm = textPaint.fontMetrics
        var baseline = (h - lines.size * lineHeight) / 2f + (lineHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
        lines.forEach {
            canvas.drawText(it, w / 2f, baseline, textPaint)
            baseline += lineHeight
        }
    }

    /** Greedy word wrap so long titles stay inside the card. */
    private fun wrap(text: String, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (textPaint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = candidate
            } else {
                lines += line
                line = word
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /** Fills [w]x[h] completely, cropping the overflow. */
    private fun cropRect(bitmap: Bitmap, w: Float, h: Float): RectF {
        val scale = max(w / bitmap.width, h / bitmap.height)
        val bw = bitmap.width * scale
        val bh = bitmap.height * scale
        return RectF((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f)
    }

    /** Fits inside [maxW]x[maxH], centered in the view. */
    private fun fitRect(bitmap: Bitmap, maxW: Float, maxH: Float): RectF {
        val scale = min(maxW / bitmap.width, maxH / bitmap.height)
        val bw = bitmap.width * scale
        val bh = bitmap.height * scale
        val left = (width - bw) / 2f
        val top = (height - bh) / 2f
        return RectF(left, top, left + bw, top + bh)
    }

    // The back of the phone is where the hand grips it, so this only accepts a
    // deliberate one-finger horizontal stroke and ignores everything else.
    private val minSwipePx = 64 * dp
    private val palmPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 25f, resources.displayMetrics)
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var tracking = false

    private val tapSlopPx = 12 * dp

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onSwipe == null && onTap == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
                tracking = event.touchMajor < palmPx
            }
            // A second finger means a grip, not a swipe.
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> tracking = false
            MotionEvent.ACTION_MOVE -> if (event.touchMajor >= palmPx) tracking = false
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val elapsed = event.eventTime - downTime
                val swipe = elapsed <= MAX_SWIPE_MS && abs(dx) >= minSwipePx && abs(dx) > abs(dy) * 1.5f
                val tap = elapsed <= MAX_TAP_MS && abs(dx) < tapSlopPx && abs(dy) < tapSlopPx
                val turned = when {
                    !tracking -> false
                    swipe -> onSwipe?.invoke(dx) == true
                    tap -> onTap?.invoke(event.x / width) == true
                    else -> false
                }
                if (turned) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                tracking = false
            }
        }
        return true
    }

    private companion object {
        /** Breathing room around the fitted cover, in dp. */
        const val MARGIN_DP = 28f

        /** A swipe slower than this is treated as a drag or a grip adjustment, not a page turn. */
        const val MAX_SWIPE_MS = 600L

        /** A touch held longer than this is the hand settling, not a tap. */
        const val MAX_TAP_MS = 300L
    }
}
