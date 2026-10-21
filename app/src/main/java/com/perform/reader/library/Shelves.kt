package com.perform.reader.library

import com.perform.sync.Genres

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
 * [folders] adds folders with nothing in them yet, as empty shelves. [folderOf] says
 * where a book sits (null: not in this tree), so one library's shelves can be drawn
 * from books several libraries share; [keyPrefix] marks the shelves as that library's.
 */
fun folderPage(
    books: List<BookEntity>,
    path: String,
    folders: Collection<String> = emptyList(),
    keyPrefix: String = "",
    folderOf: (BookEntity) -> String? = { it.shelf },
): ShelfPage {
    val prefix = if (path.isEmpty()) "" else "$path/"
    val placed = books.mapNotNull { book -> folderOf(book)?.let { book to it } }
    val withBooks = placed
        .filter { (_, folder) -> folder != path && folder.startsWith(prefix) }
        .groupBy({ (_, folder) -> folder.removePrefix(prefix).substringBefore('/') }, { it.first })
    val empty = folders
        .filter { it.startsWith(prefix) && it != path }
        .map { it.removePrefix(prefix).substringBefore('/') }
        .filter { it.isNotEmpty() && it !in withBooks }
        .distinct()
    val shelves = (withBooks.map { (name, onShelf) -> Shelf(key = keyPrefix + prefix + name, name = name, books = onShelf) } +
        empty.map { Shelf(key = keyPrefix + prefix + it, name = it, books = emptyList()) })
        .sortedBy { it.name.lowercase() }
    return ShelfPage(shelves, loose = placed.filter { it.second == path }.map { it.first })
}

/** One library's part of the Shelves page when the phone has several. "" is the phone's own books. */
data class ShelfSection(
    val library: String,
    val name: String,
    val online: Boolean,
    /** That library's top level: its folders as shelves, and its books outside any folder. */
    val page: ShelfPage,
    val count: Int,
)

/** Every folder for "Move to": the folders given and their parents, once each. */
fun allFolders(paths: Collection<String>): List<String> =
    paths.flatMap { LibraryOrganizer.prefixes(it) }.distinct().sortedWith(compareBy { it.lowercase() })

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
