package com.preform.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

/** Constants both sides agree on. */
object Protocol {
    /** Bumped on any incompatible change; the server refuses clients from another version. */
    const val VERSION = 1
    const val VERSION_HEADER = "X-Preform-Protocol"
    const val DEFAULT_PORT = 47474

    /** DNS-SD service type the PC advertises on the LAN. */
    const val SERVICE_TYPE = "_preform._tcp"

    /** Prefix of the QR code's text, so a scan of anything else is rejected at once. */
    const val INVITE_SCHEME = "preform-pair:"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * A short, readable piece of the certificate fingerprint ("3F9A C21B"). When a phone
     * pairs by typed address instead of the QR code, the person compares this on both
     * screens, so a look-alike server on the network can't slip in.
     */
    fun checkCode(certSha256: String): String = certSha256.take(8).uppercase().chunked(4).joinToString(" ")
}

/**
 * What the PC's pairing QR code carries: where the server is, the certificate to trust,
 * and a short-lived code that buys this device a token.
 */
@Serializable
data class PairingInvite(
    val version: Int = Protocol.VERSION,
    val serverId: String,
    val serverName: String,
    val port: Int,
    /** SHA-256 of the server certificate, hex. The phone trusts exactly this certificate. */
    val certSha256: String,
    val code: String,
    /** Every address the server answers on: LAN first, then Tailscale. */
    val addresses: List<String>,
) {
    fun encode(): String = Protocol.INVITE_SCHEME + Protocol.json.encodeToString(serializer(), this)

    companion object {
        fun decode(text: String): PairingInvite? =
            text.takeIf { it.startsWith(Protocol.INVITE_SCHEME) }
                ?.removePrefix(Protocol.INVITE_SCHEME)
                ?.let { runCatching { Protocol.json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

@Serializable
data class PairRequest(val code: String, val deviceId: String, val deviceName: String)

@Serializable
data class PairResponse(val token: String, val serverId: String, val serverName: String)

enum class Format { EPUB, PDF }

/** One book in the PC library, as the phone sees it. */
@Serializable
data class CatalogBook(
    val contentId: String,
    val title: String,
    val author: String? = null,
    val format: Format,
    val sizeBytes: Long,
    val rightToLeft: Boolean = false,
    /** Changes whenever the cover does, so the phone knows to fetch it again. */
    val coverEtag: String? = null,
    val addedAt: Long,
)

/** Catalog changes since a cursor. */
@Serializable
data class CatalogPage(
    val books: List<CatalogBook>,
    /** Books gone from the PC library since the cursor. */
    val removed: List<String>,
    val cursor: Long,
)

/** A value plus when and where it was last changed: the unit of "newest wins". */
@Serializable
data class Stamped<T>(val value: T, val at: Long, val by: String)

@Serializable
data class Position(
    /** Readium Locator JSON; opaque to the server. */
    val locatorJson: String,
    /** 0..1 through the book. */
    val progression: Double,
)

/**
 * Everything about reading one book that follows the reader between devices. Each group
 * is stamped separately, so changing the font on one phone doesn't undo the page reached
 * on another.
 */
@Serializable
data class BookState(
    val contentId: String,
    val position: Stamped<Position>? = null,
    /** This book's own reading settings as JSON; a null value means "follow the defaults". */
    val settings: Stamped<String?>? = null,
    /** Null value means "never set", which counts as on. */
    val bookMode: Stamped<Boolean?>? = null,
    val lastOpenedAt: Long? = null,
)

@Serializable
data class StatePage(val states: List<BookState>, val cursor: Long)

/** Merges two views of the same book: per group, the newest change wins. */
fun BookState.mergedWith(other: BookState): BookState {
    require(contentId == other.contentId) { "Merging states of different books" }
    return BookState(
        contentId = contentId,
        position = newest(position, other.position),
        settings = newest(settings, other.settings),
        bookMode = newest(bookMode, other.bookMode),
        lastOpenedAt = listOfNotNull(lastOpenedAt, other.lastOpenedAt).maxOrNull(),
    )
}

/**
 * The later of two stamped values. Same instant: the higher device id wins, so every
 * device resolves a tie the same way and they all converge.
 */
fun <T> newest(a: Stamped<T>?, b: Stamped<T>?): Stamped<T>? = when {
    a == null -> b
    b == null -> a
    a.at != b.at -> if (a.at > b.at) a else b
    else -> if (a.by >= b.by) a else b
}

/**
 * A book's identity on every device: the SHA-256 of its bytes, hex. Renaming or moving
 * the file keeps it; the same book on phone and PC is recognised as one.
 */
fun contentIdOf(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** A content id is exactly 64 lowercase hex characters; anything else is refused before use. */
fun isContentId(text: String): Boolean = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }
