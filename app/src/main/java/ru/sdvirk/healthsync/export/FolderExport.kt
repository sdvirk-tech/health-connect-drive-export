package ru.sdvirk.healthsync.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FolderExport {

    fun copyZip(context: Context, zip: File, treeUri: Uri): String {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Папка недоступна. Выбери директорию снова.")
        val existing = tree.findFile(zip.name)
        existing?.delete()
        val dest = tree.createFile("application/zip", zip.name)
            ?: error("Не удалось создать ${zip.name} в выбранной папке")
        context.contentResolver.openOutputStream(dest.uri)?.use { out ->
            zip.inputStream().use { it.copyTo(out) }
        } ?: error("Нет потока записи в папку")
        return dest.uri.toString()
    }
}
