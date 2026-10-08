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
    /** Every folder there is, for "Move to". */
    val folders: List<String> = emptyList(),
    /** Books picked in selection mode; empty when not selecting. */
    val selection: Set<Long> = emptySet(),
)

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

    private data class Listing(
        val books: List<BookEntity>,
        val query: String,
        val sort: LibrarySort,
        val view: LibraryView,
        val location: String?,
        val folders: List<String>,
    )

    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    /** Books with the folders that have nothing in them yet. */
    private val shelved = combine(library.books, container.folders) { books, folders ->
        books to folders.filter { !it.removed }.map { it.path }
    }

    private val listing = combine(shelved, query, sort, view, location) { (books, folders), q, s, v, l -> Listing(books, q, s, v, l, folders) }

    val state: StateFlow<LibraryUiState> = combine(
        listing,
        container.preferences.libraryFolder,
        library.scanState,
        opening,
        combine(container.sync.downloads, refreshing, selection) { downloads, busy, picked -> Triple(downloads, busy, picked) },
    ) { listed, folder, scan, openingId, (downloads, busy, picked) ->
        val (books, q, s, v, l) = listed
        val folders = listed.folders
        val filtered = if (q.isBlank()) books else books.filter {
            it.title.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true
        }
        val sorted = filtered.sortedWith(s.comparator())
        LibraryUiState(
            books = sorted,
            view = v,
            location = l,
            page = when {
                q.isNotBlank() -> null
                v == LibraryView.SHELVES -> folderPage(sorted, l ?: "", folders)
                v == LibraryView.GENRES -> genrePage(sorted, l)
                else -> null
            },
            continueReading = books.filter { it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt!! },
            query = q,
            sort = s,
            folderLabel = folder?.let(::folderLabel),
            scan = scan,
            opening = openingId,
            downloads = downloads,
            refreshing = busy,
            loaded = true,
            folders = allFolders(books, folders),
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

    /** Back out of a shelf: to the parent folder, or to the top. */
    fun leaveShelf() {
        val here = location.value ?: return
        location.value = if (view.value == LibraryView.SHELVES) here.substringBeforeLast('/', "").ifEmpty { null } else null
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
                    launch { container.sync.sync() }
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

    fun moveBooks(books: List<BookEntity>, folder: String) = organize {
        val moved = container.organizer.move(books, folder)
        selection.value = emptySet()
        if (moved > 0) {
            val where = if (folder.isEmpty()) "the top of the library" else "“${folder.substringAfterLast('/')}”"
            _events.send(LibraryEvent.Message(if (moved == 1) "Moved to $where" else "Moved $moved books to $where"))
        }
    }

    /** Makes a folder in [parent]; with [thenMove], moves those books into it. */
    fun makeFolder(parent: String, name: String, thenMove: List<BookEntity> = emptyList()) = organize {
        val path = container.organizer.makeFolder(parent, name)
        if (thenMove.isNotEmpty()) {
            container.organizer.move(thenMove, path)
            selection.value = emptySet()
        }
    }

    fun renameFolder(path: String, name: String) = organize {
        val renamed = container.organizer.renameFolder(path, name)
        location.value?.let { here ->
            if (here == path || here.startsWith("$path/")) location.value = renamed + here.removePrefix(path)
        }
    }

    fun removeFolder(path: String) = organize {
        container.organizer.removeFolder(path)
        location.value?.let { here -> if (here == path || here.startsWith("$path/")) location.value = path.substringBeforeLast('/', "").ifEmpty { null } }
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
