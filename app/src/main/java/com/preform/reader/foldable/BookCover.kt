package com.preform.reader.foldable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * The shut phone's cover, hinged on its spine.
 *
 * [openness] runs from 0 (cover shut over the page) to 1 (swung fully open): the cover
 * rotates about the spine edge toward the reader, uncovering the real page underneath.
 * The spine is on the left for a left-to-right book and on the right for a
 * right-to-left one, as on paper.
 *
 * The cover art is fitted and centered over a blurred, darkened copy of itself (the
 * outer panel is far narrower than a book); with no cover, a title card.
 */
@Composable
fun BookCover(
    cover: ImageBitmap?,
    title: String,
    rightToLeft: Boolean,
    openness: Float,
    showHint: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (rightToLeft) 1f else 0f, 0.5f)
                rotationY = (if (rightToLeft) 1f else -1f) * 90f * openness
                cameraDistance = 14f * density
                // Edge-on near the end; fade the last stretch so it doesn't flicker as a sliver.
                alpha = ((1f - openness) / 0.2f).coerceIn(0f, 1f)
            }
            .background(Color.Black),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x8C000000)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = cover,
                    contentDescription = title,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .aspectRatio(cover.width / cover.height.toFloat())
                        .shadow(18.dp),
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2B1B12), Color(0xFF7A4A2B), Color(0xFF3D2519)))),
            )
            Text(
                text = title.uppercase(),
                color = Color(0xFFF3E4D2),
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            )
        }
        // Hairline spine along the top edge.
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x66000000))
                .align(Alignment.TopCenter),
        )
        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
        ) {
            Text(
                text = "Swipe ${if (rightToLeft) "right" else "left"} or hold to open",
                color = Color(0xCCF3E4D2),
                fontSize = 13.sp,
                modifier = Modifier
                    .background(Color(0x66000000), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * One gesture vocabulary for the cover and for a book opened on the cover screen:
 * tap, long press, a quick flick, and a slow drag that the cover follows.
 *
 * [followDirection] is the sign of horizontal travel that moves the cover (-1 leftward,
 * +1 rightward). With [followAtOnce] any drag that way follows the finger (opening the
 * cover); without it, only a drag still going after [SLOW_DRAG_MS] does, so quick
 * swipes stay free to turn pages (closing from an open book).
 *
 * The overlay owns its touches: everything is consumed.
 */
fun Modifier.bookGestures(
    followDirection: Int,
    followAtOnce: Boolean,
    onTap: (position: Offset, size: IntSize) -> Unit,
    onLongPress: () -> Unit,
    onQuickSwipe: (dx: Float) -> Unit,
    onFollow: (fraction: Float) -> Unit,
    onFollowEnd: (fraction: Float, velocityPxPerSecond: Float) -> Unit,
): Modifier = pointerInput(followDirection, followAtOnce) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    val minSwipe = 48.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }

        // Until the finger moves past the slop: a tap, a long press, or the start of a drag.
        var outcome = Outcome.LONG_PRESS
        withTimeoutOrNull(longPressMs) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    outcome = Outcome.CANCELLED
                    return@withTimeoutOrNull
                }
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    outcome = Outcome.TAP
                    return@withTimeoutOrNull
                }
                if ((change.position - down.position).getDistance() > slop) {
                    outcome = Outcome.DRAG
                    return@withTimeoutOrNull
                }
            }
        }

        when (outcome) {
            Outcome.TAP -> {
                onTap(down.position, size)
                return@awaitEachGesture
            }
            Outcome.LONG_PRESS -> {
                onLongPress()
                // Swallow the rest of this touch so lifting the finger isn't read as anything.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
                return@awaitEachGesture
            }
            Outcome.CANCELLED -> return@awaitEachGesture
            Outcome.DRAG -> Unit
        }

        var following = false
        var fraction = 0f
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            velocity.addPosition(change.uptimeMillis, change.position)
            val travel = (change.position.x - down.position.x) * followDirection
            val slowEnough = followAtOnce || change.uptimeMillis - down.uptimeMillis >= SLOW_DRAG_MS
            if (!following && slowEnough && travel > slop) following = true
            if (following) {
                fraction = (travel / size.width).coerceIn(0f, 1f)
                onFollow(fraction)
            }
            if (!change.pressed) {
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                when {
                    following -> onFollowEnd(fraction, velocity.calculateVelocity().x * followDirection)
                    abs(dx) >= minSwipe && abs(dx) > abs(dy) * 1.5f -> onQuickSwipe(dx)
                }
                break
            }
        }
    }
}

private enum class Outcome { TAP, LONG_PRESS, DRAG, CANCELLED }

/** A drag still going after this long is deliberate: it moves the cover rather than turning a page. */
private const val SLOW_DRAG_MS = 250L
