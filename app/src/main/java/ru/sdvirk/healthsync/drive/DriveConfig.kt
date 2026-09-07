package ru.sdvirk.healthsync.drive

import android.content.Context
import ru.sdvirk.healthsync.BuildConfig
import ru.sdvirk.healthsync.R

/**
 * Целевая папка Drive и OAuth-scope.
 *
 * Folder id is not hardcoded: set `drive.folder.id` in gitignored `local.properties`,
 * or copy `drive_folder.example.xml` → `drive_folder.xml` (also gitignored).
 *
 * `drive.file` не подходит как единственный scope: приложение не создавало эту папку
 * и пользователь не открывал её через Picker, поэтому parent id вернёт 404.
 * Нужен `drive`, чтобы писать в уже существующую папку.
 */
object DriveConfig {
    const val FOLDER_ID_PLACEHOLDER = "YOUR_DRIVE_FOLDER_ID"
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
    const val MIME_ZIP = "application/zip"
    const val UPLOAD_URL =
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name,webViewLink&supportsAllDrives=true"
    const val FILE_GET_URL = "https://www.googleapis.com/drive/v3/files/"

    fun folderId(context: Context): String {
        val fromBuild = BuildConfig.DRIVE_FOLDER_ID.trim()
        if (fromBuild.isNotEmpty() && fromBuild != FOLDER_ID_PLACEHOLDER) return fromBuild
        val fromRes = context.getString(R.string.drive_folder_id).trim()
        return fromRes.ifBlank { FOLDER_ID_PLACEHOLDER }
    }

    fun requireConfiguredFolderId(context: Context): String {
        val id = folderId(context)
        if (id.isBlank() || id == FOLDER_ID_PLACEHOLDER) {
            error(
                "Укажи ID своей папки Drive: local.properties (drive.folder.id) " +
                    "или app/src/main/res/values/drive_folder.xml (скопируй drive_folder.example.xml)"
            )
        }
        return id
    }
}
