package ru.sdvirk.healthsync.watch

object BtPayload {
    const val SAMPLES = 1
    const val DIAG = 2
    const val HEARTBEAT = 3
    const val ACK = 4

    fun pack(type: Int, body: String = ""): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val out = ByteArray(1 + bodyBytes.size)
        out[0] = type.toByte()
        System.arraycopy(bodyBytes, 0, out, 1, bodyBytes.size)
        return out
    }

    fun unpack(bytes: ByteArray): Pair<Int, String>? {
        if (bytes.isEmpty()) return null
        val type = bytes[0].toInt() and 0xFF
        val body = if (bytes.size == 1) {
            ""
        } else {
            String(bytes, 1, bytes.size - 1, Charsets.UTF_8)
        }
        return type to body
    }
}
