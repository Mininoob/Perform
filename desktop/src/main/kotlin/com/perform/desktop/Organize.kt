package com.perform.desktop

import com.perform.sync.Genres
import com.perform.sync.SameBook

/** How auto-organize lays the library out in folders. */
enum class OrganizeScheme(val label: String, val example: String) {
    BY_GENRE("By genre", "Fantasy/The Hobbit.epub"),
    BY_AUTHOR("By author", "J.R.R. Tolkien/The Hobbit.epub"),
    GENRE_AUTHOR("Genre, then author", "Fantasy/J.R.R. Tolkien/The Hobbit.epub"),
    AUTHOR_SERIES("Author, then series", "Frank Herbert/Dune Chronicles/Dune.epub"),
}

/** One book's proposed new place, both as library-relative paths. */
data class PlannedMove(val contentId: String, val title: String, val from: String, val to: String)

/**
 * Where each book would go: into folders by [scheme] (or left where it is when null),
 * renamed to "Author - Title" when [rename]. Everything lands under [base] (a folder, or
 * "" for the whole library). Names never collide: with each other, or with [occupied]
 * paths that aren't moving. Books already in place aren't listed.
 */
fun planOrganize(
    books: List<LibraryBook>,
    scheme: OrganizeScheme?,
    rename: Boolean,
    base: String = "",
    occupied: Set<String> = emptySet(),
): List<PlannedMove> {
    val moving = books.map { it.relativePath.lowercase() }.toSet()
    // Paths that stay put (other files, and books not part of this plan) can't be reused.
    val taken = occupied.map { it.lowercase() }.filter { it !in moving }.toMutableSet()
    val plan = mutableListOf<PlannedMove>()

    // Stable order, so the same library always gets the same plan.
    for (book in books.sortedBy { it.relativePath.lowercase() }) {
        val c = book.catalog
        val ext = Library.extensionOf(c.format)
        val folder = if (scheme == null) c.folder else join(base, folderFor(book, scheme))
        val name = if (rename) Library.safeFileName(fileTitle(book), c.format) else book.relativePath.substringAfterLast('/')

        var target = join(folder, name)
        var n = 2
        while (target.lowercase() in taken) {
            target = join(folder, "${name.substringBeforeLast('.')} ($n).$ext")
            n++
        }
        taken += target.lowercase()
        if (target != book.relativePath) plan += PlannedMove(c.contentId, c.title, book.relativePath, target)
    }
    return plan
}

private fun folderFor(book: LibraryBook, scheme: OrganizeScheme): String {
    val c = book.catalog
    val genre = Library.safeFolderNameOrNull(c.genres.firstOrNull()) ?: Genres.UNCATEGORIZED
    val author = Library.safeFolderNameOrNull(c.author?.let(OpenLibrary::firstAuthor)) ?: "Unknown Author"
    val series = Library.safeFolderNameOrNull(c.series)
    return when (scheme) {
        OrganizeScheme.BY_GENRE -> genre
        OrganizeScheme.BY_AUTHOR -> author
        OrganizeScheme.GENRE_AUTHOR -> "$genre/$author"
        OrganizeScheme.AUTHOR_SERIES -> if (series != null) "$author/$series" else author
    }
}

/** "Frank Herbert - Dune", or just the title when the author isn't known. */
private fun fileTitle(book: LibraryBook): String {
    val c = book.catalog
    val number = c.seriesIndex?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
    val title = if (c.series != null && number != null) "${c.title} (${c.series} $number)" else c.title
    return c.author?.let { "${OpenLibrary.firstAuthor(it)} - $title" } ?: title
}

private fun join(folder: String, name: String) = if (folder.isEmpty()) name else "$folder/$name"

/**
 * Genres a book probably belongs in, from what the library already says about it, best
 * first: the genres of the author's other books here, a folder named exactly like a genre
 * (custom ones included), then what its folder names, title and series suggest. Only ever
 * offered: the sorter puts them first, auto genre shows them for review.
 */
fun guessGenres(book: LibraryBook, hints: GenreHints = GenreHints.NONE, max: Int = 3): List<String> {
    val c = book.catalog
    val folders = c.folder.split('/').filter(String::isNotBlank)
    val byAuthor = SameBook.authorKey(c.author)?.let { hints.byAuthor[it] }.orEmpty()
    val named = folders.asReversed().mapNotNull { f -> hints.genres.firstOrNull { it.equals(f, ignoreCase = true) } }
    val clues = Genres.classify(folders + listOfNotNull(c.title, c.series), max)
    val all = (byAuthor + named + clues).distinct()
    // Plain "Fiction" only when nothing more specific turned up, as in Genres.classify.
    return all.filter { it != "Fiction" }.ifEmpty { all }.take(max)
}

/** What the rest of the library says, for [guessGenres]. */
class GenreHints(
    /** Author surname key to the genres of their books here, most used first. */
    val byAuthor: Map<String, List<String>>,
    /** Every genre a book can have: the standard ones and the library's own. */
    val genres: List<String>,
) {
    companion object {
        val NONE = GenreHints(emptyMap(), Genres.ALL)

        fun of(books: List<LibraryBook>, genres: List<String>): GenreHints {
            // Insertion-ordered, so ties keep the order the books list their genres in.
            val byAuthor = LinkedHashMap<String, MutableMap<String, Int>>()
            books.filter { it.catalog.genres.isNotEmpty() }.forEach { b ->
                val key = SameBook.authorKey(b.catalog.author) ?: return@forEach
                val counts = byAuthor.getOrPut(key) { LinkedHashMap() }
                b.catalog.genres.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
            return GenreHints(byAuthor.mapValues { (_, c) -> c.entries.sortedByDescending { it.value }.map { it.key } }, genres)
        }
    }
}
