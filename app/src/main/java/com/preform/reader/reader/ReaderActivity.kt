package com.preform.reader.reader

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AndroidUiDispatcher
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import com.github.barteksc.pdfviewer.PDFView
import com.preform.reader.container
import com.preform.reader.foldable.BookCover
import com.preform.reader.foldable.BookModeWindowEffects
import com.preform.reader.foldable.FoldPosture
import com.preform.reader.foldable.FoldStateRepository
import com.preform.reader.foldable.PostureSnapshot
import com.preform.reader.foldable.RearDisplayCover
import com.preform.reader.foldable.bookGestures
import com.preform.reader.library.BookFormat
import com.preform.reader.settings.CoverControls
import com.preform.reader.settings.PdfFit
import com.preform.reader.settings.ReadingMode
import com.preform.reader.settings.ReadingPreferences
import com.preform.reader.settings.SettingsFormat
import com.preform.reader.settings.SwipeDirection
import com.preform.reader.ui.PreformTheme
import com.preform.reader.ui.palette
import com.preform.reader.ui.readingColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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
import org.readium.r2.navigator.epub.css.RsProperties
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.toUri
import kotlin.math.roundToInt

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

    /** Exists from before the navigator is created: Readium consults it on every PDF load. */
    private val pdfLock = PdfPageLock()
    private val navigator = MutableStateFlow<ReaderNavigator?>(null)
    private val controlsVisible = MutableStateFlow(false)

    /** PDF two-page spreads, drawn by us: Readium's PDF engine can't lay them out. */
    private var pdfPages: PdfPageRenderer? = null

    /** First page of the spread on screen while a PDF spread is showing; null otherwise. */
    private val spreadPage = MutableStateFlow<Int?>(null)

    /** Readium's locator for every PDF page, to save a spread's position in Readium's terms. */
    private var pdfPositions: List<Locator> = emptyList()

    /** The book opened on the shut phone's screen, past its cover. */
    private val frontOpen = MutableStateFlow(false)

    /** 0 = cover shut over the page, 1 = swung fully open. */
    private val coverOpenness = Animatable(0f)
    private val coverHint = MutableStateFlow(false)

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

        val controls = store.coverControls.stateIn(lifecycleScope, SharingStarted.Eagerly, CoverControls.SWIPE_AND_TAP)
        val swipeForNext = store.backSwipeForNext.stateIn(lifecycleScope, SharingStarted.Eagerly, SwipeDirection.LEFT)
        val rtl = session.book.rightToLeft
        rearCover = RearDisplayCover(
            activity = this,
            cover = { session.cover },
            title = { session.book.title },
            // A right-to-left book's next page is the other way, as with a paper one.
            onBackSwipe = { direction ->
                controls.value.swipe && turnPage(forward = (direction == swipeForNext.value) != rtl)
            },
            // Unflipped, the right side goes forward, as on the front screen; flipping
            // the swipe direction flips taps with it.
            onBackTap = { side ->
                val rightIsNext = swipeForNext.value == SwipeDirection.LEFT
                controls.value.tap && turnPage(forward = ((side == SwipeDirection.RIGHT) == rightIsNext) != rtl)
            },
        )

        if (session.book.format == BookFormat.PDF) {
            pdfLock.attach(window.decorView)
            // Without a seekable file there are simply no spreads; single pages still work.
            pdfPages = runCatching { PdfPageRenderer(this, session.book.uri) }.getOrNull()
            lifecycleScope.launch { pdfPositions = session.publication.positions() }
        }

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
        val spread by spreadPage.collectAsStateWithLifecycle()
        val frontIsOpen by frontOpen.collectAsStateWithLifecycle()
        val hint by coverHint.collectAsStateWithLifecycle()
        val store = container.preferences
        val keepScreenOn by store.keepScreenOn.collectAsStateWithLifecycle(false)
        val appTheme by store.effectiveTheme.collectAsStateWithLifecycle(session.appTheme)
        val book by container.library.observe(session.book.id).collectAsStateWithLifecycle(session.book)
        // Per book, and on until switched off.
        val bookMode = book?.bookMode ?: true
        val locator by remember(nav) { nav?.visual?.currentLocator ?: MutableStateFlow(session.initialLocator) }
            .collectAsStateWithLifecycle()
        val rtl = session.book.rightToLeft

        val posture = snapshot?.posture
        val closed = posture is FoldPosture.Closed
        val shutInBookMode = bookMode && closed
        // The cover is what's showing: drives the screen hold, the lock screen and the bars.
        val coverShowing = shutInBookMode && !frontIsOpen
        val halfOpen = posture as? FoldPosture.HalfOpen
        // Window-relative: a hinge reads horizontal only when the window is laid out across it.
        val tabletop = halfOpen?.orientation == FoldingFeature.Orientation.HORIZONTAL
        val layout = resolveLayout(
            mode = prefs.mode ?: ReadingMode.SPREAD,
            format = session.book.format,
            windowWidthDp = LocalConfiguration.current.screenWidthDp.toFloat(),
            tabletop = tabletop,
            pageLock = prefs.pageLock == true,
        )
        val pdfSpread = layout.spread && pdfPages != null

        // A jump made elsewhere (the progress slider) lands a locked page off-center.
        LaunchedEffect(locator) { pdfLock.recenter() }

        val palette = appTheme.palette
        val appColors = palette.readingColors()
        LaunchedEffect(nav, prefs, layout, appColors) {
            // Before applying: a changed fit or lock makes Readium reload the PDF, and the
            // load hook must already see the new values.
            pdfLock.update(layout.pdfLock, wholePage = showsWholePage(prefs, layout))
            nav?.apply(prefs, layout, appColors)
        }

        // Entering a PDF spread picks up Readium's page; leaving hands the page back.
        LaunchedEffect(pdfSpread) {
            if (pdfSpread) {
                val page = (navigator.value?.visual?.currentLocator?.value ?: session.initialLocator)
                    ?.locations?.position?.minus(1) ?: 0
                spreadPage.value = Spreads.pagesAt(page, pdfPages!!.pageCount).first()
            } else {
                spreadPage.value?.let { page ->
                    spreadPage.value = null
                    pdfPositions.getOrNull(page)?.let { navigator.value?.visual?.go(it, animated = false) }
                    pdfLock.recenter()
                }
            }
        }

        // Folding or unfolding always lands back in Book Mode with the cover shut.
        LaunchedEffect(closed) { shutCoverAtOnce() }
        // So does the screen going off: waking shows the cover again.
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { shutCoverAtOnce() }

        // Book Mode: the outside cover while open, the full-screen cover while shut.
        LaunchedEffect(Unit) { rearCover.run() }
        // Only while in front: Android refuses the outer screen to an app in the background.
        val resumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
            .isAtLeast(Lifecycle.State.RESUMED)
        LaunchedEffect(bookMode, closed, posture != null, resumed) {
            rearCover.setWanted(bookMode && posture != null && !closed && resumed)
        }

        BookModeWindowEffects(coverShowing = coverShowing, keepAwake = keepScreenOn)
        SystemBars(visible = (controls || tabletop) && !coverShowing)

        // A PDF's pages are fixed images that reading themes can't recolor, so a PDF sits
        // on the app theme: page surround, gaps and controls alike.
        val colors = if (session.book.format == BookFormat.PDF) appColors else prefs.colors(appColors)
        LaunchedEffect(colors.background) { pdfLock.background = colors.background }
        val chrome = colors.toChrome(accent = palette.accent)
        var showSettings by remember { mutableStateOf(false) }
        val pageCount = pdfPages?.pageCount ?: 0
        val progression = spread?.let { if (pageCount > 1) it / (pageCount - 1).toDouble() else 0.0 }
            ?: locator?.locations?.totalProgression ?: book?.progression ?: 0.0
        val seek: (Double) -> Unit = { p ->
            if (spreadPage.value != null) {
                showSpread(Spreads.pagesAt((p * (pageCount - 1)).roundToInt(), pageCount).first())
            } else {
                lifecycleScope.launch {
                    session.publication.locateProgression(p)?.let { nav?.visual?.go(it, animated = false) }
                }
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
                        Box(
                            Modifier
                                .fillMaxSize()
                                // Locked PDF pages don't move under a drag, so a swipe
                                // turns the page instead. EPUB pages turn on their own.
                                .pageSwipes(enabled = layout.pdfLock == PdfLock.PAGES) { dx ->
                                    // Right-to-left swipes go forward, or backward in a right-to-left book.
                                    turnPage(forward = (dx < 0) != rtl)
                                },
                        ) {
                            NavigatorHost()
                        }

                        val pages = pdfPages
                        if (spread != null && pages != null) {
                            PdfSpreadView(
                                renderer = pages,
                                page = spread!!,
                                rightToLeft = rtl,
                                background = Color(colors.background),
                                modifier = Modifier
                                    .pageSwipes(enabled = true) { dx -> turnPage(forward = (dx < 0) != rtl) }
                                    .pointerInput(Unit) {
                                        detectTapGestures { tap -> onPageTap(tap.x, size.width) }
                                    },
                            )
                        }

                        // A book opened on the shut phone's screen: taps and quick swipes turn
                        // pages; a slow drag back, or a long press, swings the cover shut.
                        if (shutInBookMode && frontIsOpen) {
                            val closeDirection = if (rtl) -1 else 1
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .bookGestures(
                                        followDirection = closeDirection,
                                        followAtOnce = false,
                                        onTap = { tap, size -> onPageTap(tap.x, size.width) },
                                        onLongPress = ::closeFront,
                                        onQuickSwipe = { dx -> turnPage(forward = (dx < 0) != rtl) },
                                        onFollow = { f -> animateCover { coverOpenness.snapTo(1f - f) } },
                                        onFollowEnd = { f, velocity ->
                                            if (f > 0.3f || velocity > FLING_PX_PER_SECOND) {
                                                closeFront()
                                            } else {
                                                animateCover { coverOpenness.animateTo(1f, tween(220)) }
                                            }
                                        },
                                    ),
                            )
                        }

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
                                ReaderBottomBar(if (spread != null) null else locator?.title, progression, chrome, seek)
                            }
                        }
                    }
                    if (hinge != null) {
                        Spacer(Modifier.height(with(density) { (hinge.hingeBottomPx - hinge.hingeTopPx).toDp() }))
                        FlexControls(
                            chapter = if (spread != null) null else locator?.title,
                            progression = progression,
                            colors = chrome,
                            onPrevious = { turnPage(forward = false) },
                            onNext = { turnPage(forward = true) },
                            onSeek = seek,
                            onOpenSettings = { showSettings = true },
                            onBack = ::finish,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }

                // The shut cover, drawn over everything while it covers any of the page.
                if (shutInBookMode && (!frontIsOpen || coverOpenness.value < 1f)) {
                    val cover = remember { session.cover?.asImageBitmap() }
                    val openDirection = if (rtl) 1 else -1
                    BookCover(
                        cover = cover,
                        title = session.book.title,
                        rightToLeft = rtl,
                        openness = coverOpenness.value,
                        showHint = hint,
                        modifier = if (frontIsOpen) {
                            Modifier
                        } else {
                            Modifier.bookGestures(
                                followDirection = openDirection,
                                followAtOnce = true,
                                onTap = { _, _ -> onCoverTapped() },
                                onLongPress = ::openFront,
                                onQuickSwipe = { dx -> if (dx * openDirection > 0) openFront() },
                                onFollow = { f -> animateCover { coverOpenness.snapTo(f) } },
                                onFollowEnd = { f, velocity ->
                                    if (f > 0.3f || velocity > FLING_PX_PER_SECOND) {
                                        openFront()
                                    } else {
                                        animateCover { coverOpenness.animateTo(0f, tween(220)) }
                                    }
                                },
                            )
                        },
                    )
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
        // Locked PDF pages: edge taps turn through turnPage, which centers the page.
        // First in line, so the adapter below never moves a locked PDF itself.
        visual.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                if (pdfLock.lock != PdfLock.PAGES) return false
                val width = fragment.view?.width?.takeIf { it > 0 } ?: return false
                if (event.point.x in width * EDGE_FRACTION..width * (1 - EDGE_FRACTION)) return false
                onPageTap(event.point.x, width)
                return true
            }
        })
        visual.addInputListener(DirectionalNavigationAdapter(visual, animatedTransition = true))
        visual.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                controlsVisible.update { !it }
                return true
            }
        })
        navigator.value = ReaderNavigator(fragment, visual)
    }

    /** Our own tap zones, where we handle input: the outer 30% turn pages, the middle toggles the controls. */
    private fun onPageTap(x: Float, width: Int) {
        val rtl = session.book.rightToLeft
        when {
            // The left edge goes back, except in a right-to-left book.
            x < width * EDGE_FRACTION -> turnPage(forward = rtl)
            x > width * (1 - EDGE_FRACTION) -> turnPage(forward = !rtl)
            else -> controlsVisible.update { !it }
        }
    }

    /**
     * One page forward or back, by whatever is drawing the book: our PDF spread, a
     * centered locked PDF page, or Readium itself.
     */
    private fun turnPage(forward: Boolean): Boolean {
        spreadPage.value?.let { page ->
            val count = pdfPages?.pageCount ?: return false
            val target = (if (forward) Spreads.next(page, count) else Spreads.previous(page, count)) ?: return false
            showSpread(target)
            return true
        }
        if (pdfLock.lock == PdfLock.PAGES) return pdfLock.turn(forward)
        val visual = navigator.value?.visual ?: return false
        return if (forward) visual.goForward(animated = true) else visual.goBackward(animated = true)
    }

    private fun showSpread(page: Int) {
        spreadPage.value = page
        pdfPositions.getOrNull(page)?.let(::savePosition)
    }

    /**
     * Opens the book past its cover on the shut phone. The cover shows over the lock
     * screen, so it must never be a way past it: locked, this asks to unlock first.
     */
    private fun openFront() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            animateCover { coverOpenness.animateTo(0f, tween(220)) }
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = openFront()
                },
            )
            return
        }
        animateCover {
            coverOpenness.animateTo(1f, tween(COVER_SWING_MS))
            frontOpen.value = true
        }
    }

    /**
     * Runs a cover animation. Animating needs a frame clock, which only Compose's UI
     * dispatcher provides; plain lifecycleScope doesn't, and animateTo would crash.
     */
    private fun animateCover(block: suspend () -> Unit) {
        lifecycleScope.launch(AndroidUiDispatcher.Main) { block() }
    }

    private fun closeFront() {
        frontOpen.value = false
        controlsVisible.value = false
        animateCover { coverOpenness.animateTo(0f, tween(COVER_SWING_MS)) }
    }

    private fun shutCoverAtOnce() {
        frontOpen.value = false
        animateCover { coverOpenness.snapTo(0f) }
    }

    /** Locked: ask to unlock. Unlocked: a moment's hint of how to open the cover. */
    private fun onCoverTapped() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, null)
            return
        }
        lifecycleScope.launch {
            coverHint.value = true
            delay(2_500)
            coverHint.value = false
        }
    }

    private fun setBookMode(on: Boolean) {
        container.appScope.launch { container.library.setBookMode(session.book.id, on) }
    }

    @OptIn(FlowPreview::class)
    private fun savePositionAsYouRead() {
        lifecycleScope.launch {
            navigator.collect { nav ->
                nav?.visual?.currentLocator?.debounce(1_000)?.collect { locator ->
                    // While a PDF spread is up, the spread owns the position, not Readium.
                    if (spreadPage.value == null) savePosition(locator)
                }
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
        if (!::session.isInitialized) return
        // The debounce may still be holding the latest page; don't lose it.
        val spread = spreadPage.value
        if (spread != null) {
            pdfPositions.getOrNull(spread)?.let(::savePosition)
        } else {
            navigator.value?.visual?.currentLocator?.value?.let(::savePosition)
        }
    }

    override fun onDestroy() {
        if (::rearCover.isInitialized) rearCover.close()
        pdfLock.detach()
        pdfPages?.let { pages -> container.appScope.launch { pages.close() } }
        super.onDestroy()
        if (::session.isInitialized && isFinishing) container.openBooks.close(session.book.id)
    }

    private fun navigatorFactory(): FragmentFactory {
        val layout = resolveLayout(
            mode = session.initialPreferences.mode ?: ReadingMode.SPREAD,
            format = session.book.format,
            windowWidthDp = resources.configuration.screenWidthDp.toFloat(),
            tabletop = false,
            pageLock = session.initialPreferences.pageLock == true,
        )
        pdfLock.update(layout.pdfLock, wholePage = showsWholePage(session.initialPreferences, layout))
        return when (session.book.format) {
            BookFormat.EPUB -> EpubNavigatorFactory(session.publication).createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = session.initialPreferences.toEpubPreferences(layout, session.appTheme.palette.readingColors()),
                listener = object : EpubNavigatorFragment.Listener {
                    override fun onExternalLinkActivated(url: AbsoluteUrl) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    }
                },
                configuration = EpubNavigatorFragment.Configuration(readiumCssRsProperties = TWO_COLUMNS_ON_ANY_WIDE_SCREEN),
            )
            BookFormat.PDF -> PdfNavigatorFactory(
                session.publication,
                PdfiumEngineProvider(
                    listener = object : PdfiumEngineProvider.Listener {
                        override fun onConfigurePdfView(configurator: PDFView.Configurator) = pdfLock.configure(configurator)
                    },
                ),
            ).createFragmentFactory(
                initialLocator = session.initialLocator,
                initialPreferences = session.initialPreferences.toPdfiumPreferences(layout),
            )
        }
    }

    companion object {
        private const val EXTRA_BOOK_ID = "book_id"

        /** Same as Readium's tap zones: the outer 30% on each side. */
        private const val EDGE_FRACTION = 0.3f

        /** How long the cover takes to swing open or shut. */
        private const val COVER_SWING_MS = 320

        /** A release faster than this finishes the swing even if the drag was short. */
        private const val FLING_PX_PER_SECOND = 1200f

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

/**
 * ReadiumCSS only honours a two-column request on screens at least 60em (960 CSS px)
 * wide, and forces one column below that. The open Fold is 752 CSS px: plenty for two
 * columns, but under the cutoff, so a spread would silently stay one page. Inline
 * properties beat that stylesheet rule, so the column count is pointed straight at
 * the reader's request (two in a spread, one otherwise), and the minimum column width
 * that would otherwise squeeze it back to one is dropped. Scroll mode is untouched:
 * its own rule switches columns off entirely.
 */
private val TWO_COLUMNS_ON_ANY_WIDE_SCREEN = RsProperties(
    overrides = mapOf(
        "--RS__colCount" to "var(--USER__colCount, 1)",
        "--RS__colWidth" to "auto",
    ),
)

/** A locked page always shows whole; otherwise it's the reader's fit choice. */
private fun showsWholePage(prefs: ReadingPreferences, layout: ReaderLayout): Boolean =
    layout.pdfLock == PdfLock.PAGES || prefs.pdfFit == PdfFit.PAGE

/** One navigator, EPUB or PDF, behind the few operations the reader needs. */
class ReaderNavigator(val fragment: Fragment, val visual: OverflowableNavigator) {

    @Suppress("UNCHECKED_CAST")
    fun apply(prefs: ReadingPreferences, layout: ReaderLayout, appColors: ReadingColors) {
        when (fragment) {
            is EpubNavigatorFragment -> fragment.submitPreferences(prefs.toEpubPreferences(layout, appColors))
            is PdfNavigatorFragment<*, *> ->
                (fragment as PdfNavigatorFragment<PdfiumSettings, PdfiumPreferences>)
                    .submitPreferences(prefs.toPdfiumPreferences(layout))
        }
    }
}
