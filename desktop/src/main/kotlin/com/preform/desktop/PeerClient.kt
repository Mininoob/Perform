package com.preform.desktop

import com.preform.sync.CatalogPage
import com.preform.sync.PairRequest
import com.preform.sync.PairResponse
import com.preform.sync.PeerHello
import com.preform.sync.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** The other library refused this computer's token: it was removed over there. */
class PeerUnpairedException : IOException("That library no longer recognises this computer. Connect again.")

/**
 * Reads another computer's Preform Library: its catalog, covers and files. Trusts exactly
 * the certificate pinned at pairing, and tries each of the peer's addresses in turn (last
 * good first), so the same pairing works at home and over Tailscale. Blocking: call it off
 * the UI thread.
 */
class PeerClient(private val peer: Peer, private val onAddressWorked: (String) -> Unit = {}) {

    private val quick = pinned(peer.certSha256, readSeconds = 30)
    private val slow = pinned(peer.certSha256, readSeconds = 180)

    /** The other library introducing itself, over the pinned connection. */
    fun hello(): PeerHello = call(quick, "/hello").use {
        Protocol.json.decodeFromString(PeerHello.serializer(), it.body!!.string())
    }

    fun catalog(since: Long): CatalogPage = call(quick, "/library?since=$since").use {
        Protocol.json.decodeFromString(CatalogPage.serializer(), it.body!!.string())
    }

    /** Saves a cover to [target]; false when the peer has none. */
    fun cover(contentId: String, full: Boolean, target: Path): Boolean = try {
        call(quick, "/books/$contentId/cover?size=${if (full) "full" else "thumb"}").use { response ->
            target.parent.toFile().mkdirs()
            target.toFile().outputStream().use { response.body!!.byteStream().copyTo(it) }
            true
        }
    } catch (e: PeerHttpException) {
        if (e.code == 404) false else throw e
    }

    /** Downloads a book into [part], resuming what's already there; reports bytes so far. */
    fun download(contentId: String, part: Path, onProgress: (Long) -> Unit = {}) {
        val file = part.toFile()
        val have = if (file.exists()) file.length() else 0L
        call(slow, "/books/$contentId/file") { if (have > 0) header("Range", "bytes=$have-") }.use { response ->
            val append = response.code == 206
            var written = if (append) have else 0L
            FileOutputStream(file, append).use { out ->
                val input = response.body!!.byteStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    written += read
                    onProgress(written)
                }
            }
        }
    }

    private fun call(client: OkHttpClient, path: String, extra: Request.Builder.() -> Unit = {}): Response {
        var last: IOException? = null
        for (address in peer.addressOrder) {
            val request = Request.Builder()
                .url(url(address, peer.port, path))
                .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
                .header("Authorization", "Bearer ${peer.token}")
                .apply(extra)
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                last = e
                continue
            }
            onAddressWorked(address)
            return response.checked()
        }
        throw last ?: IOException("No address to reach ${peer.name} on.")
    }

    class PeerHttpException(val code: Int, message: String) : IOException(message)

    companion object {
        private val JSON = "application/json".toMediaType()

        /** Who answers at an address, before pairing: its name and the certificate it shows. */
        fun hello(address: String, port: Int): Pair<PeerHello, String> {
            var seen: String? = null
            val recorder = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    throw CertificateException("Client certificates aren't used.")

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    seen = sha256Hex(chain[0].encoded)
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val request = Request.Builder()
                .url(url(address, port, "/hello"))
                .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
                .build()
            val hello = clientWith(recorder, 10).newCall(request).execute().checked().use {
                Protocol.json.decodeFromString(PeerHello.serializer(), it.body!!.string())
            }
            return hello to (seen ?: throw IOException("That library didn't present a certificate."))
        }

        /** Trades the other library's pairing code for a read-only token. */
        fun pair(addresses: List<String>, port: Int, certSha256: String, request: PairRequest): Pair<String, PairResponse> {
            val client = pinned(certSha256, 30)
            val body = Protocol.json.encodeToString(PairRequest.serializer(), request).toRequestBody(JSON)
            var last: IOException? = null
            for (address in addresses) {
                val call = Request.Builder()
                    .url(url(address, port, "/pair"))
                    .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
                    .post(body)
                    .build()
                val response = try {
                    client.newCall(call).execute()
                } catch (e: IOException) {
                    last = e
                    continue
                }
                return response.checked().use { address to Protocol.json.decodeFromString(PairResponse.serializer(), it.body!!.string()) }
            }
            throw last ?: IOException("No address to reach that library on.")
        }

        private fun pinned(certSha256: String, readSeconds: Long): OkHttpClient {
            val pin = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    throw CertificateException("Client certificates aren't used.")

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val actual = chain.firstOrNull()?.encoded?.let(::sha256Hex)
                    if (!MessageDigest.isEqual(actual?.toByteArray(), certSha256.lowercase().toByteArray())) {
                        throw CertificateException("This isn't the paired library.")
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            return clientWith(pin, readSeconds)
        }

        private fun clientWith(trust: X509TrustManager, readSeconds: Long): OkHttpClient {
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            return OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trust)
                // The certificate pin is the identity; addresses change between networks.
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(readSeconds, TimeUnit.SECONDS)
                .build()
        }

        private fun url(address: String, port: Int, path: String): String {
            val host = if (':' in address && !address.startsWith("[")) "[$address]" else address
            return "https://$host:$port$path"
        }

        private fun Response.checked(): Response {
            if (isSuccessful) return this
            val error = when (code) {
                401 -> PeerUnpairedException()
                else -> PeerHttpException(code, runCatching { body?.string() }.getOrNull()?.take(200)?.ifBlank { null } ?: "That library answered $code.")
            }
            close()
            throw error
        }
    }
}
