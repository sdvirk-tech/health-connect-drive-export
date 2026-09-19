package ru.sdvirk.healthsync.link

import android.util.Log
import ru.sdvirk.healthsync.watch.WatchSync
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class PhoneLogServer(
    private val onDiag: (String) -> Unit,
    private val onSamples: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({
            try {
                ServerSocket().use { ss ->
                    ss.reuseAddress = true
                    ss.bind(java.net.InetSocketAddress(WatchSync.LAN_HTTP_PORT))
                    server = ss
                    while (running.get()) {
                        val socket = try {
                            ss.accept()
                        } catch (_: Exception) {
                            break
                        }
                        socket.use { s ->
                            try {
                                s.soTimeout = 8_000
                                val req = MiniHttp.readRequest(s)
                                when {
                                    req.method == "GET" && req.path.startsWith("/ping") ->
                                        MiniHttp.writeOk(s, "pong")
                                    req.method == "POST" && req.path.startsWith("/diag") -> {
                                        onDiag(req.body.toString(Charsets.UTF_8))
                                        MiniHttp.writeOk(s, "diag-ok")
                                    }
                                    req.method == "POST" && req.path.startsWith("/samples") -> {
                                        onSamples(req.body.toString(Charsets.UTF_8))
                                        MiniHttp.writeOk(s, "samples-ok")
                                    }
                                    else -> MiniHttp.write(s, 404, "Not Found", "no")
                                }
                            } catch (e: Exception) {
                                Log.w("PhoneLogServer", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PhoneLogServer", e)
            } finally {
                running.set(false)
            }
        }, "healthsync-http").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        thread = null
    }

    companion object {
        fun localIpv4(): String? {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in ifaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            return null
        }
    }
}
