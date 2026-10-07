package com.preform.reader.reader

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import com.preform.reader.container
import com.preform.reader.foldable.BookModeWindowEffects
import com.preform.reader.foldable.CoverFace
import com.preform.reader.foldable.FoldPosture
import com.preform.reader.foldable.FoldStateRepository
import com.preform.reader.foldable.PostureSnapshot
import com.preform.reader.foldable.RearDisplayCover
import com.preform.reader.library.BookFormat
import com.preform.reader.settings.AppTheme
import com.preform.reader.settings.ReadingMode
import com.preform.reader.settings.ReadingPreferences
import com.preform.reader.settings.SettingsFormat
import com.preform.reader.settings.SwipeDirection
import com.preform.reader.ui.PreformTheme
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri

/**
 * Reads one book with Readium's navigator, and owns Book Mode while it does.
 *
 * Its own activity because Readium creates the navigator through the activity's
 * fragment factory, which must be installed before `super.onCreate` - safe only when
 * the activity exists for exactly one book.
 */
class ReaderActivity : FragmentActivity() {

    private lateinit var session: ReaderSession
    private lateinit var prefs: StateFlow<ReadingPreferences>
    private lateinit var rearCover: RearDisplayCover
    private val navigator = MutableStateFlow<ReaderNavigator?>(null)
    private val controlsVisible = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val found = container.openBooks[intent.getLongExtra(EXTRA_BOOK_ID, -1L)]
        if (found == null) {
            // After process death the opened publication is gone; the library reopens it.
            super.onCreate(null)
            finish()
            return
        }
        session = found
        supportFragmentManager.fragmentFactory = navigatorFactory()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = container.preferences
        prefs = combine(store.globalReading, container.library.observe(session.book.id)) { global, book ->
            ReadingPreferences.resolve(global, ReadingPreferences.fromJson(book?.prefsOverrideJson))
        }.stateIn(lifecycleScope, SharingStarted.Eagerly, session.initialPreferences)

        val swipeEnabled = store.backSwipeEnabled.stateIn(lifecycleScope, SharingStarted.Eagerly, true)
        val swipeForNext = store.backSwipeForNext.stateIn(lifecycleScope, SharingStarted.Eagerly, SwipeDirection.LEFT)
        rearCover = RearDisplayCover(
            activity = this,
            cover = { session.cover },
            title = { session.book.title },
            onBackSwipe = { direction ->
                val visual = navigator.value?.visual
                when {
                    visual == null || !swipeEnabled.value -> false
                    // A right-to-left book's next page is the other way, as with a paper one.
                    (direction == swipeForNext.value) != session.book.rightToLeft -> visual.goForward(animated = true)
                    else -> visual.goBackward(animated = true)
                }
            },
        )

        val postures = FoldStateRepository(this).snapshots()
        setContent { Reader(postures) }
        savePositionAsYouRead()
    }

    @Composable
    private fun Reader(postures: Flow<PostureSnapshot>) {
        val snapshot by postures.collectAsStateWithLifecycle(initialValue = null)
        val prefs by prefs.collectAsStateWithLifecycle()
        val nav by navigator.collectAsStateWithLifecycle()
        val controls by controlsVisible.collectAsStateWithLifecycle()
        val store = container.preferences
        val bookMode by store.bookMode.collectAsStateWithLifecycle(false)
        val keepScreenOn by store.keepScreenOn.collectAsStateWithLifecycle(false)
        val appTheme by store.appTheme.collectAsStateWithLifecycle(AppTheme.SYSTEM)
        val book by container.library.observe(session.book.id).collectAsStateWithLifecycle(session.book)
        val locator by remember(nav) { nav?.visual?.currentLocator ?: MutableStateFlow(session.initialLocator) }
            .collectAsStateWithLifecycle()

        val posture = snapshot?.posture
        val closed = posture is FoldPosture.Closed
        val coverShowing = bookMode && closed
        val halfOpen = posture as? FoldPosture.HalfOpen
        // Window-relative: a hinge reads horizontal only when the window is laid out across it.
        val tabletop = halfOpen?.orientation == FoldingFeature.Orientation.HORIZONTAL
        val layout = resolveLayout(
            mode = prefs.mode ?: ReadingMode.SPREAD,
            format = session.book.format,
            windowWidthDp = LocalConfiguration.current.screenWidthDp.toFloat(),
            tabletop = tabletop,
        )

        LaunchedEffect(nav, prefs, layout) { nav?.apply(prefs, layout) }

        // Book Mode: the outside cover while open, the full-screen cover while shut.
        LaunchedEffect(Unit) { rearCover.run() }
        LaunchedEffect(bookMode, closed, posture != null) { rearCover.setWanted(bookMode && posture != null && !closed) }

        val autoScrolling = layout.autoScroll && !controls && !coverShowing
        BookModeWindowEffects(coverShowing = coverShowing, keepAwake = keepScreenOn || autoScrolling)
        SystemBars(visible = (controls || tabletop) && !coverShowing)

        if (autoScrolling && nav != null) {
            val pageHeightDp = LocalConfiguration.current.screenHeightDp
            LaunchedEffect(nav, prefs.autoScrollSpeed) {
                nav!!.autoScroll(prefs.autoScrollSpeed ?: 40.0, pageHeightDp)
            }
        }

        val colors = prefs.colors()
        val chrome = colors.toChrome()
        var showSettings by remember { mutableStateOf(false) }
        val progression = locator?.locations?.totalProgression ?: book?.progression ?: 0.0
        val seek: (Double) -> Unit = { p ->
            lifecycleScope.launch {
                session.publication.locateProgression(p)?.let { nav?.visual?.go(it, animated = false) }
            }
        }

        PreformTheme(appTheme) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(colors.background)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    val hinge = halfOpen?.takeIf { tabletop }
                    val density = LocalDensity.current
                    // The navigator stays in this one slot whatever the posture. Moving it in
                    // the tree would destroy the fragment, and Readium would rebuild it at the
                    // position the book was opened at.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (hinge != null) {
                                    Modifier.height(with(density) { hinge.hingeTopPx.toDp() })
                                } else {
                                    Modifier.weight(1f)
                                },
                            ),
                    ) {
                        NavigatorHost()
                        if (hinge == null) {
                            Fade(controls, Modifier.align(Alignment.TopCenter)) {
                                ReaderTopBar(
                                    title = session.book.title,
                                    bookMode = bookMode,
                                    colors = chrome,
                                    onBack = ::finish,
                                    onToggleBookMode = { setBookMode(!bookMode) },
                                    onOpenSettings = { showSettings = true },
                                )
                            }
                            Fade(controls, Modifier.align(Alignment.BottomCenter)) {
                                ReaderBottomBar(locator?.title, progression, chrome, seek)
                            }
                        }
                    }
                    if (hinge != null) {
                        Spacer(Modifier.height(with(density) { (hinge.hingeBottomPx - hinge.hingeTopPx).toDp() }))
                        FlexControls(
                            chapter = locator?.title,
                            progression = progression,
                            colors = chrome,
                            onPrevious = { nav?.visual?.goBackward(animated = true) },
                            onNext = { nav?.visual?.goForward(animated = true) },
                            onSeek = seek,
                            onOpenSettings = { showSettings = true },
                            onBack = ::finish,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }

                if (coverShowing) {
                    val cover = remember { session.cover?.asImageBitmap() }
                    CoverFace(cover = cover, title = session.book.title, onTap = ::onCoverTapped)
                }

                if (showSettings) {
                    var thisBook by remember { mutableStateOf(book?.prefsOverrideJson != null) }
                    ReaderSettingsSheet(
                        prefs = prefs,
                        format = if (session.book.format == BookFormat.PDF) SettingsFormat.PDF else SettingsFormat.EPUB,
                        editingThisBook = thisBook,
                        bookHasOverrides = book?.prefsOverrideJson != null,
                        onScopeChange = { thisBook = it },
                        onResetBook = { container.appScope.launch { container.library.clearOverride(session.book.id) } },
                        onChange = { change ->
                            container.appScope.launch {
                                if (thisBook) {
                                    container.library.updateOverride(session.book.id, change)
                                } else {
                                    container.preferences.updateGlobalReading(change)
                                }
                            }
                        },
                        onDismiss = { showSettings = false },
                    )
                }
            }
        }
    }

    @Composable
    private fun NavigatorHost() {
        when (session.book.format) {
            BookFormat.EPUB -> AndroidFragment<EpubNavigatorFragment>(Modifier.fillMaxSize(), onUpdate = ::attach)
            BookFormat.PDF -> AndroidFragment<PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>>(
                Modifier.fillMaxSize(),
                onUpdate = ::attach,
            )
        }
    }

    /** Hidden while reading, shown with the controls; never over the shut cover. */
    @Composable
    private fun SystemBars(visible: Boolean) {
        val view = LocalView.current
        LaunchedEffect(visible) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** Wires up a navigator fragment once: edge taps turn pages, a center tap toggles the controls. */
    private fun attach(fragment: Fragment) {
        if (navigator.value?.fragment === fragment) return
        val visual = fragment as OverflowableNavigator
        visual.addInputListener(DirectionalNavigationAdapter(visual, animatedTransition = true))
        visual.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                controlsVisible.update { !it }
                return true
            }
        })
        navigator.value = ReaderNavigator(fragment, visual)
    }

    /**
     * The shut cover shows over the lock screen, so it must never be a way past it.
     * Locked: a tap asks to unlock. Unlocked: a tap turns Book Mode off.
     */
    private fun onCoverTapped() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, null)
        } else {
            setBookMode(false)
        }
    }

    private fun setBookMode(on: Boolean) {
        container.appScope.launch { container.preferences.setBookMode(on) }
    }

    @OptIn(FlowPreview::class)
    private fun savePositionAsYouRead() {
        lifecycleScope.launch {
            navigator.collect { nav ->
                nav?.visual?.currentLocator?.debounce(1_000)?.collect(::savePosition)
            }
        }
    }

    private fun savePosition(locator: Locator) {
        val id = session.book.id
        val json = locator.toJSON().toString()
        val progression = locator.locations.totalProgression ?: 0.0
        container.appScope.launch { container.library.savePosition(id, json, progression) }
    }

    override fun onStop() {
        super.onStop()
        // The debounce may still be holding the latest page; don't lose it.
        if (::session.isInitialized) navigator.value?.visual?.currentLocator?.value?.let(::savePosition)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::session.isInitialized && isFinishing) container.openBooks.close(session.book.id)
    }

    private fun navigatorFactory(): FragmentFactory {
        val layout = resolveLayout(
            mode = session.initialPreferences.mode ?: ReadingMode.SPREAD,
            format = session.book.format,
            windowWidthDp = resources.configuration.screenWidthDp.toFloat(),
            tabletop = false,
        )
        return when (session.book.format) {
            BookFormat.EPUB -> EpubNavigatorFactory(session.publication).createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = session.initialPreferences.toEpubPreferences(layout),
                listener = object : EpubNavigatorFragment.Listener {
                    override fun onExternalLinkActivated(url: AbsoluteUrl) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    }
                },
            )
            BookFormat.PDF -> PdfNavigatorFactory(session.publication, PdfiumEngineProvider()).createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = session.initialPreferences.toPdfiumPreferences(layout),
            )
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"

        fun intent(context: Context, bookId: Long): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}

/**
 * Top-level on purpose: called inside a Column, AnimatedVisibility resolves to the
 * ColumnScope overload, which can't be used from the nested Box.
 */
@Composable
private fun Fade(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(), exit = fadeOut()) { content() }
}

/** One navigator, EPUB or PDF, behind the few operations the reader needs. */
class ReaderNavigator(val fragment: Fragment, val visual: OverflowableNavigator) {

    @Suppress("UNCHECKED_CAST")
    fun apply(prefs: ReadingPreferences, layout: ReaderLayout) {
        when (fragment) {
            is EpubNavigatorFragment -> fragment.submitPreferences(prefs.toEpubPreferences(layout))
            is PdfNavigatorFragment<*, *> ->
                (fragment as PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>)
                    .submitPreferences(prefs.toPdfiumPreferences(layout))
        }
    }

    /** Runs until cancelled. PDFs turn a page roughly every screenful at the same speed. */
    suspend fun autoScroll(pixelsPerSecond: Double, pageHeightDp: Int) {
        when (fragment) {
            is EpubNavigatorFragment -> AutoScroller.runEpub(fragment, pixelsPerSecond)
            else -> AutoScroller.runPaged(
                visual,
                pageIntervalMs = (pageHeightDp / pixelsPerSecond * 1000).toLong().coerceIn(4_000, 120_000),
            )
        }
    }
}
