package com.perform.desktop

import com.perform.sync.Protocol
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The addresses a phone can reach this PC on, best first. */
object Addresses {

    fun current(): List<String> {
        val v4 = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { PrivateNetwork.isAllowed(it) && !it.isLinkLocalAddress }
            .map { it.hostAddress }
        return (v4.sortedBy(::rank) + listOfNotNull(tailscaleName())).distinct()
    }

    /**
     * Home Wi-Fi first (192.168 / 10), Tailscale next, then 172.16/12, which on a PC is
     * usually a WSL or Hyper-V adapter the phone can't reach anyway.
     */
    private fun rank(ip: String): Int = when {
        ip.startsWith("192.168.") || ip.startsWith("10.") -> 0
        isTailscale(ip) -> 1
        else -> 2
    }

    fun isTailscale(ip: String): Boolean =
        ip.split('.').map { it.toIntOrNull() ?: -1 }.let { it.size == 4 && it[0] == 100 && it[1] in 64..127 }

    /** The tailnet's MagicDNS name for this PC, if Tailscale is installed and signed in. */
    private fun tailscaleName(): String? = runCatching {
        val process = ProcessBuilder(Platform.current.tailscaleCommand, "status", "--self", "--peers=false", "--json")
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(3, TimeUnit.SECONDS)
        Regex(""""DNSName"\s*:\s*"([^"]+)"""").find(out)?.groupValues?.get(1)?.trimEnd('.')?.ifBlank { null }
    }.getOrNull()
}

/** Another Perform Library heard on the network. */
data class NearbyLibrary(val serverId: String, val name: String, val addresses: List<String>, val port: Int)

/**
 * Announces the library on the home network (DNS-SD), so a phone still finds the PC
 * after the router hands it a new address, and listens for the other libraries there.
 */
class Discovery(
    private val serverId: String,
    private val name: String,
    private val port: Int,
    private val platform: Platform = Platform.current,
) {
    private val running = mutableListOf<JmDNS>()
    private var dnsSd: Process? = null
    private var browser: Process? = null

    /** Other libraries on the network, by instance name; never this one. */
    private val found = MutableStateFlow(emptyMap<String, NearbyLibrary>())
    private val _nearby = MutableStateFlow(emptyList<NearbyLibrary>())
    val nearby: StateFlow<List<NearbyLibrary>> = _nearby

    private fun heard(instance: String, library: NearbyLibrary?) {
        found.update { if (library == null || library.serverId == serverId) it - instance else it + (instance to library) }
        _nearby.value = found.value.values.distinctBy { it.serverId }.sortedBy { it.name.lowercase() }
    }

    fun start() {
        if (platform.isMac) {
            browseMac()
            // macOS has its own mDNS responder; announcing through it (dns-sd) avoids two
            // stacks fighting over port 5353 and needs no extra permission.
            dnsSd = runCatching {
                ProcessBuilder("dns-sd", "-R", name, Protocol.SERVICE_TYPE, "local", port.toString(), "id=$serverId")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }.getOrNull()
            return
        }
        Addresses.current()
            .filter { !Addresses.isTailscale(it) && it.first().isDigit() }
            .forEach { ip ->
                runCatching {
                    val dns = JmDNS.create(InetAddress.getByName(ip), "perform-${serverId.take(8)}")
                    dns.registerService(
                        ServiceInfo.create("${Protocol.SERVICE_TYPE}.local.", name, port, "id=$serverId"),
                    )
                    dns.addServiceListener("${Protocol.SERVICE_TYPE}.local.", listener(dns))
                    running += dns
                }
            }
    }

    private fun listener(dns: JmDNS) = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) {
            dns.requestServiceInfo(event.type, event.name, 3_000)
        }

        override fun serviceRemoved(event: ServiceEvent) = heard(event.name, null)

        override fun serviceResolved(event: ServiceEvent) {
            val info = event.info
            val id = info.getPropertyString("id") ?: return
            val addresses = info.inet4Addresses.map { it.hostAddress }.filter { PrivateNetwork.isAllowed(InetAddress.getByName(it)) }
            if (addresses.isNotEmpty()) heard(event.name, NearbyLibrary(id, event.name, addresses, info.port))
        }
    }

    /** macOS: `dns-sd -B` lists instances as they come and go; `-L` resolves each one. */
    private fun browseMac() {
        browser = runCatching {
            ProcessBuilder("dns-sd", "-B", Protocol.SERVICE_TYPE, "local").redirectErrorStream(true).start()
        }.getOrNull() ?: return
        val process = browser!!
        Thread({
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    val match = BROWSE_LINE.find(line) ?: return@forEachLine
                    val (action, instance) = match.destructured
                    if (action == "Rmv") heard(instance, null) else Thread({ heard(instance, resolveMac(instance)) }, "perform-resolve").start()
                }
            }
        }, "perform-browse").apply { isDaemon = true }.start()
    }

    private fun resolveMac(instance: String): NearbyLibrary? = runCatching {
        val process = ProcessBuilder("dns-sd", "-L", instance, Protocol.SERVICE_TYPE, "local").redirectErrorStream(true).start()
        try {
            val reader = process.inputStream.bufferedReader()
            var host: String? = null
            var port = 0
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                val line = reader.readLine() ?: break
                REACHED.find(line)?.let { host = it.groupValues[1].trimEnd('.'); port = it.groupValues[2].toInt() }
                val id = TXT_ID.find(line)?.groupValues?.get(1)
                if (host != null && id != null) return@runCatching NearbyLibrary(id, instance, listOf(host!!), port)
            }
            null
        } finally {
            process.destroy()
        }
    }.getOrNull()

    fun stop() {
        dnsSd?.destroy()
        dnsSd = null
        browser?.destroy()
        browser = null
        running.forEach { runCatching { it.unregisterAllServices(); it.close() } }
        running.clear()
    }
}

private val BROWSE_LINE = Regex("""\s(Add|Rmv)\s+\d+\s+\d+\s+\S+\s+_perform\._tcp\.\s+(.+)$""")
private val REACHED = Regex("""can be reached at (\S+):(\d+)""")
private val TXT_ID = Regex("""id=([A-Za-z0-9-]+)""")
