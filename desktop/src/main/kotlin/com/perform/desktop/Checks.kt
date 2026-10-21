package com.perform.desktop

/** Things worth tidying in a library. */
enum class CheckKind(val label: String, val hint: String) {
    IDENTICAL("Identical copies", "The same file sits in more than one place. The extra copies can go to the bin."),
    SAME_BOOK("Same book twice", "Different files with the same title and author: another edition, or an EPUB and a PDF."),
    UNREADABLE("Can't be read", "These files couldn't be opened as books. They may be damaged or protected."),
    TITLE_FROM_FILE("Title is the file name", "Nothing inside these files names the book. Edit them, or look them up again."),
    NO_AUTHOR("No author", "Edit them, or look them up again."),
    NO_COVER("No cover", "Edit them to choose a cover, or look them up again."),
    NO_GENRE("No genre", "Edit them, or look them up again."),
}

/**
 * Runs every check over the library. [identicalCopies] comes from the last scan; each
 * check lists the books it found, with the same book's copies or twins next to each other.
 */
fun runChecks(books: List<LibraryBook>, identicalCopies: Map<String, List<String>>): Map<CheckKind, List<LibraryBook>> {
    val results = LinkedHashMap<CheckKind, List<LibraryBook>>()
    results[CheckKind.IDENTICAL] = books.filter { identicalCopies[it.catalog.contentId].orEmpty().isNotEmpty() }
    results[CheckKind.SAME_BOOK] = books
        .groupBy { sameBookKey(it) }
        .filterKeys { it != null }
        .values
        .filter { group -> group.size > 1 }
        .flatten()
    results[CheckKind.UNREADABLE] = books.filter { it.unreadable }
    results[CheckKind.TITLE_FROM_FILE] = books.filter { it.titleFromFile && !it.unreadable }
    results[CheckKind.NO_AUTHOR] = books.filter { it.catalog.author == null && !it.unreadable }
    results[CheckKind.NO_COVER] = books.filter { it.catalog.coverEtag == null && !it.unreadable }
    results[CheckKind.NO_GENRE] = books.filter { it.catalog.genres.isEmpty() && !it.unreadable }
    return results
}

/** Title and first author, ignoring case, punctuation and a leading article; null without an author. */
private fun sameBookKey(book: LibraryBook): String? {
    val author = book.catalog.author?.let(OpenLibrary::firstAuthor) ?: return null
    val surname = author.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 2 }.lastOrNull() ?: return null
    return OpenLibrary.normalize(OpenLibrary.cleanTitle(book.catalog.title)) + "|" + surname
}
