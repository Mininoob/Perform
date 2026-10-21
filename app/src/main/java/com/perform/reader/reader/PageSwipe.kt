package com.perform.reader.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Turns a quick, clearly horizontal one-finger swipe into a single page turn.
 *
 * Watches without consuming, so the view underneath still receives every touch: taps
 * keep reaching Readium's edge tap zones. Used for locked PDF pages, where the PDF view
 * itself no longer moves under a drag.
 *
 * [onSwipe] gets the horizontal travel: negative is right-to-left.
 */
fun Modifier.pageSwipes(enabled: Boolean, onSwipe: (dx: Float) -> Unit): Modifier =
    if (!enabled) this else pointerInput(onSwipe) {
        val minDistance = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var last = down.position
            var multiTouch = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) multiTouch = true
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                last = change.position
                if (!change.pressed) {
                    val dx = last.x - down.position.x
                    val dy = last.y - down.position.y
                    val quick = change.uptimeMillis - down.uptimeMillis <= MAX_SWIPE_MS
                    if (!multiTouch && quick && abs(dx) >= minDistance && abs(dx) > abs(dy) * 1.5f) onSwipe(dx)
                    break
                }
            }
        }
    }

/** Slower than this reads as a drag or a hand settling, not a page turn. */
private const val MAX_SWIPE_MS = 700L
