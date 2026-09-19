package ru.sdvirk.healthsync.wear

import android.content.Context
import android.net.wifi.WifiManager
import ru.sdvirk.healthsync.watch.LanAddresses
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WatchLanClient {

    fun findPhoneHttp(context: Context): Pair<String, Int> {
        val prefs = context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
        val last = prefs.getString(WatchSync.KEY_LAST_LAN_IP, null)
        if (!last.isNullOrBlank() && ping(last, WatchSync.LAN_HTTP_PORT, 800)) {
            return last to WatchSync.LAN_HTTP_PORT
        }
        val found = discover(context)
        if (found != null && ping(found.first, found.second, 800)) {
            prefs.edit().putString(WatchSync.KEY_LAST_LAN_IP, found.first).apply()
            return found
        }
        val scanned = scanSubnet()
        if (scanned != null) {
            prefs.edit().putString(WatchSync.KEY_LAST_LAN_IP, scanned.first).apply()
            return scanned
        }
        val ifaces = LanAddresses.allIpv4Summary()
        error(
            "телефон не ответил на :8765. Часы: $ifaces. " +
                "Открой Health Sync на телефоне (уведомление «ждёт часы»), одна Wi-Fi, Wi-Fi на часах не выключай.",
        )
    }

    fun discover(context: Context, timeoutMs: Int = 3500): Pair<String, Int>? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("healthsync")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 700
                val probe = WatchSync.LAN_PROBE.toByteArray(StandardCharsets.UTF_8)
                val buf = ByteArray(256)
                val deadline = System.currentTimeMillis() + timeoutMs
                var lastSend = 0L
                while (System.currentTimeMillis() < deadline) {
                    if (System.currentTimeMillis() - lastSend > 600) {
                        for (dest in broadcastTargets()) {
                            runCatching {
                                sock.send(DatagramPacket(probe, probe.size, dest, WatchSync.LAN_UDP_PORT))
                            }
                        }
                        lastSend = System.currentTimeMillis()
                    }
                    val incoming = DatagramPacket(buf, buf.size)
                    try {
                        sock.receive(incoming)
                    } catch (_: Exception) {
                        continue
                    }
                    val msg = String(incoming.data, incoming.offset, incoming.length, StandardCharsets.UTF_8)
                    val parsed = LanLink.parseBeacon(msg)
                    if (parsed != null) return parsed
                }
                return null
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    fun scanSubnet(timeoutMs: Int = 4500): Pair<String, Int>? {
        val hosts = LinkedHashSet<String>()
        for (iface in LanAddresses.listUpIpv4()) {
            if (LanAddresses.isPrivateIpv4(iface.ip)) {
                hosts += LanAddresses.subnetHosts(iface.ip)
            }
        }
        if (hosts.isEmpty()) return null
        val pool = Executors.newFixedThreadPool(32)
        try {
            val jobs = hosts.map { host ->
                Callable {
                    if (ping(host, WatchSync.LAN_HTTP_PORT, 400)) host else null
                }
            }
            val done = pool.invokeAll(jobs, timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            for (f in done) {
                if (f.isCancelled || !f.isDone) continue
                val host = runCatching { f.get() }.getOrNull()
                if (!host.isNullOrBlank()) return host to WatchSync.LAN_HTTP_PORT
            }
            return null
        } finally {
            pool.shutdownNow()
        }
    }

    fun ping(ip: String, port: Int, timeoutMs: Int = 2000): Boolean {
        val url = URL("http://$ip:$port/ping")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.readBytes()
                ?.toString(Charsets.UTF_8)
                .orEmpty()
            code == 200 && (text.contains("pong") || text.contains(WatchSync.LAN_MAGIC))
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
