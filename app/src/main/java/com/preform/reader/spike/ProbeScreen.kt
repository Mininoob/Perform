package com.preform.reader.spike

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.preform.reader.book.ReadingPosition
import com.preform.reader.book.paginate
import com.preform.reader.settings.SettingsScreen
import com.preform.reader.settings.SwipeDirection
import kotlinx.coroutines.delay

@Composable
fun ProbeScreen(
    snapshot: PostureSnapshot?,
    bookMode: Boolean,
    displayReport: List<String>,
    onToggleBookMode: () -> Unit,
    bookText: String,
    position: ReadingPosition,
    backSwipeEnabled: Boolean,
    onBackSwipeEnabledChange: (Boolean) -> Unit,
    backSwipeForNext: SwipeDirection,
    onBackSwipeForNextChange: (SwipeDirection) -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var showDiagnostics by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        val closed = snapshot?.posture is FoldPosture.Closed

        // A shut book has no status bar. Nothing else sells the illusion as cheaply,
        // so the cover goes fully immersive and the bars come straight back the
        // moment we leave it.
        val immersive = bookMode && closed
        val view = LocalView.current
        LaunchedEffect(immersive) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            if (immersive) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // Measured on the Fold 8 Ultra: a lit cover on a shut phone costs ~94 mA, and
        // dimming it to AOD level only saved ~13 mA - the cost is the phone being held
        // awake, not the pixels. So the cover is not held lit indefinitely. It is held
        // briefly after the lid shuts, then the phone sleeps on its normal timeout, and
        // every wake comes straight back to the cover over the lock screen.
        val holdAllowed = rememberScreenHoldAllowed()
        LaunchedEffect(immersive, holdAllowed) {
            val activity = view.context as? Activity ?: return@LaunchedEffect
            val window = activity.window
            activity.showOverLockScreen(immersive)
            if (immersive && holdAllowed) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                debugLog("FoldProbe", "screen hold on for ${COVER_HOLD_MS / 1000}s")
                delay(COVER_HOLD_MS)
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            debugLog("FoldProbe", "screen hold released (cover=$immersive allowed=$holdAllowed)")
        }

        when {
            // The shut device: cover art and nothing else, until they flip it off.
            immersive -> CoverFace(
                onExit = {
                    val activity = view.context as? Activity
                    val keyguard = activity?.getSystemService(KeyguardManager::class.java)
                    if (activity != null && keyguard?.isKeyguardLocked == true) {
                        // The cover is shown over the lock screen, so it must never be a
                        // way past it. While locked, a tap asks to unlock and leaves
                        // Book Mode alone.
                        keyguard.requestDismissKeyguard(activity, null)
                    } else {
                        onToggleBookMode()
                    }
                },
            )

            showSettings -> SettingsScreen(
                backSwipeEnabled = backSwipeEnabled,
                onBackSwipeEnabledChange = onBackSwipeEnabledChange,
                backSwipeForNext = backSwipeForNext,
                onBackSwipeForNextChange = onBackSwipeForNextChange,
                onClose = { showSettings = false },
            )

            showDiagnostics -> Diagnostics(
                snapshot = snapshot,
                bookMode = bookMode,
                displayReport = displayReport,
                onToggleBookMode = onToggleBookMode,
                onClose = { showDiagnostics = false },
            )

            // Open device: the book, which is the point of the whole app.
            else -> ReaderPage(
                bookText = bookText,
                position = position,
                bookMode = bookMode,
                onToggleBookMode = onToggleBookMode,
                onOpenSettings = { showSettings = true },
                onOpenDiagnostics = { showDiagnostics = true },
            )
        }
    }
}

/**
 * The reading surface: one page at a time, turned by tapping the outer thirds.
 *
 * A stand-in until Readium's navigator lands in Phase 1; the page turns go through
 * [ReadingPosition] so the same controls will drive it unchanged.
 */
@Composable
private fun ReaderPage(
    bookText: String,
    position: ReadingPosition,
    bookMode: Boolean,
    onToggleBookMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12100E))
            .safeDrawingPadding()
            .padding(horizontal = 26.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pride and Prejudice",
                color = Color(0xFF6E665C),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "settings",
                color = Color(0xFF6E665C),
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onOpenSettings),
            )
            Text(
                text = "diagnostics",
                color = Color(0xFF6E665C),
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onOpenDiagnostics),
            )
        }

        // Tap zones: left third back, right third forward. The middle is deliberately
        // dead so a tap made while just holding the phone doesn't lose your place.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(position) {
                    detectTapGestures { tap ->
                        val third = size.width / 3f
                        when {
                            tap.x < third -> position.previous()
                            tap.x > third * 2 -> position.next()
                        }
                    }
                },
        ) {
            val measurer = rememberTextMeasurer()
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            val pages = remember(bookText, widthPx, heightPx) {
                paginate(measurer, bookText, PageTextStyle, widthPx, heightPx)
            }
            LaunchedEffect(pages) { position.updatePages(pages) }

            // Measured and drawn with the same style object, or the breaks would lie.
            val range = position.pages.getOrNull(position.pageIndex) ?: pages.first()
            BasicText(
                text = bookText.substring(range.first, range.last + 1).trimStart('\n'),
                style = PageTextStyle,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${position.pageIndex + 1} of ${position.pageCount}",
                color = Color(0xFF6E665C),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (bookMode) "book mode on" else "book mode off",
                color = if (bookMode) Color(0xFFD6A15C) else Color(0xFF6E665C),
                fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onToggleBookMode),
            )
        }
    }
}

/** Full-bleed cover on the shut device. Tapping it flips Book Mode back off. */
@Composable
private fun CoverFace(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2B1B12), Color(0xFF7A4A2B), Color(0xFF3D2519)),
                ),
            )
            .clickable(onClick = onExit),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color(0x66000000))
                .align(Alignment.TopCenter),
        )
        Text(
            text = "THE\nCOMPLETE\nWORKS",
            color = Color(0xFFF3E4D2),
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun Diagnostics(
    snapshot: PostureSnapshot?,
    bookMode: Boolean,
    displayReport: List<String>,
    onToggleBookMode: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Preform · fold probe",
                color = Color(0xFFE8E8EA),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "close",
                color = Color(0xFF8A8A90),
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }

        Text(
            text = "PRESENTATION DISPLAYS",
            color = Color(0xFF8A8A90),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        if (displayReport.isEmpty()) {
            Text("none reported", color = BAD, fontSize = 12.sp)
        }
        displayReport.forEach {
            Text(it, color = Color(0xFFB9B9C0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))

        if (snapshot == null) {
            Text("waiting for WindowLayoutInfo", color = Color(0xFF8A8A90), fontSize = 13.sp)
        } else {
            val posture = when (val p = snapshot.posture) {
                is FoldPosture.Closed -> "CLOSED (cover screen, display ${p.displayId})"
                is FoldPosture.HalfOpen -> "HALF-OPEN ${p.orientation} separating=${p.isSeparating}"
                is FoldPosture.Open -> "OPEN (inner, display ${p.displayId})"
            }
            Field("posture", posture)
            Field("window px", "${snapshot.widthPx} x ${snapshot.heightPx}")
            Field("raw emissions", snapshot.rawEmissions.toString())
            Field("meaningful updates", snapshot.meaningfulUpdates.toString(), OK)

            val healthy = snapshot.createCount == 1
            Field("onCreate count", snapshot.createCount.toString(), if (healthy) OK else BAD)
            snapshot.describedFeatures.forEach {
                Text(it, color = Color(0xFFB9B9C0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onToggleBookMode, modifier = Modifier.fillMaxWidth()) {
            Text(if (bookMode) "Book mode: ON" else "Book mode: OFF")
        }
    }
}

@Composable
private fun Field(label: String, value: String, valueColor: Color = Color(0xFFE8E8EA)) {
    Column {
        Text(label.uppercase(), color = Color(0xFF6E6E76), fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

/** How long the cover stays lit after the lid shuts, before normal sleep applies. */
private const val COVER_HOLD_MS = 30_000L

/** Shows this activity over the lock screen while the shut cover is up, and only then. */
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

private val OK = Color(0xFF6ED08A)
private val BAD = Color(0xFFE5675F)

private val PageTextStyle = TextStyle(
    color = Color(0xFFDDD3C6),
    fontSize = 17.sp,
    lineHeight = 29.sp,
    fontFamily = FontFamily.Serif,
)
