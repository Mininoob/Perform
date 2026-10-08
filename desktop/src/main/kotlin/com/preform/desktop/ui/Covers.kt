package com.preform.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preform.desktop.Library
import com.preform.desktop.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** Decoded covers, most recently used kept, so scrolling a big grid doesn't re-read files. */
class CoverCache(private val library: Library, private val capacity: Int = 600) {
    private val images = object : LinkedHashMap<String, ImageBitmap>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > capacity
    }

    fun cached(key: String): ImageBitmap? = synchronized(images) { images[key] }

    /** Loads off the UI thread; null when the book has no cover. */
    suspend fun load(book: LibraryBook, size: Library.CoverSize): ImageBitmap? {
        val etag = book.catalog.coverEtag ?: return null
        val key = "${book.catalog.contentId}:$etag:${size.name}"
        cached(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            library.cover(book.catalog.contentId, size)
                ?.let { runCatching { SkiaImage.makeFromEncoded(it.toFile().readBytes()).toComposeImageBitmap() }.getOrNull() }
        } ?: return null
        synchronized(images) { images[key] = bitmap }
        return bitmap
    }

    fun key(book: LibraryBook, size: Library.CoverSize) = "${book.catalog.contentId}:${book.catalog.coverEtag}:${size.name}"
}

/** A book cover at 2:3, or a title card in the book's own tone when it has none. */
@Composable
fun BookCover(book: LibraryBook, covers: CoverCache, modifier: Modifier = Modifier, full: Boolean = false) {
    val size = if (full) Library.CoverSize.FULL else Library.CoverSize.THUMB
    var image by remember(book.catalog.contentId, book.catalog.coverEtag, full) {
        mutableStateOf(covers.cached(covers.key(book, size)))
    }
    LaunchedEffect(book.catalog.contentId, book.catalog.coverEtag, full) {
        if (image == null) image = covers.load(book, size)
    }
    val shape = RoundedCornerShape(if (full) 8.dp else 5.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(if (full) 10.dp else 3.dp, shape)
            .clip(shape)
            .background(titleCardColor(book.catalog.title)),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                book.catalog.title,
                color = Color(0xFFF3EEE6),
                fontFamily = FontFamily.Serif,
                fontSize = if (full) 22.sp else 13.sp,
                lineHeight = if (full) 28.sp else 16.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(if (full) 20.dp else 10.dp),
            )
        }
    }
}

/** A muted cloth-binding color picked from the title, so cover-less books still look distinct. */
private fun titleCardColor(title: String): Color {
    val tones = listOf(0xFF3D4A5C, 0xFF4F3D4A, 0xFF3F5246, 0xFF5A4636, 0xFF35485A, 0xFF4B3F5E, 0xFF5B3A3A, 0xFF384F52)
    return Color(tones[(title.hashCode() and Int.MAX_VALUE) % tones.size])
}
