package com.preform.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.preform.reader.diagnostics.DiagnosticsActivity
import com.preform.reader.library.LibraryEvent
import com.preform.reader.library.LibraryScreen
import com.preform.reader.library.LibraryViewModel
import com.preform.reader.library.folderLabel
import com.preform.reader.reader.ReaderActivity
import com.preform.reader.settings.AppSettingsActions
import com.preform.reader.settings.AppSettingsScreen
import com.preform.reader.sync.PrivateLibraryScreen
import com.preform.reader.settings.AppSettingsState
import com.preform.reader.settings.AppTheme
import com.preform.reader.settings.CoverControls
import com.preform.reader.settings.ReadingDefaultsScreen
import com.preform.reader.settings.ReadingPreferences
import com.preform.reader.settings.SwipeDirection
import com.preform.reader.settings.ThemeSchedule
import com.preform.reader.ui.PreformTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** The library and app settings. Reading happens in [ReaderActivity]. */
class MainActivity : ComponentActivity() {

    /**
     * Books handed to us by other apps ("Open with Preform"). A buffered channel rather
     * than a flow: the launch intent arrives before the UI starts collecting, and each
     * book must be delivered exactly once.
     */
    private val incoming = Channel<Uri>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        val prefs = container.preferences

        setContent {
            // What's showing now (the day or night theme when switching by time), versus
            // the theme picked in settings.
            val effectiveTheme by prefs.effectiveTheme.collectAsStateWithLifecycle(AppTheme.DEFAULT)
            val appTheme by prefs.appTheme.collectAsStateWithLifecycle(AppTheme.DEFAULT)
            PreformTheme(effectiveTheme) {
                val library: LibraryViewModel = viewModel { LibraryViewModel(container) }
                val state by library.state.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    uri?.let(library::chooseFolder)
                }
                val pickBooks = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isNotEmpty()) library.import(uris)
                }
                // Some file providers label EPUBs as generic binaries; the importer checks the format itself.
                val bookTypes = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")

                LaunchedEffect(Unit) {
                    library.events.collect { event ->
                        when (event) {
                            is LibraryEvent.OpenReader -> startActivity(ReaderActivity.intent(this@MainActivity, event.bookId))
                            is LibraryEvent.Message -> snackbar.showSnackbar(event.text)
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    incoming.receiveAsFlow().collect { library.import(listOf(it), openAfter = true) }
                }

                NavHost(navController, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            state = state,
                            snackbar = snackbar,
                            onOpen = library::open,
                            onRemove = library::remove,
                            onDownloadAction = library::downloadAction,
                            onQueryChange = library::setQuery,
                            onSortChange = library::setSort,
                            onRefresh = library::refresh,
                            onChooseFolder = { pickFolder.launch(null) },
                            onAddBook = { pickBooks.launch(bookTypes) },
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        val keepScreenOn by prefs.keepScreenOn.collectAsStateWithLifecycle(false)
                        val schedule by prefs.themeSchedule.collectAsStateWithLifecycle(ThemeSchedule.DEFAULT)
                        val coverControls by prefs.coverControls.collectAsStateWithLifecycle(CoverControls.SWIPE_AND_TAP)
                        val swipeForNext by prefs.backSwipeForNext.collectAsStateWithLifecycle(SwipeDirection.LEFT)
                        val folder by prefs.libraryFolder.collectAsStateWithLifecycle(null)
                        val pc by container.sync.settings.connection.collectAsStateWithLifecycle(null)
                        AppSettingsScreen(
                            state = AppSettingsState(
                                folderLabel = folder?.let(::folderLabel),
                                privateLibrary = pc?.serverName,
                                appTheme = appTheme,
                                themeSchedule = schedule,
                                keepScreenOn = keepScreenOn,
                                coverControls = coverControls,
                                backSwipeForNext = swipeForNext,
                                showDiagnostics = BuildConfig.DEBUG,
                            ),
                            actions = AppSettingsActions(
                                onBack = { navController.popBackStack() },
                                onChooseFolder = { pickFolder.launch(null) },
                                onRescan = library::refresh,
                                onRemoveFolder = { scope.launch { container.library.clearLibraryFolder() } },
                                onPrivateLibrary = { navController.navigate("private-library") },
                                onThemeBase = { scope.launch { prefs.setThemeBase(it) } },
                                onThemeAccent = { scope.launch { prefs.setThemeAccent(it) } },
                                onScheduleChange = { change -> scope.launch { prefs.updateThemeSchedule(change) } },
                                onKeepScreenOn = { scope.launch { prefs.setKeepScreenOn(it) } },
                                onReadingDefaults = { navController.navigate("reading-defaults") },
                                onCoverControls = { scope.launch { prefs.setCoverControls(it) } },
                                onBackSwipeForNext = { scope.launch { prefs.setBackSwipeForNext(it) } },
                                onDiagnostics = {
                                    startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
                                },
                            ),
                        )
                    }
                    composable("private-library") {
                        PrivateLibraryScreen(sync = container.sync, onBack = { navController.popBackStack() })
                    }
                    composable("reading-defaults") {
                        val global by prefs.globalReading.collectAsStateWithLifecycle(ReadingPreferences())
                        ReadingDefaultsScreen(
                            prefs = ReadingPreferences.DEFAULTS overriddenBy global,
                            onChange = { change -> scope.launch { prefs.updateGlobalReading(change) } },
                            onReset = { scope.launch { prefs.resetGlobalReading() } },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { incoming.trySend(it) }
    }
}
