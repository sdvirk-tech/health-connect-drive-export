package ru.sdvirk.healthsync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.sdvirk.healthsync.drive.DriveUploader
import ru.sdvirk.healthsync.export.JsonExporter
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
        val days = prefs.getInt(KEY_LOOKBACK_DAYS, 7).coerceIn(1, 90)

        val reader = HealthConnectReader(applicationContext)
        if (reader.client == null) return Result.retry()

        val end = Instant.now()
        val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val snapshot = reader.readSince(start, end)

        val out = File(applicationContext.cacheDir, "health_connect_export.zip")
        JsonExporter.writeZip(snapshot, out)

        val uploader = DriveUploader(uploadUrl, prefs.getString(KEY_SECRET, "") ?: "")
        return uploader.upload(out, "health_connect_export.zip").fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val UNIQUE = "daily_health_export"
        const val PREFS = "health_sync"
        const val KEY_UPLOAD_URL = "upload_url"
        const val KEY_SECRET = "upload_secret"
        const val KEY_LOOKBACK_DAYS = "lookback_days"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DailyExportWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }
    }
}
