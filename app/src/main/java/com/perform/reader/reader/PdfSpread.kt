package com.perform.reader.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Book-style page pairing for two-page spreads: the first page (the cover) alone, then
 * 2-3, 4-5 and so on, as in print, where right-hand pages carry odd numbers. Indexes
 * are 0-based; a trailing odd page stands alone.
 */
object Spreads {

    fun pagesAt(page: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val p = page.coerceIn(0, count - 1)
        if (p == 0) return listOf(0)
        val first = if (p % 2 == 1) p else p - 1
        return listOf(first, first + 1).filter { it < count }
    }

    /** First page of the next spread, or null at the end. */
    fun next(page: Int, count: Int): Int? = pagesAt(page, count).lastOrNull()?.plus(1)?.takeIf { it < count }

    /** First page of the previous spread, or null at the start. */
    fun previous(page: Int, count: Int): Int? {
        val first = pagesAt(page, count).firstOrNull() ?: return null
        return if (first == 0) null else pagesAt(first - 1, count).first()
    }

    /**
     * Which page goes on which side. Left-to-right: the cover sits alone on the right,
     * a final odd page alone on the left. Right-to-left mirrors that.
     */
    fun sides(page: Int, count: Int, rightToLeft: Boolean): Pair<Int?, Int?> {
        val pages = pagesAt(page, count)
        // (left, right) for a left-to-right book.
        val ltr: Pair<Int?, Int?> = when {
            pages.size == 2 -> pages[0] to pages[1]
            pages.firstOrNull() == 0 -> null to 0
            else -> pages.firstOrNull() to null
        }
        return if (rightToLeft) ltr.second to ltr.first else ltr
    }
}

/**
 * Renders PDF pages with Android's own PdfRenderer: Readium's pdfium adapter has no
 * two-page layout, so spreads are drawn here instead. PdfRenderer allows one open page
 * at a time, hence the lock; finished bitmaps are cached for instant page turns.
 */
class PdfPageRenderer(context: Context, uri: String) {

    private val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
        ?: error("Can't open $uri")
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private var closed = false

    val pageCount: Int = renderer.pageCount

    fun cached(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? = cache.get(key(index, maxWidth, maxHeight))

    /** The page scaled to fit [maxWidth] x [maxHeight], or null if out of range or closed. */
    suspend fun render(index: Int, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (index !in 0 until pageCount || maxWidth <= 0 || maxHeight <= 0) return null
        cached(index, maxWidth, maxHeight)?.let { return it }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                if (closed) return@withContext null
                renderer.openPage(index).use { page ->
                    val scale = min(maxWidth / page.width.toFloat(), maxHeight / page.height.toFloat())
                    val bitmap = createBitmap(max(1, (page.width * scale).toInt()), max(1, (page.height * scale).toInt()))
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key(index, maxWidth, maxHeight), bitmap)
                    bitmap
                }
            }
        }
    }

    suspend fun close() = lock.withLock {
        closed = true
        cache.evictAll()
        renderer.close()
        fd.close()
    }

    private fun key(index: Int, w: Int, h: Int) = "$index:$w:$h"
}

/**
 * Two PDF pages side by side, meeting at the gutter like an open book; on the Fold the
 * gutter lands on the hinge. Neighbouring spreads are rendered ahead of time.
 */
@Composable
fun PdfSpreadView(
    renderer: PdfPageRenderer,
    page: Int,
    rightToLeft: Boolean,
    background: ComposeColor,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(background)) {
        val margin = with(LocalDensity.current) { 12.dp.roundToPx() }
        val slotWidth = constraints.maxWidth / 2 - margin
        val slotHeight = constraints.maxHeight - 2 * margin
        val count = renderer.pageCount
        val (left, right) = Spreads.sides(page, count, rightToLeft)

        Row(Modifier.fillMaxSize()) {
            PageSlot(renderer, left, slotWidth, slotHeight, Alignment.CenterEnd, Modifier.weight(1f))
            PageSlot(renderer, right, slotWidth, slotHeight, Alignment.CenterStart, Modifier.weight(1f))
        }

        LaunchedEffect(page, slotWidth, slotHeight) {
            listOfNotNull(Spreads.next(page, count), Spreads.previous(page, count))
                .flatMap { Spreads.pagesAt(it, count) }
                .forEach { renderer.render(it, slotWidth, slotHeight) }
        }
    }
}

@Composable
private fun PageSlot(
    renderer: PdfPageRenderer,
    index: Int?,
    maxWidth: Int,
    maxHeight: Int,
    alignment: Alignment,
    modifier: Modifier,
) {
    Box(modifier.fillMaxHeight(), contentAlignment = alignment) {
        if (index == null) return@Box
        val image by produceState<ImageBitmap?>(
            initialValue = renderer.cached(index, maxWidth, maxHeight)?.asImageBitmap(),
            index, maxWidth, maxHeight,
        ) {
            value = renderer.render(index, maxWidth, maxHeight)?.asImageBitmap()
        }
        image?.let {
            Image(
                bitmap = it,
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(with(LocalDensity.current) { it.width.toDp() })
                    .shadow(4.dp),
            )
        }
    }
}
