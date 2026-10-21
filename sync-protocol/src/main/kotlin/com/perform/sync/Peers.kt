package com.perform.sync

import kotlinx.serialization.Serializable

/** What's at the other end of a pairing. */
enum class PeerKind { COMPUTER, PHONE }

/**
 * A library introducing itself (GET /hello, no token, private networks only), so a
 * computer can confirm what it found on the network before asking for a pairing code.
 */
@Serializable
data class PeerHello(
    val serverId: String,
    val name: String,
    val kind: PeerKind = PeerKind.COMPUTER,
    /** "windows", "macos", "android". */
    val platform: String,
    val bookCount: Int = 0,
    val protocol: Int = Protocol.VERSION,
    /** The libraries this one mirrors (by server id), so the other side can say "mirrors you too". */
    val mirroring: List<String> = emptyList(),
)

/** One of a phone's own books (its folder or imports), published to its computers. */
@Serializable
data class DeviceBook(
    val contentId: String,
    val title: String,
    val author: String? = null,
    val format: Format,
    val sizeBytes: Long,
    val genres: List<String> = emptyList(),
    /** Folder inside the phone's library folder; "" for imports and the top. */
    val folder: String = "",
    val coverEtag: String? = null,
)

/** Everything a phone has of its own; replaces what the computer last heard. */
@Serializable
data class DeviceCatalog(val books: List<DeviceBook>)

/** A computer asking a phone for a copy of one of its books, to file in [folder]. */
@Serializable
data class CopyRequest(val contentId: String, val folder: String, val requestedAt: Long)

/** How two libraries' books relate, for "what's missing". */
enum class BookMatch {
    /** The same file (or the same book before and after information was written into it). */
    SAME,

    /** The same title by the same author, but a different file: another edition or format. */
    EDITION,
    NONE,
}

/**
 * The one rule every side uses to say whether two books are the same: by content id first
 * (including ids a book had before its file was rewritten), then by title and author. A
 * book without an author is only ever the same by id, so two "Notes" files never match.
 */
object SameBook {

    fun match(
        aIds: Collection<String>,
        aTitle: String,
        aAuthor: String?,
        bIds: Collection<String>,
        bTitle: String,
        bAuthor: String?,
    ): BookMatch = when {
        aIds.any { it in bIds } -> BookMatch.SAME
        key(aTitle, aAuthor)?.let { it == key(bTitle, bAuthor) } == true -> BookMatch.EDITION
        else -> BookMatch.NONE
    }

    /** The first author's surname, lowercased: "tolkien" for "J.R.R. Tolkien"; null without one. */
    fun authorKey(author: String?): String? = author
        ?.split(',', '&', ';')?.first()
        ?.lowercase()
        ?.split(Regex("""[^\p{L}\p{N}]+"""))
        ?.filter { it.length >= 2 }
        ?.lastOrNull()

    /** "the hobbit|tolkien"; null without an author. */
    fun key(title: String, author: String?): String? {
        val surname = authorKey(author) ?: return null
        val t = title.replace(Regex("""[(\[].*?[)\]]"""), " ").substringBefore(':').lowercase().trim()
            .replace(Regex("""^(the|a|an)\s+"""), "")
            .filter(Char::isLetterOrDigit)
        return if (t.isEmpty()) null else "$t|$surname"
    }
}
