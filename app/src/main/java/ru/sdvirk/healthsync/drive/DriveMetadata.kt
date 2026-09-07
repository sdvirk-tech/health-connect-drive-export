package ru.sdvirk.healthsync.drive

/**
 * JSON метаданных Drive API и разбор ответа — без Android SDK, чтобы покрыть юнит-тестами.
 */
internal object DriveMetadata {

    fun createFileJson(
        fileName: String,
        folderId: String = DriveConfig.FOLDER_ID,
        mimeType: String = DriveConfig.MIME_ZIP,
    ): String {
        val nameValue = AppsScriptPayload.toJsonObject(mapOf("name" to fileName))
            .removePrefix("{\"name\":")
            .removeSuffix("}")
        return "{\"name\":$nameValue,\"mimeType\":\"$mimeType\",\"parents\":[\"$folderId\"]}"
    }

    fun summarizeUpload(json: String): String {
        if (json.isBlank() || json == "ok") return "ok"
        val name = jsonStringField(json, "name")
        val id = jsonStringField(json, "id")
        val link = jsonStringField(json, "webViewLink")
        val parts = listOfNotNull(name, id?.let { "id=$it" }, link)
        return if (parts.isEmpty()) json.take(300) else parts.joinToString(" · ")
    }

    fun jsonStringField(json: String, key: String): String? {
        val needle = "\"$key\":\""
        val start = json.indexOf(needle)
        if (start < 0) return null
        val from = start + needle.length
        val out = StringBuilder()
        var i = from
        while (i < json.length) {
            val ch = json[i]
            if (ch == '\\' && i + 1 < json.length) {
                out.append(json[i + 1])
                i += 2
                continue
            }
            if (ch == '"') break
            out.append(ch)
            i++
        }
        return out.toString().ifEmpty { null }
    }
}
