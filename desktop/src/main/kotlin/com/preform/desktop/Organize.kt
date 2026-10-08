package com.preform.desktop

import com.preform.sync.Genres

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
