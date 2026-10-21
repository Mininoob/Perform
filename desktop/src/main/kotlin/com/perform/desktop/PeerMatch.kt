package com.perform.desktop

import com.perform.sync.BookMatch
import com.perform.sync.SameBook

/**
 * One library's books, indexed for "is this book in it?": by every id a book has had,
 * then by title and author surname (another edition or format). The one rule from
 * [SameBook], used for the in-yours marks and for Compare.
 */
class LibraryIndex(books: List<LibraryBook>) {
    private val ids = HashSet<String>()
    private val keys = HashSet<String>()

    init {
        books.forEach { b ->
            ids += b.catalog.contentId
            ids += b.catalog.previousIds
            SameBook.key(b.catalog.title, b.catalog.author)?.let(keys::add)
        }
    }

    fun match(book: LibraryBook): BookMatch = when {
        book.catalog.contentId in ids || book.catalog.previousIds.any { it in ids } -> BookMatch.SAME
        SameBook.key(book.catalog.title, book.catalog.author)?.let { it in keys } == true -> BookMatch.EDITION
        else -> BookMatch.NONE
    }

    /** Has this book in some form: the same file or another edition. */
    fun has(book: LibraryBook) = match(book) != BookMatch.NONE
}
