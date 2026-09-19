package ru.sdvirk.healthsync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.sdvirk.healthsync.drive.DriveUploader
import ru.sdvirk.healthsync.export.ExportFileNames
import ru.sdvirk.healthsync.export.FolderExport
import ru.sdvirk.healthsync.export.JsonExporter
import ru.sdvirk.healthsync.export.withWatchSamples
import ru.sdvirk.healthsync.health.HealthConnectReader
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class DailyExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uploadUrl = prefs.getString(KEY_UPLOAD_URL, "") ?: ""
        val tree = prefs.getString(KEY_EXPORT_TREE, "") ?: ""

        val days = prefs.getInt(KEY_LOOKBACK_DAYS, 7).coerceIn(1, 90)
        val reader = HealthConnectReader(applicationContext)
        val end = Instant.now()
        val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val snapshot = if (reader.client != null) {
            reader.readSince(start, end).withWatchSamples(applicationContext)
        } else {
            ru.sdvirk.healthsync.health.HealthSnapshot.empty()
                .copy(rangeStart = start, rangeEnd = end, exportedAt = end)
                .withWatchSamples(applicationContext)
        }

        val fileName = ExportFileNames.zipName()
        val out = File(applicationContext.cacheDir, fileName)
        JsonExporter.writeZip(snapshot, out)

        var copied = false
        if (tree.isNotBlank()) {
            runCatching {
                FolderExport.copyZip(applicationContext, out, android.net.Uri.parse(tree))
                copied = true
            }
        }

        if (uploadUrl.isBlank()) return Result.success()

        val uploader = DriveUploader(uploadUrl, prefs.getString(KEY_SECRET, "") ?: "")
        return uploader.upload(out, fileName).fold(
            onSuccess = { Result.success() },
            onFailure = { if (copied) Result.success() else Result.retry() }
        )
    }

    companion object {
        const val UNIQUE = "daily_health_export"
        const val PREFS = "health_sync"
        const val KEY_UPLOAD_URL = "upload_url"
        const val KEY_SECRET = "upload_secret"
        const val KEY_LOOKBACK_DAYS = "lookback_days"
        const val KEY_EXPORT_TREE = "export_tree_uri"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyExportWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}
