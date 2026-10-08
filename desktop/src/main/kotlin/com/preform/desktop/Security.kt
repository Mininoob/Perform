package com.preform.desktop

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.io.path.exists

/**
 * The server's TLS identity: a self-signed certificate made on first run and kept with
 * the app's data. Phones don't trust it through any authority; they pin its fingerprint,
 * delivered in the pairing QR code, so only this PC can ever answer as the library.
 */
class TlsIdentity(private val file: Path, val password: String) {

    val keyStore: KeyStore = if (file.exists()) {
        KeyStore.getInstance("JKS").apply { file.toFile().inputStream().use { load(it, password.toCharArray()) } }
    } else {
        buildKeyStore {
            certificate(ALIAS) {
                password = this@TlsIdentity.password
                daysValid = 3650
                domains = listOf("localhost")
            }
        }.also { it.saveToFile(file.toFile(), password) }
    }

    /** SHA-256 of the certificate, lowercase hex: what the phone pins. */
    val fingerprint: String = sha256Hex(keyStore.getCertificate(ALIAS).encoded)

    companion object {
        const val ALIAS = "preform"
    }
}

/** Device tokens: random, shown to the device once, stored only as a hash. */
object Tokens {
    private val random = SecureRandom()

    fun newToken(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    fun hash(token: String): String = sha256Hex(token.toByteArray())
}

/**
 * The pairing window opened from the tray: one six-digit code, valid for five minutes,
 * good for one device, closed early after too many wrong guesses.
 */
class PairingWindow(private val clock: () -> Long = System::currentTimeMillis) {
    private val random = SecureRandom()
    private var code: String? = null
    private var expiresAt = 0L
    private var failures = 0

    @Synchronized
    fun open(): String {
        val fresh = (0 until 6).joinToString("") { random.nextInt(10).toString() }
        code = fresh
        expiresAt = clock() + VALID_MS
        failures = 0
        return fresh
    }

    @Synchronized
    fun close() {
        code = null
    }

    @get:Synchronized
    val isOpen: Boolean get() = code != null && clock() < expiresAt

    /** True once for the right code; then the window closes. */
    @Synchronized
    fun consume(attempt: String): Boolean {
        val current = code ?: return false
        if (clock() >= expiresAt) {
            close()
            return false
        }
        val match = MessageDigest.isEqual(current.toByteArray(), attempt.toByteArray())
        if (match) {
            close()
        } else if (++failures >= MAX_FAILURES) {
            close()
        }
        return match
    }

    companion object {
        const val VALID_MS = 5 * 60 * 1000L
        const val MAX_FAILURES = 5
    }
}

/**
 * Who may talk to the server at all: this PC, the home network, and Tailscale. A
 * request from any other address is refused before it reaches a route, so even a
 * forwarded port wouldn't expose the library to the internet.
 */
object PrivateNetwork {

    fun isAllowed(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> {
            val b = address.address.map { it.toInt() and 0xFF }
            address.isLoopbackAddress ||
                address.isSiteLocalAddress || // 10/8, 172.16/12, 192.168/16
                address.isLinkLocalAddress || // 169.254/16
                (b[0] == 100 && b[1] in 64..127) // 100.64/10: Tailscale
        }
        is Inet6Address -> {
            val b = address.address.map { it.toInt() and 0xFF }
            address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                (b[0] and 0xFE) == 0xFC || // fc00::/7 unique local
                (b[0] == 0xFD && b[1] == 0x7A && b[2] == 0x11 && b[3] == 0x5C && b[4] == 0xA1 && b[5] == 0xE0) // Tailscale
        }
        else -> false
    }

    /** Parses an address literal without ever doing a DNS lookup. */
    fun parse(literal: String): InetAddress? {
        val clean = literal.removePrefix("[").removeSuffix("]").substringBefore('%')
        val looksLikeIp = clean.contains(':') || clean.all { it.isDigit() || it == '.' }
        return when {
            clean == "localhost" -> InetAddress.getLoopbackAddress()
            looksLikeIp -> runCatching { InetAddress.getByName(clean) }.getOrNull()
            else -> null
        }
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
