package ru.sdvirk.healthsync.wear

import android.content.Context
import ru.sdvirk.healthsync.watch.WatchSync
import java.io.File
import java.time.Instant

object WatchDiagStore {

    fun save(context: Context, text: String) {
        val trimmed = text.trim().ifBlank { return }
        val now = System.currentTimeMillis()
        context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
            .edit()
            .putString(WatchSync.KEY_LAST_WATCH_DIAG, trimmed)
            .putLong(WatchSync.KEY_LAST_WATCH_DIAG_MS, now)
            .apply()
        val file = file(context)
        file.parentFile?.mkdirs()
        file.appendText(
            "----- ${Instant.ofEpochMilli(now)} -----\n$trimmed\n\n",
            Charsets.UTF_8,
        )
        if (file.length() > 400_000) {
            val keep = file.readText(Charsets.UTF_8).takeLast(200_000)
            file.writeText(keep, Charsets.UTF_8)
        }
    }

    fun last(context: Context): String =
        context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
            .getString(WatchSync.KEY_LAST_WATCH_DIAG, "")
            .orEmpty()

    fun lastMs(context: Context): Long =
        context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
            .getLong(WatchSync.KEY_LAST_WATCH_DIAG_MS, 0L)

    fun file(context: Context): File = File(context.filesDir, WatchSync.DIAG_FILE)

    fun asProbeLines(context: Context): List<String> {
        val text = last(context)
        val ms = lastMs(context)
        if (text.isBlank() || ms == 0L) {
            return listOf("Лог часов «Почему пусто»: ещё не приходил. На часах: Почему пусто → Лог на телефон.")
        }
        return listOf("Лог часов ${Instant.ofEpochMilli(ms)}:", text)
    }
}
