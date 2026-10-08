package com.preform.desktop

import com.preform.sync.Protocol
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

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
        val process = ProcessBuilder("tailscale", "status", "--self", "--peers=false", "--json")
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(3, TimeUnit.SECONDS)
        Regex(""""DNSName"\s*:\s*"([^"]+)"""").find(out)?.groupValues?.get(1)?.trimEnd('.')?.ifBlank { null }
    }.getOrNull()
}

/**
 * Announces the library on the home network (DNS-SD), so a phone still finds the PC
 * after the router hands it a new address.
 */
class Discovery(private val serverId: String, private val name: String, private val port: Int) {
    private val running = mutableListOf<JmDNS>()

    fun start() {
        Addresses.current()
            .filter { !Addresses.isTailscale(it) && it.first().isDigit() }
            .forEach { ip ->
                runCatching {
                    val dns = JmDNS.create(InetAddress.getByName(ip), "preform-${serverId.take(8)}")
                    dns.registerService(
                        ServiceInfo.create("${Protocol.SERVICE_TYPE}.local.", name, port, "id=$serverId"),
                    )
                    running += dns
                }
            }
    }

    fun stop() {
        running.forEach { runCatching { it.unregisterAllServices(); it.close() } }
        running.clear()
    }
}
