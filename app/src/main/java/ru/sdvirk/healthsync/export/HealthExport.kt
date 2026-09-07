package ru.sdvirk.healthsync.export

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.sdvirk.healthsync.R
import ru.sdvirk.healthsync.drive.DriveUploader
import ru.sdvirk.healthsync.drive.GoogleDriveAuth
import ru.sdvirk.healthsync.health.HealthConnectReader
import ru.sdvirk.healthsync.worker.DailyExportWorker
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthExport(private val context: Context) {

    data class Outcome(
        val summary: String,
        val zipFile: File,
        val fileName: String,
        /** null — в Drive не отправляли (нет Google-аккаунта и нет Apps Script URL). */
        val upload: Result<String>?,
    )

    suspend fun run(lookbackDays: Int = 7): Outcome {
        val reader = HealthConnectReader(context)
        val sdk = reader.availability()
        if (sdk != HealthConnectClient.SDK_AVAILABLE) {
            val msg = when (sdk) {
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    context.getString(R.string.hc_update_required)
                else -> context.getString(R.string.hc_unavailable)
            }
            error(msg)
        }

        val days = lookbackDays.coerceIn(1, 90)
        val end = Instant.now()
        val start = end.minus(days.toLong(), ChronoUnit.DAYS)
        val snap = reader.readSince(start, end)
        val fileName = ExportFileNames.zipName()
        val out = File(context.cacheDir, fileName)
        JsonExporter.writeZip(snap, out)

        val prefs = context.getSharedPreferences(DailyExportWorker.PREFS, Context.MODE_PRIVATE)
        val token = GoogleDriveAuth.silentAccessToken(context)
        val url = prefs.getString(DailyExportWorker.KEY_UPLOAD_URL, "") ?: ""
        val secret = prefs.getString(DailyExportWorker.KEY_SECRET, "") ?: ""

        if (token.isNullOrBlank() && url.isBlank()) {
            return Outcome(snap.summaryLines().joinToString(" · "), out, fileName, upload = null)
        }

        val upload = withContext(Dispatchers.IO) {
            DriveUploader(
                uploadUrl = url,
                sharedSecret = secret,
                accessToken = token,
            ).upload(out, fileName)
        }
        return Outcome(snap.summaryLines().joinToString(" · "), out, fileName, upload)
    }
}
