package com.preform.reader.library

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.preform.reader.AppContainer
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
    /** False until the first database read, so an empty library isn't flashed on launch. */
    val loaded: Boolean = false,
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

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    private val listing = combine(library.books, query, sort) { books, q, s -> Triple(books, q, s) }

    val state: StateFlow<LibraryUiState> = combine(
        listing,
        container.preferences.libraryFolder,
        library.scanState,
        opening,
    ) { (books, q, s), folder, scan, openingId ->
        val filtered = if (q.isBlank()) books else books.filter {
            it.title.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true
        }
        LibraryUiState(
            books = filtered.sortedWith(s.comparator()),
            continueReading = books.filter { it.lastOpenedAt != null }.maxByOrNull { it.lastOpenedAt!! },
            query = q,
            sort = s,
            folderLabel = folder?.let(::folderLabel),
            scan = scan,
            opening = openingId,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            library.syncFolder().onFailure {
                _events.send(LibraryEvent.Message("Couldn't read the library folder. Choose it again in Settings."))
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
                val publication = container.readium.open(book.uri).getOrElse {
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
                val cover = book.coverFullPath?.let { path ->
                    withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
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
