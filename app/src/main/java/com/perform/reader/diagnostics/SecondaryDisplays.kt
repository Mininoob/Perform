package com.perform.reader.diagnostics

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.perform.reader.foldable.debugLog

/**
 * Lists the displays the platform exposes to apps, and re-reports as they change.
 *
 * Diagnostics only. On the Z Fold 8 this only ever shows one display, which is why the
 * outer cover goes through WindowAreaController rather than Presentation.
 */
class SecondaryDisplays(activity: Activity) {

    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private var listener: DisplayManager.DisplayListener? = null

    fun describeAll(): List<String> {
        val presentable = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .mapTo(HashSet()) { it.displayId }
        return displayManager.displays.map { d ->
            "id=${d.displayId} \"${d.name}\" state=${stateName(d.state)} presentable=${d.displayId in presentable}"
        }
    }

    fun start(onChange: () -> Unit) {
        stop()
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = changed("added", displayId)
            override fun onDisplayRemoved(displayId: Int) = changed("removed", displayId)
            override fun onDisplayChanged(displayId: Int) = changed("changed", displayId)

            private fun changed(what: String, displayId: Int) {
                debugLog(TAG, "display $what: $displayId")
                onChange()
            }
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        this.listener = listener
    }

    fun stop() {
        listener?.let(displayManager::unregisterDisplayListener)
        listener = null
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($state)"
    }

    private companion object {
        const val TAG = "Diagnostics"
    }
}
