package ru.sdvirk.healthsync.watch

object LanLink {
    fun encodeBeacon(ip: String, port: Int = WatchSync.LAN_HTTP_PORT): String =
        "${WatchSync.LAN_MAGIC} $ip $port"

    fun parseBeacon(raw: String): Pair<String, Int>? {
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null
        if (parts[0] != WatchSync.LAN_MAGIC) return null
        val ip = parts[1]
        val port = parts[2].toIntOrNull() ?: return null
        if (ip.count { it == '.' } != 3) return null
        if (port !in 1..65535) return null
        return ip to port
    }
}
