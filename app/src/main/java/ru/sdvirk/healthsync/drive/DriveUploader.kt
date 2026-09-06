package ru.sdvirk.healthsync.drive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Загрузка zip в папку Google Drive.
 *
 * Вариант A (проще для MVP): залить через Apps Script Web App URL
 *   (скрипт принимает multipart и кладёт файл в folderId).
 * Вариант B: OAuth + Drive API (нужен clientId и refresh token на телефоне).
 *
 * Сейчас: HTTP POST на URL из BuildConfig / prefs. Подставь свой endpoint.
 */
class DriveUploader(
    private val uploadUrl: String,
    private val sharedSecret: String = "",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun upload(file: File, fileName: String = file.name): Result<String> = runCatching {
        require(uploadUrl.isNotBlank()) { "Задай uploadUrl (Apps Script или свой backend)" }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                file.asRequestBody("application/zip".toMediaType())
            )
            .apply {
                if (sharedSecret.isNotBlank()) addFormDataPart("secret", sharedSecret)
            }
            .build()

        val req = Request.Builder().url(uploadUrl).post(body).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: $text")
            text.ifBlank { "ok" }
        }
    }
}
