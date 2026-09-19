package ru.sdvirk.healthsync.watch

import java.io.InputStream
import java.io.OutputStream

object BtFrame {
    const val MAX = 200_000

    fun write(out: OutputStream, payload: ByteArray) {
        require(payload.size <= MAX) { "payload ${payload.size}" }
        val header = ByteArray(4)
        header[0] = (payload.size ushr 24).toByte()
        header[1] = (payload.size ushr 16).toByte()
        header[2] = (payload.size ushr 8).toByte()
        header[3] = payload.size.toByte()
        out.write(header)
        out.write(payload)
        out.flush()
    }

    fun read(input: InputStream): ByteArray? {
        val header = ByteArray(4)
        if (!readFully(input, header)) return null
        val len = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        if (len !in 1..MAX) return null
        val body = ByteArray(len)
        if (!readFully(input, body)) return null
        return body
    }

    private fun readFully(input: InputStream, dest: ByteArray): Boolean {
        var off = 0
        while (off < dest.size) {
            val n = input.read(dest, off, dest.size - off)
            if (n <= 0) return false
            off += n
        }
        return true
    }
}
