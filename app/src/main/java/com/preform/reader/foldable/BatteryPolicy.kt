package com.preform.reader.foldable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.preform.reader.BuildConfig

/** Below this, an unplugged phone stops holding the screen on for the cover. */
private const val LOW_BATTERY_PERCENT = 20

/**
 * Whether Book Mode may hold the screen on for the shut cover.
 *
 * A lit screen keeps the whole phone out of deep sleep, which is the one genuinely
 * expensive thing Book Mode does. So it yields to Power Saving mode and to a low,
 * unplugged battery, and re-evaluates live as either changes.
 */
@Composable
fun rememberScreenHoldAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(screenHoldAllowed(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                allowed = screenHoldAllowed(c)
            }
        }
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        // Both are protected system broadcasts, so they still arrive when not exported.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return allowed
}

private fun screenHoldAllowed(context: Context): Boolean {
    if (context.getSystemService(PowerManager::class.java).isPowerSaveMode) return false
    val battery = context.getSystemService(BatteryManager::class.java)
    return battery.isCharging ||
        battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) > LOW_BATTERY_PERCENT
}

/** Diagnostic logging that compiles into release builds but never runs there. */
internal fun debugLog(tag: String, message: String, error: Throwable? = null) {
    if (BuildConfig.DEBUG) Log.d(tag, message, error)
}
