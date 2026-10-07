package com.preform.reader.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.preform.reader.BuildConfig
import com.preform.reader.foldable.FoldStateRepository

/**
 * Debug-only window into what the hardware reports: posture, hinge, displays, whether
 * the activity survived a fold, and (via logcat) battery current. The Phase 0 probe,
 * minus the Book Mode prototype that grew into the real reader.
 */
class DiagnosticsActivity : ComponentActivity() {

    private lateinit var displays: SecondaryDisplays

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createCount++
        enableEdgeToEdge()

        val repository = FoldStateRepository(this) { createCount }
        displays = SecondaryDisplays(this)

        setContent {
            // Hoisted behind remember: a Flow built in the composable body restarts
            // collection on every recomposition and feeds itself.
            val postures = remember(repository) { repository.snapshots() }
            val snapshot by postures.collectAsStateWithLifecycle(initialValue = null)

            var displayGeneration by remember { mutableIntStateOf(0) }
            val displayReport = remember(displayGeneration, snapshot) { displays.describeAll() }

            LaunchedEffect(Unit) { displays.start { displayGeneration++ } }
            if (BuildConfig.DEBUG) {
                LaunchedEffect(Unit) { PowerMeter(this@DiagnosticsActivity).run() }
            }

            DiagnosticsScreen(snapshot = snapshot, displayReport = displayReport, onClose = ::finish)
        }
    }

    override fun onDestroy() {
        displays.stop()
        super.onDestroy()
    }

    companion object {
        /** Deliberately static: survives activity recreation so we can detect it. */
        var createCount = 0
            private set
    }
}
