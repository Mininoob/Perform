package com.preform.reader.library

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.preform.reader.AppContainer
import com.preform.reader.ui.TypeCoverBitmap
import com.preform.reader.reader.ReaderSession
import com.preform.reader.settings.ReadingPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

enum class LibrarySort(val label: String) {
    RECENT("Recently read"),
    TITLE("Title"),
    AUTHOR("Author"),
    ADDED("Recently added"),
}

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    /** The most recently read book; shown as the hero when not searching. */
    val continueReading: BookEntity? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val folderLabel: String? = null,
    val scan: ScanState = ScanState.Idle,
    /** The book currently being opened, for a spinner on its cover. */
    val opening: Long? = null,
    /** PC books downloading, by id, with progress 0..1. */
    val downloads: Map<Long, Float> = emptyMap(),
    /** A pull-to-refresh is running: the folder scan and the PC sync. */
    val refreshing: Boolean = false,
    val view: LibraryView = LibraryView.SHELVES,
    /** Inside a shelf: its folder path or genre. Null at the top. */
    val location: String? = null,
    /** Shelves to show; null when showing the plain grid (All books, or searching). */
    val page: ShelfPage? = null,
    /** False until the first database read, so an empty library isn't flashed on launch. */
    val loaded: Boolean = false,
    /** Every folder there is, for "Move to" (the current library's, inside one). */
    val folders: List<String> = emptyList(),
    /** Books picked in selection mode; empty when not selecting. */
    val selection: Set<Long> = emptySet(),
    /** The paired libraries. */
    val libraries: List<LibraryInfo> = emptyList(),
    /** At the top of Shelves with several libraries: one section each, then the phone's own books. */
    val sections: List<ShelfSection>? = null,
    /** The page title inside a shelf, when it isn't just the folder's name (a library's own top). */
    val locationTitle: String? = null,
    /** Inside one library's shelves: which (for new folders and moves there). */
    val locationLibrary: String? = null,
    /** For each PC book, the libraries that have it. */
    val owners: Map<Long, List<String>> = emptyMap(),
) {
    /** Several libraries: shelves are grouped by library and keys carry the library. */
    val grouped: Boolean get() = libraries.size > 1
}

/** A paired library as the library screen shows it. */
data class LibraryInfo(val id: String, val name: String, val online: Boolean)

sealed interface LibraryEvent {
    data class OpenReader(val bookId: Long) : LibraryEvent
    data class Message(val text: String) : LibraryEvent
}

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val library = container.library
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.RECENT)
    private val opening = MutableStateFlow<Long?>(null)
    private val refreshing = MutableStateFlow(false)

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    private val view = MutableStateFlow(LibraryView.SHELVES)
    private val location = MutableStateFlow<String?>(null)

    /** The books, and where each sits in each library. */
    private class Shelving(
        val books: List<BookEntity>,
        val libraries: List<LibraryInfo>,
        /** Book id → library id → folder there. */
        val folderIn: Map<Long, Map<String, String>>,
        /** Library id → folders made there, empty ones included. */
        val made: Map<String, List<String>>,
    ) {
        val grouped get() = libraries.size > 1
        val only: String get() = libraries.singleOrNull()?.id ?: LibraryOrganizer.PHONE

        /** Where [book] sits in [library] ("" = the phone's own books); null when not there. */
        fun folderOf(book: BookEntity, library: String): String? = when {
            book.source != BookSource.SERVER -> book.shelf.takeIf { library == LibraryOrganizer.PHONE || !grouped }
            else -> folderIn[book.id]?.get(library)
        }

        /** One tree for everything (a single library): a PC book where its library has it. */
        fun merged(book: BookEntity): String = if (book.source != BookSource.SERVER) book.shelf else folderIn[book.id]?.values?.firstOrNull() ?: book.shelf

        fun foldersOf(library: String): List<String> = if (grouped) made[library].orEmpty() else made.values.flatten()
    }

    private data class Listing(
        val shelving: Shelving,
        val query: String,
        val sort: LibrarySort,
        val view: LibraryView,
        val location: String?,
    )

    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val libraries = combine(container.sync.settings.connections, container.sync.statuses) { connections, statuses ->
        connections.map { LibraryInfo(it.serverId, it.serverName, statuses[it.serverId] !is com.preform.reader.sync.SyncStatus.Offline) }
    }

    private val shelved = combine(library.books, container.folders, container.memberships, libraries) { books, folders, members, libs ->
        Shelving(
            books = books,
            libraries = libs,
            folderIn = members.groupBy { it.bookId }.mapValues { (_, m) -> m.associate { it.serverId to it.folder } },
            made = folders.filter { !it.removed }.groupBy({ it.serverId }, { it.path }),
        )
    }

    private val listing = combine(shelved, query, sort, view, location) { sh, q, s, v, l -> Listing(sh, q, s, v, l) }

    /** The latest shelving, for parsing shelf keys outside the state pipeline. */
    @Volatile
    private var current: Shelving? = null

    /** A shelf key or location: "<library>|<path>" when grouped, else the path in the one library. */
    private fun parse(key: String): Pair<String, String> =
        if ('|' in key) key.substringBefore('|') to key.substringAfter('|') else (current?.only ?: LibraryOrganizer.PHONE) to key

    val state: StateFlow<LibraryUiState> = combine(
        listing,
        container.preferences.libraryFolder,
        library.scanState,
        opening,
        combine(container.sync.downloads, refreshing, selection) { downloads, busy, picked -> Triple(downloads, busy, picked) },
    ) { listed, folder, scan, openingId, (downloads, busy, picked) ->
        val (sh, q, s, v, l) = listed
        current = sh
        val books = sh.books
        val filtered = if (q.isBlank()) books else books.filter {
            it.title.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true
        }
        val sorted = filtered.sortedWith(s.comparator())
        val names = sh.libraries.associate { it.id to it.name }
        // Grouped: the top shows a section per library; inside, one library's shelves.
        val (lib, path) = if (sh.grouped && l != null && v == LibraryView.SHELVES) l.substringBefore('|') to l.substringAfter('|') else null to l
        val sections = if (sh.grouped && v == LibraryView.SHELVES && l == null && q.isBlank()) {
            val phone = sorted.filter { it.source != BookSource.SERVER }
            sh.libraries.map { info ->
                val page = folderPage(sorted, "", sh.foldersOf(info.id), "${info.id}|") { sh.folderOf(it, info.id) }
                ShelfSection(info.id, info.name, info.online, page, sorted.count { sh.folderOf(it, info.id) != null })
            } + listOfNotNull(
                phone.takeIf { it.isNotEmpty() }?.let {
                    ShelfSection(LibraryOrganizer.PHONE, "On this phone", true, folderPage(it, "", sh.foldersOf(LibraryOrganizer.PHONE), "|"), it.size)
                },
            )
        } else {
            null
        }
        LibraryUiState(
            books = sorted,
            view = v,
            location = l,
            page = when {
                q.isNotBlank() -> null
                sections != null -> null
                v == LibraryView.SHELVES && lib != null ->
                    folderPage(sorted, path.orEmpty(), sh.foldersOf(lib), "$lib|") { sh.folderOf(it, lib) }
                v == LibraryView.SHELVES -> folderPage(sorted, l ?: "", sh.foldersOf(sh.only)) { sh.merged(it) }
                v == LibraryView.GENRES -> genrePage(sorted, l)
                else -> null
            },
            sections = sections,
            libraries = sh.libraries,
            locationLibrary = lib ?: sh.only.takeIf { v == LibraryView.SHELVES && !sh.grouped },
            locationTitle = if (lib != null && path.isNullOrEmpty()) names[lib] ?: "On this phone" else null,
            owners = sh.folderIn.mapValues { (_, byLibrary) -> byLibrary.keys.mapNotNull { names[it] } },
            continueReading = books.filter { it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt!! },
            query = q,
            sort = s,
            folderLabel = folder?.let(::folderLabel),
            scan = scan,
            opening = openingId,
            downloads = downloads,
            refreshing = busy,
            loaded = true,
            folders = allFolders(
                if (lib != null) books.mapNotNull { sh.folderOf(it, lib) } + sh.foldersOf(lib)
                else books.map { sh.merged(it) } + sh.made.values.flatten(),
            ),
            selection = picked.filterTo(HashSet()) { id -> books.any { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        refresh()
        viewModelScope.launch { container.sync.notices.collect { _events.send(LibraryEvent.Message(it)) } }
        viewModelScope.launch {
            container.preferences.libraryView.first()
                ?.let { saved -> LibraryView.entries.firstOrNull { it.name == saved } }
                ?.let { view.value = it }
        }
    }

    fun setView(value: LibraryView) {
        view.value = value
        location.value = null
        viewModelScope.launch { container.preferences.setLibraryView(value.name) }
    }

    /** Opens a shelf: a folder path or a genre. */
    fun enterShelf(key: String) {
        location.value = key
    }

    /** Back out of a shelf: to the parent folder, then the library's own top when grouped, then the top. */
    fun leaveShelf() {
        val here = location.value ?: return
        if (view.value != LibraryView.SHELVES) {
            location.value = null
            return
        }
        location.value = if ('|' in here) {
            val path = here.substringAfter('|')
            if (path.isEmpty()) null else here.substringBefore('|') + "|" + path.substringBeforeLast('/', "")
        } else {
            here.substringBeforeLast('/', "").ifEmpty { null }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    launch {
                        library.syncFolder().onFailure {
                            _events.send(LibraryEvent.Message("Couldn't read the library folder. Choose it again in Settings."))
                        }
                    }
                    // The PC library syncs alongside; being away from it is normal, so no message.
                    launch {
                        if (container.sync.sync().isSuccess) {
                            // Then the phone's own books go to the PC, once each.
                            container.sync.hashLocalBooks()
                            runCatching { container.sync.uploadLocalBooks() }
                        }
                    }
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    fun downloadAction(book: BookEntity, action: DownloadAction) {
        viewModelScope.launch {
            when (action) {
                DownloadAction.PIN -> {
                    container.sync.setPinned(book, true)
                    // "Keep downloaded" on a book not yet on the phone fetches it now.
                    if (!book.downloaded) {
                        container.sync.download(book).onFailure { _events.send(LibraryEvent.Message(it.message ?: "Download failed.")) }
                    }
                }
                DownloadAction.UNPIN -> container.sync.setPinned(book, false)
                DownloadAction.REMOVE_DOWNLOAD -> container.sync.removeDownload(book)
            }
        }
    }

    // Organizing: shown at once, sent to the PC in the background.

    /** Moves books within the library being browsed, or, from Genres or All books, in every library each is in. */
    fun moveBooks(books: List<BookEntity>, folder: String) = organize {
        val moved = container.organizer.move(books, folder, state.value.locationLibrary)
        selection.value = emptySet()
        if (moved > 0) {
            val where = if (folder.isEmpty()) "the top of the library" else "“${folder.substringAfterLast('/')}”"
            _events.send(LibraryEvent.Message(if (moved == 1) "Moved to $where" else "Moved $moved books to $where"))
        }
    }

    /**
     * Makes a folder in [parent] of [library] (the one being browsed when null); with
     * [thenMove], moves those books into it.
     */
    fun makeFolder(parent: String, name: String, thenMove: List<BookEntity> = emptyList(), library: String? = null) = organize {
        val lib = library ?: state.value.locationLibrary ?: LibraryOrganizer.PHONE
        val path = container.organizer.makeFolder(lib, parent, name)
        if (thenMove.isNotEmpty()) {
            container.organizer.move(thenMove, path, lib)
            selection.value = emptySet()
        }
    }

    /** Renames the folder a shelf key names. */
    fun renameFolder(key: String, name: String) = organize {
        val (lib, path) = parse(key)
        val renamed = container.organizer.renameFolder(lib, path, name, withPhone = current?.grouped == false)
        location.value?.let { here ->
            val (hereLib, herePath) = parse(here)
            if (hereLib == lib && (herePath == path || herePath.startsWith("$path/"))) {
                location.value = (if ('|' in here) "$lib|" else "") + renamed + herePath.removePrefix(path)
            }
        }
    }

    /** Removes the folder a shelf key names; its books move up a level. */
    fun removeFolder(key: String) = organize {
        val (lib, path) = parse(key)
        container.organizer.removeFolder(lib, path, withPhone = current?.grouped == false)
        location.value?.let { here ->
            val (hereLib, herePath) = parse(here)
            if (hereLib == lib && (herePath == path || herePath.startsWith("$path/"))) leaveShelf()
        }
    }

    fun select(book: BookEntity) {
        selection.value = selection.value + book.id
    }

    fun toggleSelected(book: BookEntity) {
        selection.value = if (book.id in selection.value) selection.value - book.id else selection.value + book.id
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    private fun organize(work: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                work()
                // Send it now rather than at the next refresh; quietly retried later if the PC is away.
                launch { container.sync.sync() }
            } catch (e: OrganizeError) {
                _events.send(LibraryEvent.Message(e.message ?: "That didn't work."))
            }
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setSort(value: LibrarySort) {
        sort.value = value
    }

    fun chooseFolder(treeUri: Uri) {
        viewModelScope.launch {
            library.setLibraryFolder(treeUri).onFailure {
                _events.send(LibraryEvent.Message("Couldn't read that folder."))
            }
        }
    }

    /** Adds books picked with "Add book". A single book opens straight away when [openAfter]. */
    fun import(uris: List<Uri>, openAfter: Boolean = false) {
        viewModelScope.launch {
            val results = uris.map { library.import(it) }
            val failures = results.count { it.isFailure }
            if (failures > 0) {
                val reason = results.first { it.isFailure }.exceptionOrNull()?.message
                _events.send(
                    LibraryEvent.Message(
                        if (uris.size == 1) reason ?: "That file couldn't be added." else "$failures of ${uris.size} files couldn't be added.",
                    ),
                )
            }
            if (openAfter && uris.size == 1) {
                results.single().getOrNull()?.let { openById(it) }
            }
        }
    }

    fun remove(book: BookEntity) {
        viewModelScope.launch { library.remove(book) }
    }

    fun openById(id: Long) {
        viewModelScope.launch { library.get(id)?.let(::open) }
    }

    /**
     * Does the slow part of opening before the reader starts: opens the publication,
     * resolves settings and decodes the cover, then hands it all over in one session.
     */
    fun open(book: BookEntity) {
        if (opening.value != null) return
        opening.value = book.id
        viewModelScope.launch {
            try {
                // A PC book is fetched the first time it's opened.
                val book = if (book.downloaded) book else {
                    container.sync.download(book).getOrElse {
                        _events.send(LibraryEvent.Message(it.message ?: "“${book.title}” couldn't be downloaded."))
                        return@launch
                    }
                }
                val publication = container.readium.open(book.readableUri!!).getOrElse {
                    _events.send(LibraryEvent.Message("“${book.title}” couldn't be opened. The file may have moved or be damaged."))
                    return@launch
                }
                val preferences = ReadingPreferences.resolve(
                    global = container.preferences.globalReading.first(),
                    book = ReadingPreferences.fromJson(book.prefsOverrideJson),
                )
                val locator = book.locatorJson?.let { json ->
                    runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
                }
                // A book without a cover wears the series' typeset jacket in Book Mode too.
                val cover = withContext(Dispatchers.IO) {
                    book.coverFullPath?.let(BitmapFactory::decodeFile)
                        ?: TypeCoverBitmap.render(container.appContext, book.title, book.author, book.genreList)
                }
                val appTheme = container.preferences.effectiveTheme.first()
                container.openBooks.put(ReaderSession(book, publication, locator, preferences, cover, appTheme))
                library.markOpened(book.id)
                _events.send(LibraryEvent.OpenReader(book.id))
            } finally {
                opening.value = null
            }
        }
    }
}

private fun LibrarySort.comparator(): Comparator<BookEntity> = when (this) {
    LibrarySort.RECENT -> compareByDescending<BookEntity> { it.lastOpenedAt ?: 0L }.thenByDescending { it.addedAt }
    LibrarySort.TITLE -> compareBy { it.title.lowercase() }
    LibrarySort.AUTHOR -> compareBy<BookEntity> { it.author?.lowercase() ?: "￿" }.thenBy { it.title.lowercase() }
    LibrarySort.ADDED -> compareByDescending { it.addedAt }
}
