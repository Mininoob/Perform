package com.preform.reader

import android.app.Application
import android.content.Context
import com.preform.reader.library.CoverExtractor
import com.preform.reader.library.LibraryDatabase
import com.preform.reader.library.LibraryOrganizer
import com.preform.reader.library.LibraryRepository
import com.preform.reader.reader.OpenBooks
import com.preform.reader.reader.ReadiumServices
import com.preform.reader.settings.PreferencesStore
import com.preform.reader.sync.LibrarySync
import com.preform.reader.sync.SyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PreformApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Moves, uploads and keep-every-book carry on when the app isn't open.
        com.preform.reader.sync.LibraryWorker.schedule(this)
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
    val organizer = LibraryOrganizer(database.books(), paired = { sync.settings.connectionNow() != null })
    val folders = database.books().observeFolders()
}

val Context.container: AppContainer
    get() = (applicationContext as PreformApp).container
