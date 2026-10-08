package com.preform.desktop

import com.preform.sync.BookState
import com.preform.sync.CatalogPage
import com.preform.sync.Format
import com.preform.sync.LookupQuery
import com.preform.sync.LookupResult
import io.ktor.http.ContentType
import io.ktor.server.response.respondBytes
import com.preform.sync.PairRequest
import com.preform.sync.PairResponse
import com.preform.sync.Protocol
import com.preform.sync.StatePage
import com.preform.sync.isContentId
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.outputStream

/** What the routes need, so tests can run them against a temporary library. */
class ServerDeps(
    val store: Store,
    val library: Library,
    val pairing: PairingWindow,
    val serverId: String,
    val serverName: () -> String,
    val tempDir: Path,
    val clock: () -> Long = System::currentTimeMillis,
    /** Looks up books only a phone has; null result when lookups are off or offline. */
    val lookup: (List<LookupQuery>) -> List<LookupResult>? = { null },
    val lookupCover: (Long) -> ByteArray? = { null },
)

fun Application.preformModule(deps: ServerDeps) {
    install(ContentNegotiation) { json(Protocol.json) }
    install(PartialContent)
    install(AutoHeadResponse)

    // Two gates before any route: a private address, and the same protocol version.
    intercept(ApplicationCallPipeline.Plugins) {
        val remote = PrivateNetwork.parse(context.request.origin.remoteAddress)
        if (remote == null || !PrivateNetwork.isAllowed(remote)) {
            context.respond(HttpStatusCode.Forbidden)
            finish()
            return@intercept
        }
        if (context.request.header(Protocol.VERSION_HEADER)?.toIntOrNull() != Protocol.VERSION) {
            context.respond(HttpStatusCode.UpgradeRequired, "This library needs Preform protocol ${Protocol.VERSION}.")
            finish()
        }
    }

    routing {
        post("/pair") {
            val request = call.receive<PairRequest>()
            if (!deps.pairing.consume(request.code)) {
                call.respond(HttpStatusCode.Forbidden, "That code isn't valid. Open pairing on the PC and try again.")
                return@post
            }
            val token = Tokens.newToken()
            deps.store.addDevice(request.deviceId, request.deviceName.take(80), Tokens.hash(token), deps.clock())
            call.respond(PairResponse(token, deps.serverId, deps.serverName()))
        }

        get("/library") {
            call.device(deps) ?: return@get
            val (books, removed, cursor) = deps.store.catalogSince(call.since())
            call.respond(CatalogPage(books, removed, cursor))
        }

        get("/books/{id}/file") {
            call.device(deps) ?: return@get
            // An id from before information was written into the file still finds the book.
            val book = call.parameters["id"]?.takeIf(::isContentId)?.let(deps.store::resolve)?.let(deps.store::book)
            val file = book?.let(deps.library::file)
            if (file == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondFile(file.toFile())
        }

        get("/books/{id}/cover") {
            call.device(deps) ?: return@get
            val id = call.parameters["id"]?.takeIf(::isContentId)?.let(deps.store::resolve)
            val size = if (call.request.queryParameters["size"] == "full") Library.CoverSize.FULL else Library.CoverSize.THUMB
            val cover = id?.let { deps.library.cover(it, size) }
            if (cover == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            deps.store.book(id)?.catalog?.coverEtag?.let { call.response.header(HttpHeaders.ETag, "\"$it\"") }
            call.respondFile(cover.toFile())
        }

        put("/books") {
            call.device(deps) ?: return@put
            val name = call.request.queryParameters["name"] ?: "book"
            val temp = Files.createTempFile(deps.tempDir, "upload", ".part")
            try {
                val contentId = withContext(Dispatchers.IO) { call.receiveToFile(temp) }
                val format = if (contentId == null) null else sniffFormat(temp)
                if (contentId == null || format == null) {
                    call.respond(HttpStatusCode.UnprocessableEntity, "Only EPUB and PDF books up to 1 GB can be added.")
                    return@put
                }
                call.respond(deps.library.addUpload(temp, name, contentId, format))
            } finally {
                Files.deleteIfExists(temp)
            }
        }

        get("/state") {
            call.device(deps) ?: return@get
            val (states, cursor) = deps.store.statesSince(call.since())
            call.respond(StatePage(states, cursor))
        }

        post("/state") {
            call.device(deps) ?: return@post
            val incoming = call.receive<List<BookState>>()
            if (incoming.any { !isContentId(it.contentId) }) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            call.respond(incoming.map { deps.store.mergeState(it.copy(contentId = deps.store.resolve(it.contentId))) })
        }

        post("/lookup") {
            call.device(deps) ?: return@post
            val queries = call.receive<List<LookupQuery>>()
            val results = withContext(Dispatchers.IO) { deps.lookup(queries) }
            if (results == null) {
                call.respond(HttpStatusCode.ServiceUnavailable, "The PC can't look books up right now.")
            } else {
                call.respond(results)
            }
        }

        get("/lookup/cover/{coverId}") {
            call.device(deps) ?: return@get
            val coverId = call.parameters["coverId"]?.toLongOrNull()
            val bytes = coverId?.let { withContext(Dispatchers.IO) { deps.lookupCover(it) } }
            if (bytes == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respondBytes(bytes, ContentType.Image.JPEG)
            }
        }
    }
}

/** The paired device making this call, or null after answering 401. */
private suspend fun ApplicationCall.device(deps: ServerDeps): Device? {
    val token = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
    val device = token?.let { deps.store.deviceForTokenHash(Tokens.hash(it)) }
    if (device == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    deps.store.touchDevice(device.deviceId, deps.clock())
    return device
}

private fun ApplicationCall.since(): Long = request.queryParameters["since"]?.toLongOrNull() ?: 0L

/** Streams the body to [file], hashing as it goes. Null if it's over the size limit. */
private suspend fun ApplicationCall.receiveToFile(file: Path): String? {
    val digest = MessageDigest.getInstance("SHA-256")
    var total = 0L
    DigestInputStream(receiveChannel().toInputStream(), digest).use { input ->
        file.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_UPLOAD_BYTES) return null
                output.write(buffer, 0, read)
            }
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** Trusts the bytes, not the file name: a PDF starts with %PDF; an EPUB is a zip naming itself one. */
internal fun sniffFormat(file: Path): Format? {
    val head = Files.newInputStream(file).use { it.readNBytes(4) }
    return when {
        head.decodeToString().startsWith("%PDF") -> Format.PDF
        head.size == 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> runCatching {
            ZipFile(file.toFile()).use { zip ->
                zip.getEntry("mimetype")?.let { zip.getInputStream(it).readBytes().decodeToString().trim() }
            }
        }.getOrNull()?.takeIf { it == "application/epub+zip" }?.let { Format.EPUB }
        else -> null
    }
}

private const val MAX_UPLOAD_BYTES = 1L shl 30

/** The HTTPS server: one TLS connector on every interface, gated to private addresses. */
class LibraryServer(private val deps: ServerDeps, private val identity: TlsIdentity, val port: Int) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        engine = embeddedServer(
            Netty,
            configure = {
                sslConnector(
                    keyStore = identity.keyStore,
                    keyAlias = TlsIdentity.ALIAS,
                    keyStorePassword = { identity.password.toCharArray() },
                    privateKeyPassword = { identity.password.toCharArray() },
                ) {
                    port = this@LibraryServer.port
                    host = "0.0.0.0"
                }
            },
        ) { preformModule(deps) }.start(wait = false)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
    }
}
