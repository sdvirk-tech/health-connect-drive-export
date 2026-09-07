package ru.sdvirk.healthsync.drive

import java.util.Base64

/**
 * JSON-тело для Apps Script Web App: zip как base64.
 * Multipart в `doPost` почти всегда ломается, JSON + `e.postData.contents` — рабочий путь.
 */
internal object AppsScriptPayload {

    const val MIME_ZIP = "application/zip"

    fun encode(
        fileName: String,
        fileBytes: ByteArray,
        secret: String = "",
        mimeType: String = MIME_ZIP,
    ): String {
        val b64 = Base64.getEncoder().encodeToString(fileBytes)
        val fields = linkedMapOf(
            "fileName" to fileName,
            "mimeType" to mimeType,
            "fileBase64" to b64,
        )
        if (secret.isNotBlank()) {
            fields["secret"] = secret
        }
        return toJsonObject(fields)
    }

    fun toJsonObject(fields: Map<String, String>): String = buildString {
        append('{')
        fields.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"').append(escape(key)).append('"')
            append(':')
            append('"').append(escape(value)).append('"')
        }
        append('}')
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
