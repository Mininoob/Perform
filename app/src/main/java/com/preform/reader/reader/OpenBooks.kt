package com.preform.reader.reader

import android.graphics.Bitmap
import com.preform.reader.library.BookEntity
import com.preform.reader.settings.ReadingPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.util.concurrent.ConcurrentHashMap

/** Everything the reader needs, prepared before it starts so its first frame is the book. */
class ReaderSession(
    val book: BookEntity,
    val publication: Publication,
    val initialLocator: Locator?,
    /** Effective settings at open time; live changes arrive through flows afterwards. */
    val initialPreferences: ReadingPreferences,
    /** Full-size cover for Book Mode, decoded off the main thread. */
    val cover: Bitmap?,
)

/**
 * Publications opened by the library and handed to the reader.
 *
 * Readium's navigator must be created from the activity's fragment factory before
 * `super.onCreate`, which is synchronous, while opening a book is not. So the library
 * opens the book first and parks it here; the reader picks it up by id. After process
 * death the session is gone and the reader returns to the library, the same pattern
 * Readium's own sample app uses.
 */
class OpenBooks {
    private val sessions = ConcurrentHashMap<Long, ReaderSession>()

    operator fun get(bookId: Long): ReaderSession? = sessions[bookId]

    fun put(session: ReaderSession) {
        sessions.put(session.book.id, session)?.publication?.close()
    }

    fun close(bookId: Long) {
        sessions.remove(bookId)?.publication?.close()
    }
}
