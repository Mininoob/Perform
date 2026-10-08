package com.preform.desktop

import com.preform.sync.Genres
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Finds genres for books that don't declare any, by asking Open Library (free, no
 * account) for the subjects librarians gave the title. Only the title and author are
 * sent, once per book; the answer is stored and shared with every paired phone.
 */
class GenreLookup(
    private val fetch: (String) -> String? = ::httpGet,
) {
    /**
     * Genres for a book, or null when Open Library couldn't be reached, so the caller can
     * try again later. An empty list means it was reached and had nothing useful.
     */
    fun genres(title: String, author: String?): List<String>? {
        val cleanTitle = cleanTitle(title)
        if (cleanTitle.isBlank()) return emptyList()
        val queries = listOfNotNull(
            author?.let { query(cleanTitle, it) },
            query(cleanTitle, null),
        )
        for (url in queries) {
            val body = fetch(url) ?: return null
            val subjects = parseSubjects(body, cleanTitle)
            if (subjects.isNotEmpty()) return Genres.classify(subjects)
        }
        return emptyList()
    }

    private fun query(title: String, author: String?): String {
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        val authorPart = author?.let { "&author=${enc(firstAuthor(it))}" }.orEmpty()
        return "https://openlibrary.org/search.json?title=${enc(title)}$authorPart&fields=title,subject&limit=5"
    }

    companion object {
        /**
         * Subjects of the first work whose title really is [title]: Open Library's search
         * is loose, and a near miss would file the book under someone else's genre.
         */
        internal fun parseSubjects(json: String, title: String): List<String> = runCatching {
            val docs = Json.parseToJsonElement(json).jsonObject["docs"]?.jsonArray ?: return emptyList()
            val wanted = normalize(title)
            docs.asSequence()
                .mapNotNull { it as? JsonObject }
                .filter { doc -> doc["title"]?.let { normalize(cleanTitle(it.jsonPrimitive.content)) } == wanted }
                .mapNotNull { it["subject"] as? JsonArray }
                .firstOrNull { it.isNotEmpty() }
                // Open Library lists dozens, most specific to the edition; the first ones carry the genre.
                ?.take(40)
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .orEmpty()
        }.getOrDefault(emptyList())

        /** "Dune: Deluxe Edition (Book 1)" → "Dune". */
        internal fun cleanTitle(title: String): String =
            title.replace(Regex("""[(\[].*?[)\]]"""), " ")
                .substringBefore(':')
                .replace(Regex("""\s+"""), " ")
                .trim()

        /** Lowercase letters and digits only, without a leading article: "The Hobbit" matches "Hobbit". */
        private fun normalize(title: String): String =
            title.lowercase().replace(Regex("""^(the|a|an)\s+"""), "").filter(Char::isLetterOrDigit)

        /** "Terry Pratchett, Neil Gaiman" → "Terry Pratchett". */
        internal fun firstAuthor(author: String): String = author.split(',', '&', ';').first().trim()

        private val client: HttpClient by lazy {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
        }

        private fun httpGet(url: String): String? = runCatching {
            val request = HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(15))
                // Open Library asks API callers to identify themselves.
                .header("User-Agent", "PreformLibrary/1.0 (personal ebook library)")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body().takeIf { response.statusCode() == 200 }
        }.getOrNull()
    }
}
