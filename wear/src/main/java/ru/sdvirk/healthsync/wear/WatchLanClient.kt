package ru.sdvirk.healthsync.wear

import ru.sdvirk.healthsync.watch.LanLink
import ru.sdvirk.healthsync.watch.WatchSync
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.StandardCharsets

object WatchLanClient {

    fun discover(timeoutMs: Int = 4000): Pair<String, Int>? {
        DatagramSocket().use { sock ->
            sock.broadcast = true
            sock.soTimeout = timeoutMs
            val probe = WatchSync.LAN_PROBE.toByteArray(StandardCharsets.UTF_8)
            for (dest in broadcastTargets()) {
                runCatching {
                    sock.send(DatagramPacket(probe, probe.size, dest, WatchSync.LAN_UDP_PORT))
                }
            }
            val buf = ByteArray(256)
            val incoming = DatagramPacket(buf, buf.size)
            return try {
                sock.receive(incoming)
                val msg = String(incoming.data, incoming.offset, incoming.length, StandardCharsets.UTF_8)
                LanLink.parseBeacon(msg)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun ping(ip: String, port: Int): Boolean {
        val url = URL("http://$ip:$port/ping")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    fun post(ip: String, port: Int, path: String, body: String): String {
        val url = URL("http://$ip:$port$path")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 8000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", bytes.size.toString())
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.readBytes()
                ?.toString(Charsets.UTF_8)
                ?.trim()
                .orEmpty()
            if (code !in 200..299) error("HTTP $code $text")
            return text.ifBlank { "ok" }
        } finally {
            conn.disconnect()
        }
    }

    private fun broadcastTargets(): List<InetAddress> {
        val dests = LinkedHashSet<InetAddress>()
        runCatching { dests += InetAddress.getByName("255.255.255.255") }
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return dests.toList()
            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ifc in nif.interfaceAddresses) {
                    val b = ifc.broadcast
                    if (b != null) dests += b
                }
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val bytes = addr.address
                        bytes[3] = 0xFF.toByte()
                        dests += InetAddress.getByAddress(bytes)
                    }
                }
            }
        }
        return dests.toList()
    }
}
