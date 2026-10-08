package com.preform.desktop

import com.preform.sync.BookState
import com.preform.sync.CatalogBook
import com.preform.sync.CatalogPage
import com.preform.sync.OrganizeChange
import com.preform.sync.OrganizeRequest
import com.preform.sync.OrganizeResult
import com.preform.sync.PairRequest
import com.preform.sync.PeerKind
import com.preform.sync.PairResponse
import com.preform.sync.Position
import com.preform.sync.Protocol
import com.preform.sync.Stamped
import com.preform.sync.StatePage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes

class ServerTest {

    private lateinit var dir: Path
    private lateinit var root: Path
    private lateinit var store: Store
    private lateinit var library: Library
    private val pairing = PairingWindow()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("preform-server")
        root = dir.resolve("Books")
        Files.createDirectories(root)
        store = Store(dir.resolve("state.db"))
        library = Library({ root }, store, dir.resolve("covers"))
        TestBooks.epub(root.resolve("pride.epub"), "Pride and Prejudice", body = "x".repeat(5_000))
        library.scan()
    }

    @After
    fun tearDown() {
        store.close()
        dir.toFile().deleteRecursively()
    }

    private fun serve(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { preformModule(ServerDeps(store, library, pairing, "srv-1", { "Test PC" }, dir)) }
        block()
    }

    private suspend fun HttpClient.json(method: String, path: String, token: String?, body: String? = null): HttpResponse {
        val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header(Protocol.VERSION_HEADER, Protocol.VERSION)
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            body?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
        return when (method) {
            "POST" -> post(path, build)
            else -> get(path, build)
        }
    }

    private suspend fun ApplicationTestBuilder.pair(): String {
        val code = pairing.open()
        val response = client.json("POST", "/pair", null, Protocol.json.encodeToString(PairRequest.serializer(), PairRequest(code, "fold", "Z Fold")))
        assertEquals(HttpStatusCode.OK, response.status)
        return Protocol.json.decodeFromString(PairResponse.serializer(), response.bodyAsText()).token
    }

    @Test
    fun `pairing hands out a token that opens the library`() = serve {
        val token = pair()
        val page = Protocol.json.decodeFromString(CatalogPage.serializer(), client.json("GET", "/library", token).bodyAsText())
        assertEquals(listOf("Pride and Prejudice"), page.books.map { it.title })
        assertTrue(page.cursor > 0)
    }

    @Test
    fun `a wrong or reused code gets nothing`() = serve {
        val code = pairing.open()
        val wrong = client.json("POST", "/pair", null, """{"code":"${if (code == "123456") "654321" else "123456"}","deviceId":"x","deviceName":"x"}""")
        assertEquals(HttpStatusCode.Forbidden, wrong.status)

        pair()
        val reused = client.json("POST", "/pair", null, """{"code":"$code","deviceId":"y","deviceName":"y"}""")
        assertEquals(HttpStatusCode.Forbidden, reused.status)
    }

    @Test
    fun `no token, a made-up token, or a revoked device is refused`() = serve {
        assertEquals(HttpStatusCode.Unauthorized, client.json("GET", "/library", null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.json("GET", "/library", "made-up").status)

        val token = pair()
        store.revoke("fold")
        assertEquals(HttpStatusCode.Unauthorized, client.json("GET", "/library", token).status)
    }

    @Test
    fun `an old client is told to update`() = serve {
        assertEquals(HttpStatusCode.UpgradeRequired, client.get("/library").status)
    }

    @Test
    fun `books download whole or by range, and bad ids are not found`() = serve {
        val token = pair()
        val book = store.liveBooks().single()
        val bytes = root.resolve("pride.epub").readBytes()

        val whole = client.get("/books/${book.catalog.contentId}/file") {
            header(Protocol.VERSION_HEADER, Protocol.VERSION)
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertTrue(whole.readRawBytes().contentEquals(bytes))

        val part = client.get("/books/${book.catalog.contentId}/file") {
            header(Protocol.VERSION_HEADER, Protocol.VERSION)
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Range, "bytes=100-199")
        }
        assertEquals(HttpStatusCode.PartialContent, part.status)
        assertTrue(part.readRawBytes().contentEquals(bytes.copyOfRange(100, 200)))

        assertEquals(HttpStatusCode.NotFound, client.json("GET", "/books/..%2F..%2Fstate.db/file", token).status)
        assertEquals(HttpStatusCode.NotFound, client.json("GET", "/books/${"0".repeat(64)}/file", token).status)
    }

    @Test
    fun `phone uploads land in the library once, and non-books are refused`() = serve {
        val token = pair()
        val upload = TestBooks.epub(dir.resolve("upload/new.epub"), "Added On Phone")

        suspend fun put(bytes: ByteArray) = client.put("/books?name=New%20Book.epub") {
            header(Protocol.VERSION_HEADER, Protocol.VERSION)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(bytes)
        }

        val first = put(upload.readBytes())
        assertEquals(HttpStatusCode.OK, first.status)
        val book = Protocol.json.decodeFromString(CatalogBook.serializer(), first.bodyAsText())
        assertEquals("Added On Phone", book.title)
        assertTrue(root.resolve("Added on phone/New Book.epub").exists())

        // The same book again is recognised, not duplicated.
        assertEquals(book.contentId, Protocol.json.decodeFromString(CatalogBook.serializer(), put(upload.readBytes()).bodyAsText()).contentId)
        assertEquals(2, store.liveBooks().size)

        assertEquals(HttpStatusCode.UnprocessableEntity, put("just text".toByteArray()).status)
    }

    @Test
    fun `a phone reorganizes the library, and a computer may not`() = serve {
        val token = pair()
        val id = store.liveBooks().single().catalog.contentId
        val body = Protocol.json.encodeToString(
            OrganizeRequest.serializer(),
            OrganizeRequest(
                listOf(
                    // Real times: the book's own stamp is when it was added, moments ago.
                    OrganizeChange.Folder("Classics", at = System.currentTimeMillis()),
                    OrganizeChange.BookFolder(id, "Classics", at = System.currentTimeMillis() + 1),
                    OrganizeChange.Folder("Empty Shelf", at = System.currentTimeMillis() + 2),
                ),
            ),
        )
        val response = client.json("POST", "/organize", token, body)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(3, Protocol.json.decodeFromString(OrganizeResult.serializer(), response.bodyAsText()).applied)
        assertTrue(root.resolve("Classics/pride.epub").exists())
        val page = Protocol.json.decodeFromString(CatalogPage.serializer(), client.json("GET", "/library", token).bodyAsText())
        assertEquals("fold", page.books.single().folderStamp.by)
        assertTrue(page.folders.any { it.path == "Empty Shelf" && !it.removed })

        val code = pairing.open()
        val mac = client.json("POST", "/pair", null, Protocol.json.encodeToString(PairRequest.serializer(), PairRequest(code, "mac", "Mac", PeerKind.COMPUTER)))
        val macToken = Protocol.json.decodeFromString(PairResponse.serializer(), mac.bodyAsText()).token
        assertEquals(HttpStatusCode.Forbidden, client.json("POST", "/organize", macToken, body).status)
    }

    @Test
    fun `an upload can name its folder, but not one outside the library`() = serve {
        val token = pair()
        val upload = TestBooks.epub(dir.resolve("upload/new.epub"), "From The Phone")
        suspend fun put(folder: String) = client.put("/books?name=New.epub&folder=${java.net.URLEncoder.encode(folder, "UTF-8")}") {
            header(Protocol.VERSION_HEADER, Protocol.VERSION)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(upload.readBytes())
        }
        assertEquals(HttpStatusCode.BadRequest, put("../..").status)
        assertEquals(HttpStatusCode.OK, put("Fiction/Phone Finds").status)
        assertTrue(root.resolve("Fiction/Phone Finds/New.epub").exists())
    }

    @Test
    fun `reading state merges, newest wins, and other devices see it`() = serve {
        val token = pair()
        val id = store.liveBooks().single().catalog.contentId
        val states = ListSerializer(BookState.serializer())

        fun state(progress: Double, at: Long) =
            BookState(id, position = Stamped(Position("{}", progress), at, "fold"), lastOpenedAt = at)

        client.json("POST", "/state", token, Protocol.json.encodeToString(states, listOf(state(0.6, at = 200))))
        // An older update from another device doesn't win.
        val merged = Protocol.json.decodeFromString(
            states,
            client.json("POST", "/state", token, Protocol.json.encodeToString(states, listOf(state(0.2, at = 100)))).bodyAsText(),
        )
        assertEquals(0.6, merged.single().position!!.value.progression, 0.0)

        val page = Protocol.json.decodeFromString(StatePage.serializer(), client.json("GET", "/state?since=0", token).bodyAsText())
        assertEquals(0.6, page.states.single().position!!.value.progression, 0.0)
    }
}
