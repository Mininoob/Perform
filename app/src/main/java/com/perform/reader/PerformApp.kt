package com.perform.reader

import android.app.Application
import android.content.Context
import com.perform.reader.library.CoverExtractor
import com.perform.reader.library.LibraryDatabase
import com.perform.reader.library.LibraryOrganizer
import com.perform.reader.library.LibraryRepository
import com.perform.reader.reader.OpenBooks
import com.perform.reader.reader.ReadiumServices
import com.perform.reader.settings.PreferencesStore
import com.perform.reader.sync.LibrarySync
import com.perform.reader.sync.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PerformApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Moves, uploads and keep-every-book carry on when the app isn't open.
        com.perform.reader.sync.LibraryWorker.schedule(this)
    }
}

/**
 * The app's object graph, built once per process. Plain constructor wiring: the graph
 * is small enough that a DI framework would cost more than it saves.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    /** For writes that must outlive a screen, like saving the reading position as the reader closes. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferences = PreferencesStore(context)
    val readium = ReadiumServices(context)
    private val database = LibraryDatabase.create(context)
    private val covers = CoverExtractor(context)
    val sync = LibrarySync(context, database.books(), SyncSettings(context), covers)
    val library = LibraryRepository(
        context = context,
        dao = database.books(),
        readium = readium,
        covers = covers,
        preferences = preferences,
    )
    val openBooks = OpenBooks()

    /** Folders made, renamed and removed here, and books moved; the PC follows on the next sync. */
    val organizer = LibraryOrganizer(database.books(), paired = { sync.settings.connectionsNow().map { it.serverId }.toSet() })
    val folders = database.books().observeFolders()

    /** Which paired libraries have which PC books, and their folder in each. */
    val memberships = database.books().observeMemberships()
}

val Context.container: AppContainer
    get() = (applicationContext as PerformApp).container
