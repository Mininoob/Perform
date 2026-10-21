package com.perform.desktop

import com.perform.sync.PeerHello
import com.perform.sync.PeerKind
import com.perform.sync.Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import okhttp3.RequestBody.Companion.toRequestBody

/** Two real libraries over real TLS, each with its own certificate: pairing, browsing, copying. */
class PeerTest {

    /** One computer's whole Perform Library, in-process. */
    private class Side(val dir: Path, id: String, name: String) {
        val books: Path = dir.resolve("Books").also(Files::createDirectories)
        val store = Store(dir.resolve("library.db"))
        val library = Library({ books }, store, dir.resolve("covers"))
        val identity = TlsIdentity(dir.resolve("identity.jks"), "test-password")
        val pairing = PairingWindow()
        val port = ServerSocket(0).use { it.localPort }
        val server = LibraryServer(
            ServerDeps(store, library, pairing, id, { name }, dir.resolve("temp").also(Files::createDirectories),
                hello = { PeerHello(id, name, PeerKind.COMPUTER, "windows", store.liveBooks().size) }),
            identity,
            port,
        )
        val peerStore = PeerStore(dir.resolve("peers.db"))
        val peers = PeerService(peerStore, library, dir.resolve("covers"), dir.resolve("temp"), { id }, { name })

        fun close() {
            server.stop()
            store.close()
            peerStore.close()
        }
    }

    private lateinit var root: Path
    private lateinit var pc: Side
    private lateinit var mac: Side

    @Before
    fun setUp() {
        root = Files.createTempDirectory("perform-peers")
        pc = Side(root.resolve("pc"), "pc-id", "Study PC")
        mac = Side(root.resolve("mac"), "mac-id", "Studio Mac")
        TestBooks.epub(mac.books.resolve("Fiction/Dune.epub"), "Dune", "Frank Herbert")
        TestBooks.epub(mac.books.resolve("Fiction/Emma.epub"), "Emma", "Jane Austen")
        mac.library.scan()
        pc.server.start()
        mac.server.start()
    }

    @After
    fun tearDown() {
        pc.close()
        mac.close()
        root.toFile().deleteRecursively()
    }

    private fun connectPcToMac(): Peer {
        val (hello, cert) = pc.peers.hello("127.0.0.1", mac.port)
        assertEquals("Studio Mac", hello.name)
        assertEquals(mac.identity.fingerprint, cert)
        val code = mac.pairing.open()
        return pc.peers.connect(listOf("127.0.0.1"), mac.port, cert, code, hello)
    }

    @Test
    fun `a computer pairs with another and sees its shelves`() {
        val peer = connectPcToMac()
        assertEquals("mac-id", peer.peerId)
        assertEquals(PeerKind.COMPUTER, mac.store.devices().single().kind)

        val books = pc.peers.books("mac-id")
        assertEquals(setOf("Dune", "Emma"), books.map { it.catalog.title }.toSet())
        assertTrue(books.all { it.catalog.folder == "Fiction" && it.peerId == "mac-id" })
        val view = pc.peers.views.value.single()
        assertTrue(view.online)
        assertEquals(2, view.bookCount)
        // Thumbnails came along, so the shelf shows while the Mac is away.
        assertNotNull(pc.peers.cover(books.first(), full = false))
    }

    @Test
    fun `a wrong code or certificate is refused`() {
        val (hello, cert) = pc.peers.hello("127.0.0.1", mac.port)
        val code = mac.pairing.open()
        val wrong = if (code == "000000") "111111" else "000000"
        runCatching { pc.peers.connect(listOf("127.0.0.1"), mac.port, cert, wrong, hello) }
            .onSuccess { fail("A wrong code paired") }
        runCatching { pc.peers.connect(listOf("127.0.0.1"), mac.port, "00".repeat(32), code, hello) }
            .onSuccess { fail("A wrong certificate paired") }
        assertTrue(pc.peerStore.peers().isEmpty())
    }

    @Test
    fun `the catalog stays browsable while the other library is offline`() {
        connectPcToMac()
        mac.server.stop()
        pc.peers.refresh()
        assertFalse(pc.peers.views.value.single().online)
        assertEquals(2, pc.peers.books("mac-id").size)
    }

    @Test
    fun `changes on the other side arrive on refresh`() {
        connectPcToMac()
        TestBooks.epub(mac.books.resolve("History/SPQR.epub"), "SPQR", "Mary Beard")
        Files.delete(mac.books.resolve("Fiction/Emma.epub"))
        mac.library.scan()
        pc.peers.refresh("mac-id")
        assertEquals(setOf("Dune", "SPQR"), pc.peers.books("mac-id").map { it.catalog.title }.toSet())
    }

    @Test
    fun `copying a book over verifies it and files it in the chosen folder`() {
        connectPcToMac()
        val dune = pc.peers.books("mac-id").first { it.catalog.title == "Dune" }
        val result = pc.peers.copy(dune, "Science Fiction")
        assertEquals(CopyResult.Added(Library.ImportResult(1, 0, 0)), result)
        val filed = pc.books.resolve("Science Fiction").listDirectoryEntries()
        assertEquals(1, filed.size)
        pc.library.scan()
        assertTrue(pc.store.liveBooks().any { it.catalog.contentId == dune.catalog.contentId })
        // Nothing changed on the Mac.
        assertTrue(mac.books.resolve("Fiction/Dune.epub").exists())
        // A second copy is recognised as already there.
        assertEquals(CopyResult.Added(Library.ImportResult(0, 1, 0)), pc.peers.copy(dune, "Science Fiction"))
    }

    @Test
    fun `a computer's token can look but not add`() {
        val peer = connectPcToMac()
        val request = okhttp3.Request.Builder()
            .url("https://127.0.0.1:${mac.port}/books?name=x.epub")
            .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
            .header("Authorization", "Bearer ${peer.token}")
            .put(ByteArray(10).toRequestBody())
            .build()
        val client = trustingClient(peer.certSha256)
        client.newCall(request).execute().use { assertEquals(403, it.code) }
    }

    @Test
    fun `disconnecting forgets the other library here only`() {
        connectPcToMac()
        pc.peers.disconnect("mac-id")
        assertTrue(pc.peers.views.value.isEmpty())
        assertTrue(pc.peers.books("mac-id").isEmpty())
        assertEquals(2, mac.store.liveBooks().size)
    }

    private fun trustingClient(cert: String): okhttp3.OkHttpClient {
        val trust = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
                check(sha256Hex(chain[0].encoded) == cert)
            }
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val ssl = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return okhttp3.OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory, trust).hostnameVerifier { _, _ -> true }.build()
    }
}
