package com.preform.reader.sync

import com.preform.sync.BookState
import com.preform.sync.CatalogBook
import com.preform.sync.CatalogPage
import com.preform.sync.LookupQuery
import com.preform.sync.LookupResult
import com.preform.sync.PairRequest
import com.preform.sync.PairResponse
import com.preform.sync.Protocol
import com.preform.sync.StatePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** The PC said this phone's token is no good: it was removed in the tray app. */
class NotPairedException : IOException("This phone is no longer paired with the PC.")

/** The PC turned the request down, with its own explanation when it gave one. */
class ServerException(val code: Int, message: String) : IOException(message)

/**
 * Talks to the paired PC over HTTPS, trusting exactly one certificate: the one whose
 * fingerprint came in the pairing QR code. No certificate authority is consulted, so a
 * device on the network pretending to be the PC is refused at the handshake.
 *
 * Each call tries the PC's addresses in turn (last one that worked first), which is how
 * the same pairing works on home Wi-Fi and over Tailscale.
 */
class LibraryClient(
    private val connection: Connection,
    private val onAddressWorked: suspend (String) -> Unit = {},
) {
    private val quick = pinnedClient(connection.certSha256, readSeconds = 30)
    private val slow = pinnedClient(connection.certSha256, readSeconds = 120)

    suspend fun catalog(since: Long): CatalogPage =
        getJson("/library?since=$since") { Protocol.json.decodeFromString(CatalogPage.serializer(), it) }

    suspend fun states(since: Long): StatePage =
        getJson("/state?since=$since") { Protocol.json.decodeFromString(StatePage.serializer(), it) }

    /** Sends local changes; the PC answers with each book's merged state. */
    suspend fun pushStates(states: List<BookState>): List<BookState> {
        val list = ListSerializer(BookState.serializer())
        val body = Protocol.json.encodeToString(list, states).toRequestBody(JSON)
        return call(quick, "/state") { post(body) }.use { response ->
            Protocol.json.decodeFromString(list, response.body!!.string())
        }
    }

    /** Saves a cover to [target]; false when the PC has none. */
    suspend fun cover(contentId: String, full: Boolean, target: File): Boolean = try {
        call(quick, "/books/$contentId/cover?size=${if (full) "full" else "thumb"}") { get() }.use { response ->
            target.outputStream().use { response.body!!.byteStream().copyTo(it) }
            true
        }
    } catch (e: ServerException) {
        if (e.code == 404) false else throw e
    }

    /**
     * Downloads a book into [part], resuming from what's already there, and reports
     * progress as bytes so far.
     */
    suspend fun download(contentId: String, part: File, onProgress: (Long) -> Unit) {
        val have = if (part.exists()) part.length() else 0L
        call(slow, "/books/$contentId/file") {
            get().apply { if (have > 0) header("Range", "bytes=$have-") }
        }.use { response ->
            // 206 continues the partial file; 200 means the PC sent it all again.
            val append = response.code == 206
            var written = if (append) have else 0L
            java.io.FileOutputStream(part, append).use { out ->
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

    /** Adds a book from this phone to the PC library; returns how the PC filed it. */
    suspend fun upload(file: File, name: String): CatalogBook {
        val body: RequestBody = file.asRequestBody("application/octet-stream".toMediaType())
        val encoded = java.net.URLEncoder.encode(name, "UTF-8")
        return call(slow, "/books?name=$encoded") { put(body) }.use { response ->
            Protocol.json.decodeFromString(CatalogBook.serializer(), response.body!!.string())
        }
    }

    /** Asks the PC to look up books only this phone has. */
    suspend fun lookup(queries: List<LookupQuery>): List<LookupResult> {
        val body = Protocol.json.encodeToString(ListSerializer(LookupQuery.serializer()), queries).toRequestBody(JSON)
        return call(slow, "/lookup") { post(body) }.use { response ->
            Protocol.json.decodeFromString(ListSerializer(LookupResult.serializer()), response.body!!.string())
        }
    }

    /** A cover the PC found online, as JPEG bytes; null if there's none. */
    suspend fun lookupCover(coverId: Long): ByteArray? = try {
        call(slow, "/lookup/cover/$coverId") { get() }.use { it.body!!.bytes() }
    } catch (e: ServerException) {
        if (e.code == 404) null else throw e
    }

    private suspend fun <T> getJson(path: String, parse: (String) -> T): T =
        call(quick, path) { get() }.use { parse(it.body!!.string()) }

    /** Runs a request against each address until one answers; the caller closes the response. */
    private suspend fun call(client: OkHttpClient, path: String, method: Request.Builder.() -> Request.Builder): Response =
        withContext(Dispatchers.IO) {
            var lastError: IOException? = null
            for (address in connection.addressOrder) {
                val request = Request.Builder()
                    .url(url(address, connection.port, path))
                    .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
                    .header("Authorization", "Bearer ${connection.token}")
                    .method()
                    .build()
                val response = try {
                    client.newCall(request).execute()
                } catch (e: IOException) {
                    // Unreachable from here (wrong network, PC asleep): try the next address.
                    lastError = e
                    continue
                }
                onAddressWorked(address)
                return@withContext response.checked()
            }
            throw lastError ?: IOException("The PC has no addresses.")
        }

    companion object {
        private val JSON = "application/json".toMediaType()

        /** Trades a pairing code for a token. Tries each address in the invite. */
        suspend fun pair(
            addresses: List<String>,
            port: Int,
            certSha256: String,
            request: PairRequest,
        ): Pair<String, PairResponse> = withContext(Dispatchers.IO) {
            val client = pinnedClient(certSha256, readSeconds = 30)
            val body = Protocol.json.encodeToString(PairRequest.serializer(), request).toRequestBody(JSON)
            var lastError: IOException? = null
            for (address in addresses) {
                val call = Request.Builder()
                    .url(url(address, port, "/pair"))
                    .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
                    .post(body)
                    .build()
                val response = try {
                    client.newCall(call).execute()
                } catch (e: IOException) {
                    lastError = e
                    continue
                }
                return@withContext response.checked().use {
                    address to Protocol.json.decodeFromString(PairResponse.serializer(), it.body!!.string())
                }
            }
            throw lastError ?: IOException("No address to reach the PC on.")
        }

        /**
         * Connects to a typed address and reports the certificate it presents, without
         * trusting it for anything else. The person checks its code against the PC screen
         * before pairing goes ahead.
         */
        suspend fun fingerprintAt(address: String, port: Int): String = withContext(Dispatchers.IO) {
            var seen: String? = null
            val recorder = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    throw CertificateException("Client certificates aren't used.")

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    seen = sha256Hex(chain[0].encoded)
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val client = clientWith(recorder, readSeconds = 10)
            val request = Request.Builder()
                .url(url(address, port, "/library"))
                .header(Protocol.VERSION_HEADER, Protocol.VERSION.toString())
                .get()
                .build()
            // Without a token the answer is 401, which is fine: the handshake is what counts.
            client.newCall(request).execute().close()
            seen ?: throw IOException("The PC didn't present a certificate.")
        }

        private fun pinnedClient(certSha256: String, readSeconds: Long): OkHttpClient {
            val pin = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    throw CertificateException("Client certificates aren't used.")

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    val actual = chain.firstOrNull()?.encoded?.let(::sha256Hex)
                    if (!MessageDigest.isEqual(actual?.toByteArray(), certSha256.lowercase().toByteArray())) {
                        throw CertificateException("This isn't the paired PC.")
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
                // The certificate pin is the identity check; addresses change (LAN, Tailscale).
                .hostnameVerifier { _, _ -> true }
                // Short, so an address that isn't reachable from here fails over quickly.
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(readSeconds, TimeUnit.SECONDS)
                .writeTimeout(readSeconds, TimeUnit.SECONDS)
                .build()
        }

        private fun url(address: String, port: Int, path: String): String {
            val host = if (':' in address && !address.startsWith("[")) "[$address]" else address
            return "https://$host:$port$path"
        }

        private fun Response.checked(): Response {
            if (isSuccessful) return this
            val text = runCatching { body?.string() }.getOrNull()?.take(200)?.ifBlank { null }
            val error = when (code) {
                401 -> NotPairedException()
                426 -> ServerException(code, "Update Preform or the PC app: they speak different versions.")
                else -> ServerException(code, text ?: "The PC answered $code.")
            }
            close()
            throw error
        }

        internal fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
