package ru.sdvirk.healthsync.link

import android.util.Log
import ru.sdvirk.healthsync.watch.LanLink
import ru.sdvirk.healthsync.watch.WatchSync
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class PhoneUdpResponder {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({
            try {
                DatagramSocket(null).use { sock ->
                    sock.reuseAddress = true
                    sock.bind(java.net.InetSocketAddress(WatchSync.LAN_UDP_PORT))
                    socket = sock
                    sock.broadcast = true
                    val buf = ByteArray(256)
                    while (running.get()) {
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            sock.receive(packet)
                        } catch (_: Exception) {
                            if (!running.get()) break
                            continue
                        }
                        val msg = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8).trim()
                        if (msg != WatchSync.LAN_PROBE && !msg.startsWith(WatchSync.LAN_MAGIC)) continue
                        val ip = PhoneLogServer.localIpv4() ?: continue
                        val reply = LanLink.encodeBeacon(ip).toByteArray(StandardCharsets.UTF_8)
                        sock.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        broadcast(sock, ip)
                    }
                }
            } catch (e: Exception) {
                Log.w("PhoneUdpResponder", e)
            } finally {
                running.set(false)
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
