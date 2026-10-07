package com.preform.reader.spike

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * Finds the cover screen and keeps a [CoverPresentation] on it while Book Mode is on.
 *
 * Presentation displays come and go as the lid opens and shuts, so this listens for
 * display changes rather than looking once at startup.
 *
 * Measured on a Z Flip 5: while the device is OPEN the cover screen is powered off
 * and is not enumerated at all - getDisplays() returns only id=0. Whether it becomes
 * addressable the moment the lid shuts is the open question this class exists to
 * answer.
 */
class SecondaryDisplays(private val activity: Activity) {

    private val displayManager =
        activity.getSystemService(DisplayManager::class.java)

    private var presentation: CoverPresentation? = null
    private var enabled = false
    private var onChange: (() -> Unit)? = null

    /** Every display the platform will let us present on, for the diagnostics panel. */
    fun describeAll(): List<String> = buildList {
        displayManager.displays.forEach { d ->
            val presentable = displayManager
                .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .any { it.displayId == d.displayId }
            add("id=${d.displayId} \"${d.name}\" state=${stateName(d.state)} presentable=$presentable valid=${d.isValid}")
        }
    }

    fun presentationDisplay(): Display? =
        displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull()

    fun setEnabled(value: Boolean) {
        enabled = value
        sync()
    }

    fun start(onChange: () -> Unit) {
        this.onChange = onChange
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        describeAll().forEach { debugLog(TAG, "display: $it") }
        sync()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(listener)
        dismiss()
    }

    private fun sync() {
        val target = presentationDisplay()

        if (!enabled || target == null) {
            dismiss()
            return
        }

        // Already showing on the right display: leave it alone rather than flicker.
        presentation?.let { existing ->
            if (existing.display.displayId == target.displayId && existing.isShowing) return
        }
        dismiss()

        runCatching {
            CoverPresentation(activity, target, "THE\nCOMPLETE\nWORKS").also {
                it.show()
                presentation = it
                debugLog(TAG, "cover presented on display ${target.displayId} (${target.name})")
            }
        }.onFailure {
            // A secondary display can disappear between the lookup and the show().
            debugLog(TAG, "could not present on display ${target.displayId}", it)
        }
    }

    private fun dismiss() {
        presentation?.runCatching { dismiss() }
        presentation = null
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($state)"
    }

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = bump("added", displayId)
        override fun onDisplayRemoved(displayId: Int) = bump("removed", displayId)
        override fun onDisplayChanged(displayId: Int) = bump("changed", displayId)

        private fun bump(what: String, displayId: Int) {
            debugLog(TAG, "display $what: $displayId")
            // Dump the full roster on every change: the question we are chasing is
            // exactly when the cover screen becomes presentable to us.
            describeAll().forEach { debugLog(TAG, "  roster: $it") }
            sync()
            onChange?.invoke()
        }
    }

    private companion object {
        const val TAG = "FoldProbe"
    }
}
