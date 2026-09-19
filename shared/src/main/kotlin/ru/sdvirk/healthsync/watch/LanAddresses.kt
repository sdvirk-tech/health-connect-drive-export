package ru.sdvirk.healthsync.watch

import java.net.Inet4Address
import java.net.NetworkInterface

data class LanIface(val name: String, val ip: String)

object LanAddresses {

    fun listUpIpv4(): List<LanIface> {
        val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        val out = ArrayList<LanIface>()
        for (nif in ifaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress ?: continue
                    out += LanIface(nif.name.orEmpty(), host)
                }
            }
        }
        return out
    }

    fun preferredIpv4(): String? =
        listUpIpv4().minByOrNull { rank(it.name, it.ip) }?.ip

    fun allIpv4Summary(): String {
        val list = listUpIpv4()
        if (list.isEmpty()) return "нет IPv4"
        val preferred = preferredIpv4()
        return list.joinToString { iface ->
            val mark = if (iface.ip == preferred) "*" else ""
            "${iface.name}=${iface.ip}$mark"
        }
    }

    fun isPrivateIpv4(ip: String): Boolean {
        val p = parse(ip) ?: return false
        val a = p[0]
        val b = p[1]
        return a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31)
    }

    fun subnetHosts(ip: String): List<String> {
        val p = parse(ip) ?: return emptyList()
        val prefix = "${p[0]}.${p[1]}.${p[2]}."
        return (1..254).map { prefix + it }.filter { it != ip }
    }

    fun rank(iface: String, ip: String): Int {
        val n = iface.lowercase()
        if (n == "lo" || n.startsWith("lo")) return 1000
        if (ip.startsWith("169.254.")) return 900
        var r = 100
        if (n.contains("wlan") || n.contains("wifi") || n.contains("wl") ||
            n.contains("ap0") || n.contains("swlan")
        ) {
            r -= 50
        }
        if (n.contains("rmnet") || n.contains("ccmni") || n.contains("radio") ||
            n.contains("mobile") || n.contains("clat") || n.contains("wwan")
        ) {
            r += 200
        }
        if (n.contains("tun") || n.contains("wg") || n.contains("vpn") ||
            n.contains("ipsec") || n.contains("dummy")
        ) {
            r += 150
        }
        if (isPrivateIpv4(ip)) r -= 20 else r += 80
        return r
    }

    private fun parse(ip: String): IntArray? {
        val parts = ip.split('.')
        if (parts.size != 4) return null
        val nums = IntArray(4)
        for (i in 0..3) {
            val n = parts[i].toIntOrNull() ?: return null
            if (n !in 0..255) return null
            nums[i] = n
        }
        return nums
    }
}
