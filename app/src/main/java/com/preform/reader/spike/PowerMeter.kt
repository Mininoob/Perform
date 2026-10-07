package com.preform.reader.spike

import android.content.Context
import android.os.BatteryManager
import kotlinx.coroutines.delay

/**
 * Logs instantaneous battery current once a second so Book Mode's real cost can be
 * measured on the device, rather than guessed at.
 *
 * Shell access to the fuel gauge is blocked on Samsung, but BatteryManager exposes the
 * same reading to apps with no permission. Units and sign vary by vendor, so the host
 * side compares magnitudes between states instead of trusting absolute values.
 */
class PowerMeter(context: Context) {

    private val battery = context.getSystemService(BatteryManager::class.java)

    suspend fun run() {
        while (true) {
            val current = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            debugLog(TAG, "current=$current")
            delay(1_000)
        }
    }

    private companion object {
        const val TAG = "PowerMeter"
    }
}
