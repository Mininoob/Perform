package com.perform.desktop

import com.perform.sync.Genres
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** What Open Library knows about a book. */
data class BookInfo(
    val title: String,
    val author: String?,
    val genres: List<String>,
    val description: String?,
    val year: Int?,
    /** Open Library cover id, for [OpenLibrary.cover]. */
    val coverId: Long?,
)

/**
 * Looks books up on Open Library (free, no account): genres, the real title and author,
 * a description, the year and a cover. Only titles, authors and file names are sent, once
 * per book; answers are stored and shared with every paired phone.
 */
class OpenLibrary(
    private val fetch: (String) -> String? = ::httpGet,
    private val fetchBytes: (String) -> ByteArray? = ::httpGetBytes,
) {
    /** Result of a lookup: [info] null when reached but nothing matched. */
    data class Answer(val info: BookInfo?)

    /**
     * Finds a book by title (and author, when known). Null when Open Library couldn't be
     * reached, so the caller can try again later.
     */
    fun find(title: String, author: String?): Answer? {
        val clean = cleanTitle(title)
        if (clean.isBlank()) return Answer(null)
        val queries = listOfNotNull(author?.let { search(clean, it) }, search(clean, null))
        for (url in queries) {
            val body = fetch(url) ?: return null
            val doc = bestMatch(body, listOf(clean), author) ?: continue
            return Answer(details(doc) ?: return null)
        }
        return Answer(null)
    }

    /**
     * Finds a book that has no title inside it, from its file name ("Frank Herbert - Dune.pdf").
     * Only accepted when one part of the name is exactly the found title.
     */
    fun findByFileName(fileName: String): Answer? {
        val base = fileName.substringBeforeLast('.').replace('_', ' ')
        val parts = base.split(" - ", " – ", " — ").map(::cleanTitle).filter { it.isNotBlank() }
        if (parts.isEmpty()) return Answer(null)
        val url = "https://openlibrary.org/search.json?q=${enc(parts.joinToString(" "))}&fields=$FIELDS&limit=5"
        val body = fetch(url) ?: return null
        val doc = bestMatch(body, parts) ?: return Answer(null)
        return Answer(details(doc) ?: return null)
    }

    fun cover(coverId: Long): ByteArray? = fetchBytes("https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false")

    private fun search(title: String, author: String?): String {
        val authorPart = author?.let { "&author=${enc(firstAuthor(it))}" }.orEmpty()
        return "https://openlibrary.org/search.json?title=${enc(title)}$authorPart&fields=$FIELDS&limit=5"
    }

    /** Fills in the description, which only the work's own record has. Null if unreachable. */
    private fun details(doc: JsonObject): BookInfo? {
        val key = doc.string("key")
        val description = if (key != null && key.startsWith("/works/")) {
            val work = fetch("https://openlibrary.org$key.json") ?: return null
            parseDescription(work)
        } else {
            null
        }
        return BookInfo(
            title = doc.string("title") ?: return null,
            author = (doc["author_name"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull,
            genres = Genres.classify(subjectsOf(doc)),
            description = description,
            year = (doc["first_publish_year"] as? JsonPrimitive)?.intOrNull,
            coverId = (doc["cover_i"] as? JsonPrimitive)?.longOrNull,
        )
    }

    companion object {
        private const val FIELDS = "key,title,author_name,subject,first_publish_year,cover_i"

        /**
         * The first work whose title really is one of [titles]: Open Library's search is
         * loose, and a near miss would file the book under someone else's details.
         */
        internal fun bestMatch(json: String, titles: List<String>, author: String? = null): JsonObject? = runCatching {
            val wanted = titles.map(::normalize).toSet()
            val names = author?.let(::nameParts)
            Json.parseToJsonElement(json).jsonObject["docs"]?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { doc -> doc.string("title")?.let { normalize(cleanTitle(it)) } in wanted }
                // When the book names its author, the match must share a name with them:
                // plenty of different books share a title.
                .firstOrNull { doc ->
                    names == null || (doc["author_name"] as? JsonArray).orEmpty().any { a ->
                        (a as? JsonPrimitive)?.contentOrNull?.let { nameParts(it).intersect(names).isNotEmpty() } == true
                    }
                }
        }.getOrNull()

        /** "Herbert, Frank" and "F. Herbert" both give {herbert, frank}-ish parts; initials are ignored. */
        private fun nameParts(name: String): Set<String> =
            name.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 }.toSet()

        /** Open Library lists dozens of subjects; the first ones carry the genre. */
        internal fun subjectsOf(doc: JsonObject): List<String> =
            (doc["subject"] as? JsonArray).orEmpty().take(40).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

        /** A work's description is either a string or {"value": ...}; trailing link lists are dropped. */
        internal fun parseDescription(json: String): String? = runCatching {
            val element = Json.parseToJsonElement(json).jsonObject["description"] ?: return null
            val text = when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element.string("value")
                else -> null
            } ?: return null
            text.substringBefore("----------")
                .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1") // Markdown links to plain text.
                .trim()
                .take(1500)
                .ifBlank { null }
        }.getOrNull()

        /** "Dune: Deluxe Edition (Book 1)" → "Dune". */
        fun cleanTitle(title: String): String =
            title.replace(Regex("""[(\[].*?[)\]]"""), " ")
                .substringBefore(':')
                .replace(Regex("""\s+"""), " ")
                .trim()

        /** Lowercase letters and digits only, without a leading article: "The Hobbit" matches "Hobbit". */
        fun normalize(title: String): String =
            title.lowercase().replace(Regex("""^(the|a|an)\s+"""), "").filter(Char::isLetterOrDigit)

        /** "Terry Pratchett, Neil Gaiman" → "Terry Pratchett". */
        internal fun firstAuthor(author: String): String = author.split(',', '&', ';').first().trim()

        private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)

        private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

        private val client: HttpClient by lazy {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        }

        private fun request(url: String) = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(20))
            // Open Library asks API callers to identify themselves.
            .header("User-Agent", "PerformLibrary/1.0 (personal ebook library)")
            .GET()
            .build()

        private fun httpGet(url: String): String? = runCatching {
            val response = client.send(request(url), HttpResponse.BodyHandlers.ofString())
            response.body().takeIf { response.statusCode() == 200 }
        }.getOrNull()

        private fun httpGetBytes(url: String): ByteArray? = runCatching {
            val response = client.send(request(url), HttpResponse.BodyHandlers.ofByteArray())
            response.body().takeIf { response.statusCode() == 200 && it.size > 1000 }
        }.getOrNull()
    }
}
