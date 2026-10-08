package com.preform.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.preform.desktop.LibraryBook
import com.preform.desktop.LibraryService
import com.preform.sync.Genres

/** Where the window is: a slice of the library, or the Phones or Settings page. */
sealed interface Place {
    data object All : Place
    data object Recent : Place
    data class Folder(val path: String) : Place
    data class Genre(val name: String) : Place
    data object Phones : Place
    data object Settings : Place
}

enum class SortOrder(val label: String) {
    TITLE("Title"),
    AUTHOR("Author"),
    ADDED("Recently added"),
    YEAR("Year"),
}

/** What the window remembers while it's open: where you are, what you've searched and selected. */
@Stable
class WindowState {
    var place by mutableStateOf<Place>(Place.All)
    var query by mutableStateOf("")
    var sort by mutableStateOf(SortOrder.TITLE)

    /** Selected books by content id. */
    var selection by mutableStateOf(emptySet<String>())

    /** Where a shift-click range starts. */
    var anchor by mutableStateOf<String?>(null)

    /** The pairing panel is showing (also opened from the tray's "Pair a phone"). */
    var pairing by mutableStateOf(false)

    fun go(to: Place) {
        place = to
        selection = emptySet()
        anchor = null
    }
}

/** The books [place] shows, searched and sorted. */
fun visibleBooks(books: List<LibraryBook>, place: Place, query: String, sort: SortOrder): List<LibraryBook> {
    val inPlace = when (place) {
        Place.All -> books
        Place.Recent -> books.sortedByDescending { it.catalog.addedAt }.take(RECENT_COUNT)
        is Place.Folder -> books.filter { it.catalog.folder == place.path || it.catalog.folder.startsWith(place.path + "/") }
        is Place.Genre -> books.filter { book ->
            if (place.name == Genres.UNCATEGORIZED) book.catalog.genres.isEmpty() else place.name in book.catalog.genres
        }
        Place.Phones, Place.Settings -> emptyList()
    }
    val searched = if (query.isBlank()) inPlace else {
        val q = query.trim()
        inPlace.filter { b ->
            b.catalog.title.contains(q, true) || b.catalog.author?.contains(q, true) == true ||
                b.relativePath.contains(q, true)
        }
    }
    if (place == Place.Recent && query.isBlank()) return searched
    return when (sort) {
        SortOrder.TITLE -> searched.sortedBy { sortableTitle(it.catalog.title) }
        SortOrder.AUTHOR -> searched.sortedWith(compareBy<LibraryBook> { it.catalog.author?.let(::surnameFirst) ?: "￿" }.thenBy { sortableTitle(it.catalog.title) })
        SortOrder.ADDED -> searched.sortedByDescending { it.catalog.addedAt }
        SortOrder.YEAR -> searched.sortedWith(compareBy<LibraryBook> { it.catalog.year ?: Int.MAX_VALUE }.thenBy { sortableTitle(it.catalog.title) })
    }
}

private const val RECENT_COUNT = 60

/** "The Hobbit" files under H. */
fun sortableTitle(title: String) = title.lowercase().removePrefix("the ").removePrefix("a ").removePrefix("an ")

/** "Frank Herbert" sorts as "herbert frank". */
private fun surnameFirst(author: String): String {
    val first = author.split(',', '&', ';').first().trim()
    return if (',' in author) first.lowercase() else first.split(' ').let { (it.takeLast(1) + it.dropLast(1)).joinToString(" ") }.lowercase()
}

@Composable
fun LibraryWindow(service: LibraryService, covers: CoverCache, state: WindowState) {
    val books by service.books.collectAsState()
    val folders by service.folders.collectAsState()
    val status by service.status.collectAsState()
    val activity by service.activity.collectAsState()
    val config by service.configFlow.collectAsState()

    Row(Modifier.fillMaxSize().background(colors.background)) {
        Sidebar(
            state = state,
            books = books,
            folders = folders,
            status = status,
            activity = activity,
            modifier = Modifier.width(248.dp).fillMaxHeight(),
        )
        VerticalDivider(color = colors.line)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (val place = state.place) {
                Place.Phones -> PhonesScreen(service, state)
                Place.Settings -> SettingsScreen(service, config, status)
                else -> {
                    val shown = remember(books, place, state.query, state.sort) { visibleBooks(books, place, state.query, state.sort) }
                    Row(Modifier.fillMaxSize()) {
                        BookGrid(
                            state = state,
                            books = shown,
                            covers = covers,
                            libraryEmpty = books.isEmpty(),
                            libraryFound = status.libraryFound,
                            onRescan = service::rescanSoon,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        VerticalDivider(color = colors.line)
                        BookDetails(
                            service = service,
                            covers = covers,
                            selected = books.filter { it.catalog.contentId in state.selection },
                            shown = shown,
                            place = place,
                            modifier = Modifier.width(320.dp).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
