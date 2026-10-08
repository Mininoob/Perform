package com.preform.desktop

import com.preform.sync.PeerHello
import com.preform.sync.PeerKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/** Two libraries over real TLS, each mirroring the other: they end up the same, and nothing echoes. */
class MirrorTest {

    private class Side(val dir: Path, val id: String, val name: String, clock: () -> Long) {
        val books: Path = dir.resolve("Books").also(Files::createDirectories)
        val store = Store(dir.resolve("library.db")).also { it.origin = id; it.clock = clock }
        val library = Library({ books }, store, dir.resolve("covers"))
        val identity = TlsIdentity(dir.resolve("identity.jks"), "pw")
        val pairing = PairingWindow()
        val port = ServerSocket(0).use { it.localPort }
        val peerStore = PeerStore(dir.resolve("peers.db"))
        val trashed = mutableListOf<String>()
        val peers: PeerService = PeerService(
            peerStore, library, dir.resolve("covers"), dir.resolve("temp").also(Files::createDirectories), { id }, { name },
            clock = clock,
            store = store,
            // Tests delete instead of using the real Recycle Bin.
            trash = { file -> trashed += file.fileName.toString(); Files.deleteIfExists(file) },
        )
        val server = LibraryServer(
            ServerDeps(store, library, pairing, id, { name }, dir.resolve("temp"),
                hello = { PeerHello(id, name, PeerKind.COMPUTER, "windows", store.liveBooks().size, mirroring = peers.mirroring()) }),
            identity,
            port,
        )

        fun book(title: String) = store.liveBooks().singleOrNull { it.catalog.title == title }

        fun close() {
            server.stop()
            store.close()
            peerStore.close()
        }
    }

    private lateinit var root: Path
    private lateinit var pc: Side
    private lateinit var mac: Side
    private var now = 1_000_000L
    private val clock = { now }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("preform-mirror")
        pc = Side(root.resolve("pc"), "pc-id", "PC", clock)
        mac = Side(root.resolve("mac"), "mac-id", "Mac", clock)
        TestBooks.epub(pc.books.resolve("Fiction/Emma.epub"), "Emma", "Jane Austen")
        TestBooks.epub(mac.books.resolve("Sci-Fi/Dune.epub"), "Dune", "Frank Herbert")
        pc.library.scan()
        mac.library.scan()
        pc.server.start()
        mac.server.start()
        connect(pc, mac)
        connect(mac, pc)
    }

    @After
    fun tearDown() {
        pc.close()
        mac.close()
        root.toFile().deleteRecursively()
    }

    private fun connect(from: Side, to: Side) {
        val (hello, cert) = from.peers.hello("127.0.0.1", to.port)
        from.peers.connect(listOf("127.0.0.1"), to.port, cert, to.pairing.open(), hello)
    }

    /** A few rounds both ways, as the two-minute refresh would. */
    private fun settle() = repeat(2) {
        now += 1_000
        pc.peers.refresh()
        mac.peers.refresh()
    }

    private fun mirrorBothWays() {
        now += 1_000
        pc.peers.setMirror("mac-id", true)
        mac.peers.setMirror("pc-id", true)
        settle()
    }

    @Test
    fun `turning mirroring on previews, then each side gets the other's books in the same folders`() {
        assertEquals(MirrorPreview(copy = 1, bytes = mac.book("Dune")!!.catalog.sizeBytes, move = 0), pc.peers.mirrorPreview("mac-id"))
        mirrorBothWays()
        assertEquals("Sci-Fi", pc.book("Dune")!!.catalog.folder)
        assertEquals("Fiction", mac.book("Emma")!!.catalog.folder)
        assertTrue(pc.peers.views.value.single().mirrorsUs)
    }

    @Test
    fun `a move on one side follows on the other, and doesn't come back as new`() {
        mirrorBothWays()
        now += 10_000
        pc.library.moveBooks(listOf(pc.book("Dune")!!.catalog.contentId), "Classics")
        settle()
        assertEquals("Classics", mac.book("Dune")!!.catalog.folder)
        val macStamp = mac.book("Dune")!!.catalog.folderStamp
        assertEquals("pc-id", macStamp.by)
        // More rounds change nothing.
        val pcRev = pc.store.currentRev()
        settle()
        assertEquals(pcRev, pc.store.currentRev())
        assertEquals(macStamp, mac.book("Dune")!!.catalog.folderStamp)
    }

    @Test
    fun `when both move the same book, the later move wins on both`() {
        mirrorBothWays()
        now += 10_000
        pc.library.moveBooks(listOf(pc.book("Emma")!!.catalog.contentId), "Romance")
        now += 5_000
        mac.library.moveBooks(listOf(mac.book("Emma")!!.catalog.contentId), "Classics")
        settle()
        assertEquals("Classics", pc.book("Emma")!!.catalog.folder)
        assertEquals("Classics", mac.book("Emma")!!.catalog.folder)
    }

    @Test
    fun `details edited on one side reach the other`() {
        mirrorBothWays()
        now += 10_000
        val dune = mac.book("Dune")!!
        mac.store.saveInfo(dune.copy(catalog = dune.catalog.copy(genres = listOf("Science Fiction"), year = 1965)))
        settle()
        assertEquals(listOf("Science Fiction"), pc.book("Dune")!!.catalog.genres)
        assertEquals(1965, pc.book("Dune")!!.catalog.year)
    }

    @Test
    fun `a book removed after mirroring began goes to the bin on the other side too`() {
        mirrorBothWays()
        now += 10_000
        mac.library.trash(listOf(mac.book("Dune")!!.catalog.contentId)) { Files.deleteIfExists(it) }
        settle()
        assertEquals(null, pc.book("Dune"))
        assertEquals(listOf("Dune.epub"), pc.trashed)
        // And it doesn't come back from the PC.
        settle()
        assertEquals(null, mac.book("Dune"))
    }

    @Test
    fun `a book that was only here before mirroring is never removed by it`() {
        TestBooks.epub(pc.books.resolve("Mine.epub"), "Mine Only", "Someone Else")
        pc.library.scan()
        now += 1_000
        // Only the PC mirrors the Mac; the Mac doesn't have it and never had it.
        pc.peers.setMirror("mac-id", true)
        settle()
        assertTrue(pc.book("Mine Only") != null)
        assertTrue(pc.trashed.isEmpty())
    }

    @Test
    fun `empty folders travel too`() {
        mirrorBothWays()
        now += 10_000
        mac.library.createFolder("", "To Read")
        settle()
        assertTrue(pc.books.resolve("To Read").exists())
        assertFalse(pc.store.folder("To Read")!!.removed)
    }
}
