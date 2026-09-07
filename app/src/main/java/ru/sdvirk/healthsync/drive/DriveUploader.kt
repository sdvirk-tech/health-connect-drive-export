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
 * Основной путь — Drive REST API с OAuth access token.
 * Apps Script Web App остаётся опциональным fallback (POST после 302 часто даёт HTTP 405).
 */
class DriveUploader(
    private val uploadUrl: String = "",
    private val sharedSecret: String = "",
    private val accessToken: String? = null,
    private val folderId: String = DriveConfig.FOLDER_ID,
) {
    private val appsScriptClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val driveClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun upload(
        file: File,
        fileName: String = file.name,
        mode: Mode = Mode.AUTO,
    ): Result<String> = when (mode) {
        Mode.AUTO -> when {
            !accessToken.isNullOrBlank() -> uploadDriveApi(file, fileName)
            else -> uploadAppsScriptJson(file, fileName)
        }
        Mode.DRIVE_API -> uploadDriveApi(file, fileName)
        Mode.APPS_SCRIPT_JSON -> uploadAppsScriptJson(file, fileName)
        Mode.MULTIPART -> uploadMultipart(file, fileName)
    }

    fun verifyFolder(): Result<String> = runCatching {
        val token = accessToken?.takeIf { it.isNotBlank() }
            ?: error("Нет OAuth access token")
        val url = DriveConfig.FILE_GET_URL + folderId + "?fields=id,name&supportsAllDrives=true"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        driveClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Папка Drive HTTP ${response.code}: ${text.take(400)}")
            }
            val name = DriveMetadata.jsonStringField(text, "name") ?: folderId
            name
        }
    }

    fun uploadDriveApi(file: File, fileName: String = file.name): Result<String> = runCatching {
        val token = accessToken?.takeIf { it.isNotBlank() }
            ?: error("Войди в Google, чтобы загрузить в Drive")
        require(file.exists() && file.length() > 0L) { "Zip пустой или не найден: ${file.absolutePath}" }

        val meta = DriveMetadata.createFileJson(fileName, folderId)
        val init = Request.Builder()
            .url(DriveConfig.UPLOAD_URL)
            .header("Authorization", "Bearer $token")
            .header("X-Upload-Content-Type", DriveConfig.MIME_ZIP)
            .header("X-Upload-Content-Length", file.length().toString())
            .post(meta.toRequestBody(JSON_MEDIA))
            .build()

        val sessionUrl = driveClient.newCall(init).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Drive API HTTP ${response.code}: ${text.take(500)}")
            }
            response.header("Location")
                ?: error("Drive API: нет Location для resumable upload")
        }

        val put = Request.Builder()
            .url(sessionUrl)
            .header("Authorization", "Bearer $token")
            .put(file.asRequestBody(ZIP_MEDIA))
            .build()

        driveClient.newCall(put).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Drive API HTTP ${response.code}: ${text.take(500)}")
            }
            DriveMetadata.summarizeUpload(text.ifBlank { "ok" })
        }
    }

    fun uploadAppsScriptJson(file: File, fileName: String = file.name): Result<String> = runCatching {
        require(uploadUrl.isNotBlank()) { "Задай Upload URL (Apps Script Web App) или войди в Google" }
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
                file.asRequestBody(ZIP_MEDIA)
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
     * Даже с сохранением POST финальный /macros/echo часто отвечает 405 — поэтому основной путь это Drive API.
     */
    private fun executeKeepingPostOnRedirect(request: Request): String {
        var current = request
        var hops = 0
        while (true) {
            appsScriptClient.newCall(current).execute().use { response ->
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

    enum class Mode { AUTO, DRIVE_API, APPS_SCRIPT_JSON, MULTIPART }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val ZIP_MEDIA = DriveConfig.MIME_ZIP.toMediaType()
        const val MAX_REDIRECTS = 8
    }
}
