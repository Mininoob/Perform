package com.perform.desktop

import com.perform.sync.Protocol
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager

/** The real server over real TLS: a client pinning the right certificate gets in; a wrong pin doesn't. */
class TlsTest {

    private lateinit var dir: Path
    private lateinit var store: Store
    private lateinit var server: LibraryServer
    private lateinit var identity: TlsIdentity
    private var port = 0

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("perform-tls")
        store = Store(dir.resolve("state.db"))
        val library = Library({ dir.resolve("Books").also(Files::createDirectories) }, store, dir.resolve("covers"))
        identity = TlsIdentity(dir.resolve("identity.jks"), "test-password")
        port = ServerSocket(0).use { it.localPort }
        server = LibraryServer(ServerDeps(store, library, PairingWindow(), "srv", { "PC" }, dir), identity, port)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        store.close()
        dir.toFile().deleteRecursively()
    }

    /** The phone's trust rule, reproduced: accept exactly one certificate fingerprint. */
    private fun client(pinnedSha256: String): OkHttpClient {
        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw UnsupportedOperationException()
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded).joinToString("") { "%02x".format(it) }
                if (actual != pinnedSha256) throw java.security.cert.CertificateException("Unexpected certificate")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .hostnameVerifier { _, _ -> true } // identity comes from the pin, not the name
            .build()
    }

    private fun request() = Request.Builder()
        .url("https://127.0.0.1:$port/library")
        .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
        .build()

    @Test
    fun `the pinned certificate connects`() {
        client(identity.fingerprint).newCall(request()).execute().use { response ->
            // Reached the server over TLS; no token, so it asks for one.
            assertEquals(401, response.code)
        }
    }

    @Test
    fun `any other certificate is refused`() {
        assertThrows(SSLHandshakeException::class.java) {
            client("00".repeat(32)).newCall(request()).execute().close()
        }
    }

    @Test
    fun `the identity survives a restart`() {
        assertEquals(identity.fingerprint, TlsIdentity(dir.resolve("identity.jks"), "test-password").fingerprint)
    }
}
