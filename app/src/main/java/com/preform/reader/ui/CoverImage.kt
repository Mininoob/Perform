package com.preform.reader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Covers decoded from the JPEGs the library wrote, kept in memory for scrolling.
 * Sized by bytes, about a sixth of the heap, so a big library can't exhaust memory.
 */
object CoverCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Keyed by modification time too: a rescan rewrites a changed book's cover at the same path. */
    suspend fun load(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$path@${File(path).lastModified()}"
        cache.get(key) ?: BitmapFactory.decodeFile(path)?.also { cache.put(key, it) }
    }
}

@Composable
fun rememberCover(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let { CoverCache.load(it)?.asImageBitmap() }
    }
    return bitmap
}
