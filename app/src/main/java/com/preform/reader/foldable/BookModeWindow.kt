package com.preform.reader.foldable

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** How long the cover stays lit after the lid shuts, before normal sleep applies. */
private const val COVER_HOLD_MS = 30_000L

/**
 * The window-level side of Book Mode and reading: whether the screen is held awake and
 * whether we show over the lock screen. One effect owns FLAG_KEEP_SCREEN_ON so the
 * cover hold and "keep screen on while reading" can't fight over it.
 *
 * Measured on the Fold 8 Ultra: a lit cover on a shut phone costs ~94 mA, and dimming
 * it barely helps - the cost is being held awake. So the shut cover is held briefly,
 * then the phone sleeps on its normal timeout, and each wake comes straight back to
 * the cover over the lock screen. The automatic hold yields to Power Saving and low
 * battery; [keepAwake] is the user's explicit choice and does not.
 */
@Composable
fun BookModeWindowEffects(coverShowing: Boolean, keepAwake: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    val holdAllowed = rememberScreenHoldAllowed()

    LaunchedEffect(coverShowing, holdAllowed, keepAwake) {
        val window = activity.window
        activity.showOverLockScreen(coverShowing)
        when {
            coverShowing -> {
                if (holdAllowed) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    debugLog("BookMode", "screen hold on for ${COVER_HOLD_MS / 1000}s")
                    delay(COVER_HOLD_MS)
                }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                debugLog("BookMode", "screen hold released")
            }
            keepAwake -> window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else -> window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Shows the activity over the lock screen while the shut cover is up, and only then. */
@Suppress("DEPRECATION")
private fun Activity.showOverLockScreen(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(show)
    } else if (show) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
}
