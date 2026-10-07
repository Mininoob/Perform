package com.preform.reader.reader

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * Hands-free reading.
 *
 * EPUB: a small scroller is injected into the chapter's page and driven by the browser's
 * own frame clock, so motion is smooth without a JavaScript round trip per frame. Kotlin
 * only polls about twice a second, to re-inject after a chapter change and to move to
 * the next chapter when this one runs out. Fractional pixels are accumulated, because
 * at slow speeds a frame's movement is well under one pixel and scrollBy would drop it.
 *
 * PDF has no web page to script, so it turns a page at a time on a timer instead.
 */
object AutoScroller {

    /** Runs until cancelled; stops the in-page scroller on the way out. */
    suspend fun runEpub(navigator: EpubNavigatorFragment, pixelsPerSecond: Double) {
        try {
            while (true) {
                val result = navigator.evaluateJavascript(start(pixelsPerSecond))
                if (result?.contains("end") == true) {
                    navigator.goForward(animated = false)
                    delay(1_200)
                } else {
                    delay(500)
                }
            }
        } finally {
            withContext(NonCancellable) { navigator.evaluateJavascript(STOP) }
        }
    }

    /** Turns a page every [pageIntervalMs]. */
    suspend fun runPaged(navigator: OverflowableNavigator, pageIntervalMs: Long) {
        while (true) {
            delay(pageIntervalMs)
            if (!navigator.goForward(animated = true)) break
        }
    }

    private const val STOP = "if (window.__pfScroll) { window.__pfScroll.running = false; }"

    private fun start(speed: Double) = """
        (function(speed) {
          if (!window.__pfScroll) {
            window.__pfScroll = { speed: speed, running: true, last: null, carry: 0 };
            var step = function(t) {
              var s = window.__pfScroll;
              if (s.running && s.last !== null) {
                s.carry += s.speed * (t - s.last) / 1000;
                var whole = Math.floor(s.carry);
                if (whole > 0) { window.scrollBy(0, whole); s.carry -= whole; }
              }
              s.last = s.running ? t : null;
              requestAnimationFrame(step);
            };
            requestAnimationFrame(step);
          }
          window.__pfScroll.speed = speed;
          window.__pfScroll.running = true;
          var el = document.scrollingElement || document.documentElement;
          return (window.scrollY + window.innerHeight >= el.scrollHeight - 2) ? "end" : "ok";
        })($speed)
    """.trimIndent()
}
