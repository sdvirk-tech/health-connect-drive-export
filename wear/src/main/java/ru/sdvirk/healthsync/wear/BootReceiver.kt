package ru.sdvirk.healthsync.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.sdvirk.healthsync.watch.WatchSync

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
            .getBoolean(WatchSync.KEY_PASSIVE_ENABLED, false)
        if (!enabled) return
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<RegisterPassiveWorker>().build()
        )
    }
}

class RegisterPassiveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        WatchHealth.registerPassive(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
