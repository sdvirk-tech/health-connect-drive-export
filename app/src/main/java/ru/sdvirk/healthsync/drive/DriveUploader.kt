package ru.sdvirk.healthsync.drive

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Загрузка zip в папку Google Drive.
 *
 * По умолчанию — JSON + base64 для Apps Script Web App (multipart там почти не работает).
 * Для своего backend можно вызвать [uploadMultipart].
 */
class DriveUploader(
    private val uploadUrl: String,
    private val sharedSecret: String = "",
) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun upload(
        file: File,
        fileName: String = file.name,
        mode: Mode = Mode.APPS_SCRIPT_JSON,
    ): Result<String> = when (mode) {
        Mode.APPS_SCRIPT_JSON -> uploadAppsScriptJson(file, fileName)
        Mode.MULTIPART -> uploadMultipart(file, fileName)
    }

    fun uploadAppsScriptJson(file: File, fileName: String = file.name): Result<String> = runCatching {
        require(uploadUrl.isNotBlank()) { "Задай Upload URL (Apps Script Web App)" }
        require(file.exists() && file.length() > 0L) { "Zip пустой или не найден: ${file.absolutePath}" }

        val json = AppsScriptPayload.encode(
            fileName = fileName,
            fileBytes = file.readBytes(),
            secret = sharedSecret,
        )
        val body = json.toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(uploadUrl)
            .post(body)
            .header("Accept", "application/json, text/plain, */*")
            .build()
        executeKeepingPostOnRedirect(request)
    }

    fun uploadMultipart(file: File, fileName: String = file.name): Result<String> = runCatching {
        require(uploadUrl.isNotBlank()) { "Задай Upload URL" }
        require(file.exists() && file.length() > 0L) { "Zip пустой или не найден: ${file.absolutePath}" }

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

        val request = Request.Builder().url(uploadUrl).post(body).build()
        executeKeepingPostOnRedirect(request)
    }

    /**
     * Apps Script отвечает 302 на script.googleusercontent.com.
     * OkHttp по умолчанию превращает POST в GET и теряет тело — doPost не вызывается.
     */
    private fun executeKeepingPostOnRedirect(request: Request): String {
        var current = request
        var hops = 0
        while (true) {
            client.newCall(current).execute().use { response ->
                if (response.isRedirect && hops < MAX_REDIRECTS) {
                    val location = response.header("Location")
                        ?: error("HTTP ${response.code}: redirect без Location")
                    val nextUrl = current.url.resolve(location)
                        ?: error("Некорректный Location: $location")
                    current = current.newBuilder().url(nextUrl).build()
                    hops++
                    return@use
                }
                return readBody(response)
            }
        }
    }

    private fun readBody(response: Response): String {
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            error("HTTP ${response.code}: ${text.take(500)}")
        }
        if (text.contains("<html", ignoreCase = true) &&
            (text.contains("Sign in", ignoreCase = true) || text.contains("Google Account", ignoreCase = true))
        ) {
            error("Apps Script вернул страницу входа: в Deploy поставь Who has access = Anyone")
        }
        if (text.contains("\"ok\":false", ignoreCase = true) ||
            text.equals("forbidden", ignoreCase = true)
        ) {
            error(text.ifBlank { "Apps Script отклонил загрузку" })
        }
        return text.ifBlank { "ok" }
    }

    enum class Mode { APPS_SCRIPT_JSON, MULTIPART }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val MAX_REDIRECTS = 8
    }
}
