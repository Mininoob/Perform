package com.preform.reader.library

import com.preform.sync.Genres

/** How the library is browsed. */
enum class LibraryView(val label: String) {
    /** Folders as bookshelves, drilling into sub-folders. */
    SHELVES("Shelves"),
    GENRES("Genres"),
    ALL("All books"),
}

/** One shelf: a folder or a genre, and every book on it (sub-folders included). */
data class Shelf(
    /** What to open: the folder path or the genre name. */
    val key: String,
    val name: String,
    val books: List<BookEntity>,
)

/** What one level of browsing shows: shelves as rows, then the books sitting loose here. */
data class ShelfPage(
    val shelves: List<Shelf>,
    val loose: List<BookEntity>,
)

/**
 * The shelves inside folder [path] ("" for the top): one per sub-folder, holding every
 * book anywhere below it, plus the books directly in [path]. Books keep the order given.
 * [folders] adds folders with nothing in them yet, as empty shelves.
 */
fun folderPage(books: List<BookEntity>, path: String, folders: Collection<String> = emptyList()): ShelfPage {
    val prefix = if (path.isEmpty()) "" else "$path/"
    val withBooks = books
        .filter { it.shelf != path && it.shelf.startsWith(prefix) }
        .groupBy { it.shelf.removePrefix(prefix).substringBefore('/') }
    val empty = folders
        .filter { it.startsWith(prefix) && it != path }
        .map { it.removePrefix(prefix).substringBefore('/') }
        .filter { it.isNotEmpty() && it !in withBooks }
        .distinct()
    val shelves = (withBooks.map { (name, onShelf) -> Shelf(key = prefix + name, name = name, books = onShelf) } +
        empty.map { Shelf(key = prefix + it, name = it, books = emptyList()) })
        .sortedBy { it.name.lowercase() }
    return ShelfPage(shelves, loose = books.filter { it.shelf == path })
}

/** Every folder, for "Move to": shelves books sit on, their parents, and empty folders. */
fun allFolders(books: List<BookEntity>, folders: Collection<String>): List<String> =
    (books.flatMap { LibraryOrganizer.prefixes(it.shelf) } + folders.flatMap { LibraryOrganizer.prefixes(it) })
        .distinct()
        .sortedWith(compareBy { it.lowercase() })

/**
 * At the top ([genre] null): a shelf per genre, fullest first, books without a genre on
 * a last "Uncategorized" shelf. A book with two genres sits on both. Inside a genre:
 * its books.
 */
fun genrePage(books: List<BookEntity>, genre: String?): ShelfPage {
    fun genresOf(book: BookEntity) = book.genreList.ifEmpty { listOf(Genres.UNCATEGORIZED) }
    if (genre != null) return ShelfPage(emptyList(), books.filter { genre in genresOf(it) })

    val byGenre = LinkedHashMap<String, MutableList<BookEntity>>()
    books.forEach { book -> genresOf(book).forEach { byGenre.getOrPut(it) { mutableListOf() } += book } }
    val shelves = byGenre
        .map { (name, onShelf) -> Shelf(key = name, name = name, books = onShelf) }
        .sortedWith(compareBy<Shelf> { it.name == Genres.UNCATEGORIZED }.thenByDescending { it.books.size }.thenBy { it.name })
    return ShelfPage(shelves, loose = emptyList())
}

/** "Fiction/Fantasy" → "Fiction › Fantasy", for the header while browsing inside a shelf. */
fun shelfTitle(key: String): String = key.split('/').joinToString(" › ")
