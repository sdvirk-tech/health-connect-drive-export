package ru.sdvirk.healthsync.drive

/**
 * Целевая папка Drive и OAuth-scope.
 *
 * `drive.file` не подходит как единственный scope: приложение не создавало эту папку
 * и пользователь не открывал её через Picker, поэтому parent id вернёт 404.
 * Нужен `drive`, чтобы писать в уже существующую папку.
 */
object DriveConfig {
    const val FOLDER_ID = "10pwzTmlxVLAshc7_DfOuecSktde-HKjw"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    const val MIME_ZIP = "application/zip"
    const val UPLOAD_URL =
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,webViewLink&supportsAllDrives=true"
    const val FILE_GET_URL = "https://www.googleapis.com/drive/v3/files/"
}
