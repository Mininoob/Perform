package com.perform.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.perform.sync.CoverCloth
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.perform.desktop.Library
import com.perform.desktop.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/** Decoded covers, most recently used kept, so scrolling a big grid doesn't re-read files. */
class CoverCache(
    private val library: Library,
    /** Another library's book: its cover from the peer cache (fetched on first look when full). */
    private val peerCover: (LibraryBook, Boolean) -> java.nio.file.Path? = { _, _ -> null },
    private val capacity: Int = 600,
) {
    private val images = object : LinkedHashMap<String, ImageBitmap>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > capacity
    }

    fun cached(key: String): ImageBitmap? = synchronized(images) { images[key] }

    /** Loads off the UI thread; null when the book has no cover. */
    suspend fun load(book: LibraryBook, size: Library.CoverSize): ImageBitmap? {
        val etag = book.catalog.coverEtag ?: return null
        val key = key(book, size)
        cached(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            (if (book.peerId != null) peerCover(book, size == Library.CoverSize.FULL) else library.cover(book.catalog.contentId, size))
                ?.let { runCatching { SkiaImage.makeFromEncoded(it.toFile().readBytes()).toComposeImageBitmap() }.getOrNull() }
        } ?: return null
        synchronized(images) { images[key] = bitmap }
        return bitmap
    }

    fun key(book: LibraryBook, size: Library.CoverSize) = "${book.peerId.orEmpty()}${book.catalog.contentId}:${book.catalog.coverEtag}:${size.name}"
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
    val shape = RoundedCornerShape(if (full) 4.dp else 3.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
            .shadow(if (full) 12.dp else 3.dp, shape)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            TypeCover(book.catalog.title, book.catalog.author, book.catalog.genres)
        }
    }
}

/**
 * The series' cover for a book that has none, identical to the phone's: two colours from
 * its genre's cloth, the author along a rule at the head, the title in italic. The
 * lettering scales with the cover, so a grid thumbnail and the details jacket match.
 */
@Composable
fun TypeCover(title: String, author: String?, genres: List<String>, modifier: Modifier = Modifier) {
    val cloth = CoverCloth.of(genres, title)
    val ground = Color(cloth.ground)
    val ink = Color(cloth.ink)
    BoxWithConstraints(modifier.fillMaxSize().background(ground)) {
        val width = maxWidth.value
        val scale = width / 92f
        val longest = title.split(' ').maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val titleSize = minOf(15f * scale, (width - 20f * scale) / (longest * 0.62f))
        Column(Modifier.fillMaxSize().padding((10 * scale).dp)) {
            author?.let {
                Text(
                    it.uppercase(),
                    color = ink.copy(alpha = 0.85f),
                    fontSize = (6.5f * scale).sp,
                    letterSpacing = (1.3f * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height((5 * scale).dp))
            }
            Box(Modifier.fillMaxWidth().height((0.75f * scale).coerceAtLeast(0.75f).dp).background(ink.copy(alpha = 0.6f)))
            Spacer(Modifier.weight(1f))
            Text(
                title,
                color = ink,
                fontFamily = LiterataDisplay,
                fontStyle = FontStyle.Italic,
                fontSize = titleSize.sp,
                lineHeight = (titleSize * 1.08f).sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height((8 * scale).dp))
            Text("PERFORM", color = ink.copy(alpha = 0.55f), fontSize = (5.5f * scale).sp, letterSpacing = (2f * scale).sp)
        }
    }
}
