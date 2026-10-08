package com.preform.reader.reader

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.children
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.preform.reader.foldable.debugLog

/**
 * Holds a PDF still according to its [PdfLock]: in Pages mode no dragging or zooming at
 * all; in Scroll mode dragging stays (it is the scrolling) but zoom is pinned, and a page
 * at fit-to-screen size has nothing to drift sideways into.
 *
 * Readium reuses one PDFView and rebuilds its configuration on every document load
 * (open, settings change, fold), which switches dragging back on. So the lock is applied
 * in two places:
 *  - [configure], from Readium's `onConfigurePdfView` hook, runs inside every load and
 *    is the reliable path for dragging and double-tap zoom;
 *  - [enforce] applies a change of [lock] to the live view immediately, and pins pinch
 *    zoom, which the configurator can't set. It compares against the view's actual state
 *    each time rather than remembering what it did, because a load can undo it unseen.
 */
class PdfPageLock {

    var lock: PdfLock = PdfLock.NONE
        private set

    /**
     * Show each page whole rather than filling the width. Readium's pdfium adapter accepts
     * a "contain" fit but never applies it - it only acts on fit-to-width - so the fit is
     * set here instead, in the same per-load hook.
     */
    var wholePage: Boolean = false
        private set

    /** Call before Readium reloads with new settings, so the load hook sees the new values. */
    fun update(lock: PdfLock, wholePage: Boolean) {
        this.lock = lock
        this.wholePage = wholePage
        enforce()
    }

    private val dragAllowed get() = lock != PdfLock.PAGES
    private val zoomAllowed get() = lock == PdfLock.NONE

    private var root: View? = null
    private var lastSize = 0L
    private val listener = ViewTreeObserver.OnGlobalLayoutListener {
        enforce()
        // A fold or rotation resizes the view, and the viewer keeps its relative scroll
        // position rather than the page: re-center once the new size has settled.
        val pdf = find() ?: return@OnGlobalLayoutListener
        val size = (pdf.width.toLong() shl 32) or pdf.height.toLong()
        if (size != lastSize) {
            lastSize = size
            pdf.post { recenter() }
        }
    }

    fun attach(root: View) {
        this.root = root
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        enforce()
    }

    fun detach() {
        root?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        root = null
    }

    /** Readium's per-load hook. */
    fun configure(configurator: PDFView.Configurator) {
        configurator
            .enableSwipe(dragAllowed)
            .enableDoubletap(zoomAllowed)
            .pageFitPolicy(if (wholePage) FitPolicy.BOTH else FitPolicy.WIDTH)
            .fitEachPage(true)
            // Pads each page out to a full screen, so a turned page lands centered with
            // none of its neighbours peeking in.
            .autoSpacing(wholePage && lock == PdfLock.PAGES)
            // Readium opens at the saved page with jumpTo, which doesn't center it.
            .onLoad { recenter() }
        debugLog(TAG, "configured on load: lock=$lock wholePage=$wholePage")
    }

    /** One page forward or back, landing centered. Only for locked pages. */
    fun turn(forward: Boolean): Boolean {
        val pdf = find() ?: return false
        return show(pdf, pdf.currentPage + if (forward) 1 else -1)
    }

    /** Puts the current page back in the middle, after a jump made by someone else. */
    fun recenter() {
        if (lock != PdfLock.PAGES) return
        val pdf = find() ?: return
        show(pdf, pdf.currentPage)
    }

    /**
     * Shows [page] centered, in one step: no animation, so nothing slides into place.
     *
     * With auto-spacing each page sits centered in a screen-wide cell, but the viewer's
     * jumpTo puts the page's own left edge at the screen edge (the first page excepted,
     * which it leaves at offset zero, already centered). The library's own center snap
     * moves by half the spare width from there; this does the same, without animating.
     */
    private fun show(pdf: PDFView, page: Int): Boolean {
        if (page !in 0 until pdf.pageCount) return false
        pdf.jumpTo(page, false)
        if (page > 0) {
            val pageWidth = pdf.getPageSize(page).width * pdf.zoom
            pdf.moveTo(pdf.currentXOffset + (pdf.width - pageWidth) / 2f, pdf.currentYOffset)
        }
        return true
    }

    private fun find(): PDFView? = root?.let(::find)

    private fun enforce() {
        val pdf = find() ?: return
        if (pdf.isSwipeEnabled != dragAllowed) {
            pdf.setSwipeEnabled(dragAllowed)
            debugLog(TAG, "drag enabled=$dragAllowed")
        }
        if (!zoomAllowed && pdf.maxZoom != 1f) {
            pdf.setMinZoom(1f)
            pdf.setMidZoom(1f)
            pdf.setMaxZoom(1f)
            if (pdf.zoom != 1f) pdf.resetZoom()
            debugLog(TAG, "zoom pinned at 1x")
        } else if (zoomAllowed && pdf.maxZoom == 1f) {
            // The viewer's own defaults.
            pdf.setMinZoom(1f)
            pdf.setMidZoom(1.75f)
            pdf.setMaxZoom(3f)
            debugLog(TAG, "zoom restored")
        }
    }

    private fun find(view: View): PDFView? = when (view) {
        is PDFView -> view
        is ViewGroup -> view.children.firstNotNullOfOrNull(::find)
        else -> null
    }

    private companion object {
        const val TAG = "PageLock"
    }
}
