package com.preform.desktop

import com.preform.sync.Format
import com.preform.sync.LookupQuery
import com.preform.sync.LookupResult
import java.nio.file.Path

/**
 * Fills in what books don't say about themselves: genres, a real title and author, a
 * description, the year and a cover, from Open Library. Results go into the catalog for
 * every phone, and (when switched on) into the book files themselves.
 */
class BookInfoService(
    private val store: Store,
    private val library: Library,
    private val openLibrary: OpenLibrary,
    private val backups: Path,
    private val config: () -> AppConfig,
    private val pause: () -> Unit = { Thread.sleep(1_000) },
) {
    /**
     * Looks up every book not yet looked up, one a second (Open Library asks for gentle
     * use). Stops at the first network failure; the next rescan carries on.
     */
    @Synchronized
    fun lookUpPending() {
        for (book in store.booksAwaitingLookup()) {
            if (!config().lookUpOnline) return
            if (!lookUp(book)) return
            pause()
        }
    }

    /** False when Open Library couldn't be reached. */
    private fun lookUp(book: LibraryBook): Boolean {
        val c = book.catalog
        val answer = if (book.titleFromFile) {
            openLibrary.findByFileName(book.relativePath.substringAfterLast('/'))
        } else {
            openLibrary.find(c.title, c.author)
        } ?: return false

        val info = answer.info
        if (info == null) {
            store.saveInfo(book.copy(lookup = DONE, genreSource = book.genreSource ?: "none"))
            return true
        }

        val genres = c.genres.ifEmpty { info.genres }
        // A cover only for books without one; PDFs show their first page instead.
        val coverBytes = if (c.coverEtag == null && c.format == Format.EPUB) info.coverId?.let(openLibrary::cover) else null
        val updated = book.copy(
            catalog = c.copy(
                title = if (book.titleFromFile) info.title else c.title,
                author = c.author ?: info.author,
                genres = genres,
                description = c.description ?: info.description,
                year = c.year ?: info.year,
                coverEtag = c.coverEtag ?: coverBytes?.let { library.saveCover(c.contentId, it) },
            ),
            genreSource = when {
                c.genres.isNotEmpty() -> book.genreSource
                genres.isNotEmpty() -> "online"
                else -> "none"
            },
            titleFromFile = false,
            lookup = DONE,
        )
        store.saveInfo(updated)

        if (config().writeIntoFiles) {
            val fill = BookFill(
                title = info.title.takeIf { book.titleFromFile },
                author = info.author.takeIf { c.author == null },
                genres = genres,
                description = updated.catalog.description,
                year = updated.catalog.year,
                cover = coverBytes,
            )
            // A file that can't be written (open elsewhere, read-only) keeps its info in the catalog.
            if (!fill.isEmpty) runCatching { library.inject(c.contentId, fill, backups) }
        }
        return true
    }

    /** Looks up books a phone has but the PC doesn't. Null when Open Library is unreachable or lookups are off. */
    fun lookUpForPhone(queries: List<LookupQuery>): List<LookupResult>? {
        if (!config().lookUpOnline) return null
        return queries.take(MAX_PHONE_BATCH).mapIndexed { i, query ->
            if (i > 0) pause()
            val answer = if (query.titleFromFile) openLibrary.findByFileName(query.title) else openLibrary.find(query.title, query.author)
            val info = (answer ?: return null).info
            if (info == null) {
                LookupResult(query.key, found = false)
            } else {
                LookupResult(
                    key = query.key,
                    found = true,
                    title = info.title,
                    author = info.author,
                    genres = info.genres,
                    description = info.description,
                    year = info.year,
                    coverId = info.coverId,
                )
            }
        }
    }

    fun cover(coverId: Long): ByteArray? = if (config().lookUpOnline) openLibrary.cover(coverId) else null

    /** Puts every original file back and stops writing into files. */
    fun restoreOriginals(): Int = library.restoreOriginals(backups)

    fun backupCount(): Int = backups.toFile().listFiles()?.size ?: 0

    companion object {
        const val DONE = "done"

        /** Phones send a few books per request, so each answers within the read timeout. */
        const val MAX_PHONE_BATCH = 8
    }
}
