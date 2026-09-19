package ru.sdvirk.healthsync.link

import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

internal data class MiniHttpRequest(
    val method: String,
    val path: String,
    val body: ByteArray,
)

internal object MiniHttp {
    fun readRequest(socket: Socket): MiniHttpRequest {
        val input = BufferedInputStream(socket.getInputStream())
        val header = StringBuilder()
        val buf = ByteArray(1)
        var last4 = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            header.append(buf[0].toInt().toChar())
            last4 = ((last4 shl 8) or (buf[0].toInt() and 0xFF))
            if (header.endsWith("\r\n\r\n")) break
            if (header.length > 16_000) error("HTTP header too large")
        }
        val lines = header.toString().split("\r\n")
        val requestLine = lines.firstOrNull().orEmpty().split(" ")
        val method = requestLine.getOrElse(0) { "GET" }
        val path = requestLine.getOrElse(1) { "/" }
        val contentLength = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.toIntOrNull()
            ?: 0
        val body = if (contentLength <= 0) {
            ByteArray(0)
        } else {
            val bytes = ByteArray(contentLength.coerceAtMost(WatchBodyLimit))
            var off = 0
            while (off < bytes.size) {
                val n = input.read(bytes, off, bytes.size - off)
                if (n <= 0) break
                off += n
            }
            if (off == bytes.size) bytes else bytes.copyOf(off)
        }
        return MiniHttpRequest(method, path, body)
    }

    fun writeOk(socket: Socket, body: String = "OK") {
        write(socket, 200, "OK", body)
    }

    fun write(socket: Socket, code: Int, reason: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val header = "HTTP/1.0 $code $reason\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        val out: OutputStream = socket.getOutputStream()
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private const val WatchBodyLimit = 200_000
}
