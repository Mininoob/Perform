package com.perform.reader.foldable

import android.app.Activity
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.perform.reader.settings.SwipeDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Puts the cover on the outside of an *open* Fold while the book reads on the inside.
 *
 * This is the camera-app trick: dual-screen presentation via WindowAreaController.
 * Plain Presentation cannot do it - measured on a Z Fold 8, DisplayManager only ever
 * enumerates one display, so the outer panel is not addressable that way at all.
 *
 * When the device is shut there is nothing to do here: the activity itself is on the
 * outer panel and paints the cover directly.
 */
@OptIn(ExperimentalWindowApi::class)
class RearDisplayCover(
    private val activity: Activity,
    /** The open book's cover (null for a title card) and title, read when a session starts. */
    private val cover: () -> Bitmap?,
    private val title: () -> String,
    /** A swipe on the back cover, in the reader's own left/right. Returns whether a page turned. */
    private val onBackSwipe: (SwipeDirection) -> Boolean,
    /** A tap on the left or right third of the back cover, as the reader sees it. Returns whether a page turned. */
    private val onBackTap: (side: SwipeDirection) -> Boolean,
) {

    private val controller = WindowAreaController.getOrCreate()
    private val mainExecutor = ContextCompat.getMainExecutor(activity)

    private var rearArea: WindowAreaInfo? = null
    private var session: WindowAreaSessionPresenter? = null
    private var wanted = false

    /** Guards the gap between asking for a session and the system handing one back. */
    private var requestInFlight = false

    private val _status = MutableStateFlow("waiting for window areas")
    val status: StateFlow<String> = _status

    /** Collect for as long as the activity lives; call from a lifecycle-scoped coroutine. */
    suspend fun run() {
        controller.windowAreaInfos.collect { infos ->
            rearArea = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
            publishStatus(infos)
            sync()
        }
    }

    fun setWanted(value: Boolean) {
        wanted = value
        sync()
    }

    /**
     * Hands the outer screen back. Must be called when the reader closes: the session
     * outlives the activity that started it, so without this the old book's cover stays
     * up and the next book finds the outer screen already taken.
     */
    fun close() {
        wanted = false
        session?.close()
    }

    private fun presentStatus(): WindowAreaCapability.Status? =
        rearArea
            ?.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
            ?.status

    private fun sync() {
        val area = rearArea
        val available =
            presentStatus() == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE

        when {
            wanted && session == null && !requestInFlight && area != null && available -> {
                requestInFlight = true
                debugLog(TAG, "requesting rear-display cover session")
                try {
                    controller.presentContentOnWindowArea(area.token, activity, mainExecutor, callback)
                } catch (e: SecurityException) {
                    // Only the app in front may take the outer screen. Callers only want it
                    // while resumed, but the system can still say no at the last moment.
                    requestInFlight = false
                    debugLog(TAG, "outer screen request refused", e)
                }
            }
            !wanted && session != null -> session?.close()
        }
    }

    private fun publishStatus(infos: List<WindowAreaInfo>) {
        val text = if (infos.isEmpty()) {
            "no window areas reported"
        } else {
            infos.joinToString("; ") { info ->
                val present = info.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA).status
                val transfer = info.getCapability(WindowAreaCapability.Operation.OPERATION_TRANSFER_ACTIVITY_TO_AREA).status
                val b = info.metrics.bounds
                "${info.type} ${b.width()}x${b.height()} present=$present transfer=$transfer"
            }
        }
        _status.value = text
        debugLog(TAG, "window areas: $text")
    }

    private val callback = object : WindowAreaPresentationSessionCallback {
        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            requestInFlight = false
            this@RearDisplayCover.session = session
            // The phone may have been folded, or Book Mode switched off, while we waited.
            if (!wanted) {
                session.close()
                return
            }
            try {
                session.setContentView(
                    CoverView(
                        context = session.context,
                        cover = cover(),
                        title = title(),
                        // This panel faces away from the reader, so its left is their right.
                        onSwipe = { dx -> onBackSwipe(if (dx < 0) SwipeDirection.RIGHT else SwipeDirection.LEFT) },
                        onTap = { x ->
                            val readerX = 1f - x
                            when {
                                readerX < 1f / 3 -> onBackTap(SwipeDirection.LEFT)
                                readerX > 2f / 3 -> onBackTap(SwipeDirection.RIGHT)
                                // The middle is where the hand holds the phone.
                                else -> false
                            }
                        },
                    ),
                )
                debugLog(TAG, "rear cover session started")
            } catch (e: RuntimeException) {
                // Folding mid-handshake can remove the outer display before we draw on it
                // (WindowManager.InvalidDisplayException). Give the session back; the next
                // unfold asks again.
                debugLog(TAG, "outer display went away before the cover was shown", e)
                session.close()
            }
        }

        override fun onSessionEnded(t: Throwable?) {
            requestInFlight = false
            session = null
            debugLog(TAG, "rear cover session ended", t)
        }

        override fun onContainerVisibilityChanged(isVisible: Boolean) {
            debugLog(TAG, "rear cover visible=$isVisible")
        }
    }

    private companion object {
        const val TAG = "BookMode"
    }
}
