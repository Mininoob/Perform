package com.preform.reader.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.preform.reader.R
import com.preform.reader.book.ReadingPosition
import com.preform.reader.settings.ReaderSettings
import com.preform.reader.settings.SwipeDirection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 0 hardware spike.
 *
 * Answers three questions on real hardware before any product code is written:
 *  1. Does this activity survive a fold, or does the system recreate it?
 *     [createCount] is static, so it keeps counting across recreations: if it ever
 *     reads higher than 1 after folding, our configChanges list is wrong.
 *  2. What does WindowManager actually report in each posture on this device?
 *  3. Can we paint the cover screen ourselves via the Presentation API, without
 *     depending on the user allowlisting us in Good Lock?
 */
class FoldProbeActivity : ComponentActivity() {

    private lateinit var secondaryDisplays: SecondaryDisplays

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createCount++
        enableEdgeToEdge()

        // No FLAG_KEEP_SCREEN_ON here. Holding it for the whole activity meant the
        // inner screen never timed out while reading; the hold now belongs only to the
        // shut cover, and ProbeScreen owns it.

        val repository = FoldStateRepository(this)
        secondaryDisplays = SecondaryDisplays(this)

        val bookText = resources.openRawResource(R.raw.pride_and_prejudice_ch1)
            .bufferedReader()
            .use { it.readText() }
            .trim()

        // Lives here, outside composition, because two surfaces turn its pages: the tap
        // zones inside, and the swipe on the back cover, which arrives as a View callback
        // from the dual-screen session.
        val position = ReadingPosition()

        val settings = ReaderSettings(this)
        val backSwipeForNext = settings.backSwipeForNext
            .stateIn(lifecycleScope, SharingStarted.Eagerly, SwipeDirection.LEFT)

        val backSwipeEnabled = settings.backSwipeEnabled
            .stateIn(lifecycleScope, SharingStarted.Eagerly, true)

        val rearCover = RearDisplayCover(this) { direction ->
            when {
                // Switched off in settings: no page turn, and no buzz either.
                !backSwipeEnabled.value -> false
                direction == backSwipeForNext.value -> position.next()
                else -> position.previous()
            }
        }

        setContent {
            // remember is load-bearing, not tidiness. Calling snapshots() in the
            // composable body builds a fresh Flow every recomposition, which restarts
            // collection, which emits, which recomposes: a runaway loop that made the
            // hinge sensor look noisy when the noise was ours.
            val postures = remember(repository) { repository.snapshots() }
            val snapshot by postures.collectAsStateWithLifecycle(initialValue = null)

            var bookMode by remember { mutableStateOf(false) }
            // Bumped by the display listener so the report below recomposes.
            var displayGeneration by remember { mutableIntStateOf(0) }

            val rearStatus by rearCover.status.collectAsStateWithLifecycle()

            val displayReport = remember(displayGeneration, snapshot, rearStatus) {
                secondaryDisplays.describeAll() + "rear area: $rearStatus"
            }

            LaunchedEffect(Unit) {
                secondaryDisplays.start { displayGeneration++ }
            }
            LaunchedEffect(Unit) {
                rearCover.run()
            }
            if (com.preform.reader.BuildConfig.DEBUG) {
                LaunchedEffect(Unit) {
                    PowerMeter(this@FoldProbeActivity).run()
                }
            }
            LaunchedEffect(bookMode) {
                secondaryDisplays.setEnabled(bookMode)
            }

            // Open device + Book Mode: book on the inside, cover on the outside.
            // Shut device: the activity is already on the outer panel and paints the
            // cover itself, so the dual-screen session is not wanted.
            val open = snapshot?.posture.let { it != null && it !is FoldPosture.Closed }
            LaunchedEffect(bookMode, open) {
                rearCover.setWanted(bookMode && open)
            }

            val swipeForNext by backSwipeForNext.collectAsStateWithLifecycle()
            val swipeEnabled by backSwipeEnabled.collectAsStateWithLifecycle()

            ProbeScreen(
                snapshot = snapshot,
                bookMode = bookMode,
                displayReport = displayReport,
                onToggleBookMode = { bookMode = !bookMode },
                bookText = bookText,
                position = position,
                backSwipeEnabled = swipeEnabled,
                onBackSwipeEnabledChange = { enabled ->
                    lifecycleScope.launch { settings.setBackSwipeEnabled(enabled) }
                },
                backSwipeForNext = swipeForNext,
                onBackSwipeForNextChange = { direction ->
                    lifecycleScope.launch { settings.setBackSwipeForNext(direction) }
                },
            )
        }
    }

    override fun onDestroy() {
        secondaryDisplays.stop()
        super.onDestroy()
    }

    companion object {
        /** Deliberately static: survives activity recreation so we can detect it. */
        var createCount = 0
            internal set
    }
}
