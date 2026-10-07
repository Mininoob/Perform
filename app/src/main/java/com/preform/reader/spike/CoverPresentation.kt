package com.preform.reader.spike

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Paints the cover onto the Flex Window.
 *
 * Presentation is the documented way to put our own content on a secondary display,
 * and the Flip's cover screen advertises FLAG_PRESENTATION - so this is the path to
 * Book Mode that does not depend on the user allowlisting us in Good Lock.
 *
 * Deliberately a plain View rather than Compose: a Presentation has no ViewTree
 * lifecycle owners wired up by default, and for a static cover the plumbing buys
 * us nothing.
 */
class CoverPresentation(
    outerContext: Context,
    display: Display,
    private val title: String,
) : Presentation(outerContext, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            setBackgroundDrawableResource(android.R.color.black)
        }
        setContentView(CoverView(context, title))
    }
}

/**
 * The cover, drawn with the same type metrics as the Compose cover the activity paints
 * when the lid is shut (26sp light serif on 30sp lines), so the outside of an open
 * device and the outside of a shut one look identical.
 *
 * Sized in sp, never as a fraction of the screen: an earlier version scaled text by
 * height, which suited the Flip's near-square cover and blew "COMPLETE" off both edges
 * of the Fold's tall, narrow one.
 */
internal class CoverView(
    context: Context,
    private val title: String,
    /**
     * Back-of-phone page turning. Receives the swipe's horizontal travel in this
     * view's own coordinates and returns whether a page actually turned. Null leaves
     * the cover inert to touch.
     */
    private val onSwipe: ((dxPx: Float) -> Boolean)? = null,
) : View(context) {

    // The back of the phone is where the hand grips it, so this only accepts a
    // deliberate one-finger horizontal stroke and ignores everything else.
    private val minSwipePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64f, resources.displayMetrics)
    private val palmPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 25f, resources.displayMetrics)
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var tracking = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handler = onSwipe ?: return super.onTouchEvent(event)
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
                val quick = event.eventTime - downTime <= MAX_SWIPE_MS
                val horizontal = abs(dx) >= minSwipePx && abs(dx) > abs(dy) * 1.5f
                if (tracking && quick && horizontal && handler(dx)) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                tracking = false
            }
        }
        return true
    }

    private val lines = title.split("\n")
    private val backdrop = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F3E4D2")
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SERIF, 300, false)
        } else {
            Typeface.SERIF
        }
        textAlign = Paint.Align.CENTER
    }
    private var lineHeight = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backdrop.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(0xFF2B1B12.toInt(), 0xFF7A4A2B.toInt(), 0xFF3D2519.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )

        val metrics = resources.displayMetrics
        text.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 26f, metrics)

        // Only ever shrink, and only if the longest line would crowd the edges.
        val longest = lines.maxOf { text.measureText(it) }
        val usable = w - 2 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, metrics)
        if (longest > usable) text.textSize *= usable / longest

        lineHeight = text.textSize * (30f / 26f)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, backdrop)

        // Hairline spine along the top edge, 3dp to match the Compose cover.
        val spineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
        canvas.drawRect(0f, 0f, w, spineHeight, spine)

        // Centre the block of lines, placing each baseline in the middle of its line box.
        val fm = text.fontMetrics
        val blockHeight = lines.size * lineHeight
        var baseline = (h - blockHeight) / 2f + (lineHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
        lines.forEach { line ->
            canvas.drawText(line, w / 2f, baseline, text)
            baseline += lineHeight
        }
    }
}

/** A swipe slower than this is treated as a drag or a grip adjustment, not a page turn. */
private const val MAX_SWIPE_MS = 600L
