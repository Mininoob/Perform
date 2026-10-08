package com.preform.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Cursor
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.WindowEvent
import java.awt.event.WindowStateListener

val TITLE_BAR_HEIGHT = 38.dp
val SIDEBAR_WIDTH = 248.dp

/** Room left for macOS's own close / minimise / zoom buttons, drawn over our bar. */
private val TRAFFIC_LIGHTS = 78.dp

/**
 * The window's own title bar, in the series' quiet register: the sidebar half carries the
 * mark and name, the rest is empty space to drag by. macOS keeps its native buttons (the
 * window runs with a transparent, full-content title bar); Windows gets hairline caption
 * buttons, since the window there is undecorated.
 */
@Composable
fun FrameWindowScope.TitleBar(isMac: Boolean) {
    val maximized = rememberMaximized()
    Row(
        Modifier
            .fillMaxWidth()
            .height(TITLE_BAR_HEIGHT)
            .background(colors.background)
            .then(if (isMac) Modifier else Modifier.moveWindow(this)),
    ) {
        Row(
            Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(colors.raised)
                .padding(start = if (isMac) TRAFFIC_LIGHTS else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(remember { SeriesMark() }, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Preform Library", fontFamily = LiterataDisplay, fontSize = 15.sp, color = colors.text, maxLines = 1)
        }
        VerticalDivider(color = colors.line)
        Box(Modifier.weight(1f).fillMaxHeight().background(colors.background)) {
            // Only the content side is ruled off; the sidebar runs on up into the bar.
            HorizontalDivider(Modifier.align(Alignment.BottomCenter), color = colors.line)
            if (!isMac) {
                Row(Modifier.align(Alignment.TopEnd).fillMaxHeight()) {
                    Caption(CaptionGlyph.Minimize) { window.extendedState = window.extendedState or Frame.ICONIFIED }
                    Caption(if (maximized) CaptionGlyph.Restore else CaptionGlyph.Maximize) { toggleMaximized() }
                    Caption(CaptionGlyph.Close, closing = true) {
                        window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
                    }
                }
            }
        }
    }
}

/** macOS: our bar sits under a transparent native title bar, which keeps drag, zoom and the buttons. */
fun FrameWindowScope.blendMacTitleBar() {
    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}

private fun FrameWindowScope.toggleMaximized() {
    if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) {
        window.extendedState = Frame.NORMAL
    } else {
        // An undecorated frame would otherwise maximise over the taskbar.
        window.maximizedBounds = usableBounds(window.bounds)
        window.extendedState = Frame.MAXIMIZED_BOTH
    }
}

/** The work area of the screen the window is mostly on, without the taskbar. */
private fun usableBounds(near: Rectangle): Rectangle {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val device = env.screenDevices.maxByOrNull { it.defaultConfiguration.bounds.intersection(near).let { r -> if (r.isEmpty) 0 else r.width * r.height } }
        ?: env.defaultScreenDevice
    val config = device.defaultConfiguration
    val bounds = config.bounds
    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
    // maximizedBounds is relative to the screen's origin.
    return Rectangle(insets.left, insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
}

@Composable
private fun FrameWindowScope.rememberMaximized(): Boolean {
    var maximized by remember { mutableStateOf(window.extendedState and Frame.MAXIMIZED_BOTH != 0) }
    DisposableEffect(window) {
        val listener = WindowStateListener { maximized = it.newState and Frame.MAXIMIZED_BOTH != 0 }
        window.addWindowStateListener(listener)
        onDispose { window.removeWindowStateListener(listener) }
    }
    return maximized
}

/** Drag to move; double-click to maximise or restore, as a native title bar does. */
private fun Modifier.moveWindow(scope: FrameWindowScope): Modifier = with(scope) {
    this@moveWindow
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaximized() }) }
        .pointerInput(Unit) {
            var grab = Point()
            detectDragGestures(
                onDragStart = {
                    val mouse = MouseInfo.getPointerInfo().location
                    if (window.extendedState and Frame.MAXIMIZED_BOTH != 0) {
                        // Pull a maximised window down from where it was held, like Windows does.
                        val fraction = (mouse.x - window.x).toDouble() / window.width
                        window.extendedState = Frame.NORMAL
                        window.setLocation(mouse.x - (window.width * fraction).toInt(), mouse.y - 12)
                    }
                    grab = Point(mouse.x - window.x, mouse.y - window.y)
                },
                onDrag = { change, _ ->
                    change.consume()
                    val mouse = MouseInfo.getPointerInfo().location
                    window.setLocation(mouse.x - grab.x, mouse.y - grab.y)
                },
            )
        }
}

private enum class CaptionGlyph { Minimize, Maximize, Restore, Close }

@Composable
private fun Caption(glyph: CaptionGlyph, closing: Boolean = false, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val ink = if (closing && hovered) Color.White else colors.text.copy(alpha = if (hovered) 0.9f else 0.62f)
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(
                when {
                    !hovered -> Color.Transparent
                    closing -> Color(0xFFC42B1C)
                    else -> colors.text.copy(alpha = 0.07f)
                },
            )
            .hoverable(hover)
            .clickable(interactionSource = hover, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(10.dp)) {
            val w = 1.dp.toPx()
            val s = size.width
            when (glyph) {
                CaptionGlyph.Minimize -> drawLine(ink, Offset(0f, s / 2), Offset(s, s / 2), w)
                CaptionGlyph.Maximize -> drawRect(ink, Offset(w / 2, w / 2), Size(s - w, s - w), style = Stroke(w))
                CaptionGlyph.Restore -> {
                    val inset = s * 0.22f
                    drawRect(ink, Offset(w / 2, inset), Size(s - inset - w / 2, s - inset - w / 2), style = Stroke(w))
                    drawLine(ink, Offset(inset, inset), Offset(inset, w / 2), w)
                    drawLine(ink, Offset(inset, w / 2), Offset(s - w / 2, w / 2), w)
                    drawLine(ink, Offset(s - w / 2, w / 2), Offset(s - w / 2, s - inset), w)
                    drawLine(ink, Offset(s - w / 2, s - inset), Offset(s - inset, s - inset), w)
                }
                CaptionGlyph.Close -> {
                    drawLine(ink, Offset(0f, 0f), Offset(s, s), w)
                    drawLine(ink, Offset(s, 0f), Offset(0f, s), w)
                }
            }
        }
    }
}

/**
 * Windows: an undecorated window has no resize border of its own, so thin invisible edges
 * and corners do it, with the system's resize cursors.
 */
@Composable
fun FrameWindowScope.ResizeEdges() {
    val maximized = rememberMaximized()
    if (maximized) return
    val edge = 5.dp
    val corner = 10.dp
    Box(Modifier.fillMaxSize()) {
        Edge(Alignment.TopCenter, Modifier.fillMaxWidth().height(edge), Cursor.N_RESIZE_CURSOR, top = true)
        Edge(Alignment.BottomCenter, Modifier.fillMaxWidth().height(edge), Cursor.S_RESIZE_CURSOR, bottom = true)
        Edge(Alignment.CenterStart, Modifier.fillMaxHeight().width(edge), Cursor.W_RESIZE_CURSOR, left = true)
        Edge(Alignment.CenterEnd, Modifier.fillMaxHeight().width(edge), Cursor.E_RESIZE_CURSOR, right = true)
        Edge(Alignment.TopStart, Modifier.size(corner), Cursor.NW_RESIZE_CURSOR, top = true, left = true)
        Edge(Alignment.TopEnd, Modifier.size(corner), Cursor.NE_RESIZE_CURSOR, top = true, right = true)
        Edge(Alignment.BottomStart, Modifier.size(corner), Cursor.SW_RESIZE_CURSOR, bottom = true, left = true)
        Edge(Alignment.BottomEnd, Modifier.size(corner), Cursor.SE_RESIZE_CURSOR, bottom = true, right = true)
    }
}

@Composable
private fun FrameWindowScope.Edge(
    alignment: Alignment,
    modifier: Modifier,
    cursor: Int,
    top: Boolean = false,
    bottom: Boolean = false,
    left: Boolean = false,
    right: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier
                .align(alignment)
                .pointerHoverIcon(PointerIcon(Cursor(cursor)))
                .pointerInput(Unit) {
                    var start = Rectangle()
                    var from = Point()
                    detectDragGestures(
                        onDragStart = {
                            start = window.bounds
                            from = MouseInfo.getPointerInfo().location
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val now = MouseInfo.getPointerInfo().location
                            val dx = now.x - from.x
                            val dy = now.y - from.y
                            val min = window.minimumSize
                            var x = start.x
                            var y = start.y
                            var w = start.width
                            var h = start.height
                            if (right) w = maxOf(min.width, start.width + dx)
                            if (bottom) h = maxOf(min.height, start.height + dy)
                            if (left) {
                                w = maxOf(min.width, start.width - dx)
                                x = start.x + start.width - w
                            }
                            if (top) {
                                h = maxOf(min.height, start.height - dy)
                                y = start.y + start.height - h
                            }
                            window.setBounds(x, y, w, h)
                        },
                    )
                },
        )
    }
}

/** The hairline an undecorated window draws round itself, so it doesn't melt into what's behind. */
fun Modifier.windowOutline(color: Color, enabled: Boolean, width: Dp = 1.dp): Modifier =
    if (enabled) this.border(width, color) else this
