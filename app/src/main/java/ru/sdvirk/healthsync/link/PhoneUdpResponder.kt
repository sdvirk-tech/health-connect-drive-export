package ru.sdvirk.healthsync.link

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import ru.sdvirk.healthsync.watch.LanAddresses
import ru.sdvirk.healthsync.watch.LanLink
import ru.sdvirk.healthsync.watch.WatchSync
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class PhoneUdpResponder(private val context: Context) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null
    private var multicast: WifiManager.MulticastLock? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicast = wifi?.createMulticastLock("healthsync")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            try {
                DatagramSocket(null).use { sock ->
                    sock.reuseAddress = true
                    sock.broadcast = true
                    sock.bind(InetSocketAddress(WatchSync.LAN_UDP_PORT))
                    sock.soTimeout = 2_000
                    socket = sock
                    val buf = ByteArray(256)
                    while (running.get()) {
                        val ip = LanAddresses.preferredIpv4()
                        if (ip != null) {
                            broadcast(sock, ip)
                        }
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            sock.receive(packet)
                        } catch (_: Exception) {
                            continue
                        }
                        val msg = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                        if (msg != WatchSync.LAN_PROBE && !msg.startsWith(WatchSync.LAN_MAGIC)) continue
                        val replyIp = LanAddresses.preferredIpv4() ?: continue
                        val reply = LanLink.encodeBeacon(replyIp).toByteArray(StandardCharsets.UTF_8)
                        sock.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        broadcast(sock, replyIp)
                    }
                }
            } catch (e: Exception) {
                Log.w("PhoneUdpResponder", e)
            } finally {
                running.set(false)
                runCatching { multicast?.release() }
                multicast = null
            }
        }, "healthsync-udp").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        thread = null
        runCatching { multicast?.release() }
        multicast = null
    }

    private fun broadcast(sock: DatagramSocket, ip: String) {
        val payload = LanLink.encodeBeacon(ip).toByteArray(StandardCharsets.UTF_8)
        val dests = LinkedHashSet<InetAddress>()
        runCatching { dests += InetAddress.getByName("255.255.255.255") }
        runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces()
            if (ifaces != null) {
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
        }
        for (dest in dests) {
            runCatching {
                sock.send(DatagramPacket(payload, payload.size, dest, WatchSync.LAN_UDP_PORT))
            }
        }
    }
}
