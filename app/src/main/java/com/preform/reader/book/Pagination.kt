package com.preform.reader.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/**
 * Splits [text] into pages that exactly fill a [widthPx] x [heightPx] box.
 *
 * Lays the whole text out once at the page width, then walks its lines and cuts a
 * new page whenever the next line would overflow. Breaks therefore always fall on
 * real line boundaries, so nothing is clipped mid-line.
 *
 * Returns each page's character range in [text].
 *
 * A stand-in until Readium's navigator does pagination for real books in Phase 1.
 */
fun paginate(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
): List<IntRange> {
    if (text.isEmpty() || widthPx <= 0 || heightPx <= 0) return listOf(0 until text.length)

    val layout: TextLayoutResult = measurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = widthPx),
    )

    val pages = mutableListOf<IntRange>()
    var firstLine = 0
    var pageTop = layout.getLineTop(0)

    for (line in 0 until layout.lineCount) {
        val overflows = layout.getLineBottom(line) - pageTop > heightPx
        if (overflows && line > firstLine) {
            pages += layout.getLineStart(firstLine) until layout.getLineStart(line)
            firstLine = line
            pageTop = layout.getLineTop(line)
        }
    }
    pages += layout.getLineStart(firstLine) until text.length
    return pages
}

/**
 * Where the reader is, kept as a character offset rather than a page number.
 *
 * Page numbers are not stable: the Fold's cover screen fits far less text than its
 * inner screen, so folding changes how many pages there are. Anchoring to the first
 * character of the current page means a fold lands the reader on whichever new page
 * contains the text they were just reading.
 *
 * Plain snapshot state, so it can be driven from outside composition too - the back
 * cover's swipe arrives through a View callback, not through Compose.
 */
class ReadingPosition {

    var pages: List<IntRange> by mutableStateOf(emptyList())
        private set

    var pageIndex by mutableIntStateOf(0)
        private set

    private var anchor = 0

    val pageCount: Int get() = pages.size.coerceAtLeast(1)

    /** Repaginate, keeping the reader on the page that holds their current text. */
    fun updatePages(newPages: List<IntRange>) {
        if (newPages == pages) return
        pages = newPages
        pageIndex = newPages.indexOfLast { it.first <= anchor }.coerceAtLeast(0)
    }

    fun next(): Boolean = goTo(pageIndex + 1)

    fun previous(): Boolean = goTo(pageIndex - 1)

    private fun goTo(index: Int): Boolean {
        if (index !in pages.indices || index == pageIndex) return false
        pageIndex = index
        anchor = pages[index].first
        return true
    }
}
