package ru.sdvirk.healthsync.wear

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WatchPhoneSync {

    fun enqueue(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "watch_phone_sync",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WatchSyncWorker>().build()
        )
    }

    suspend fun syncNow(context: Context): Int {
        val app = context.applicationContext
        val store = WatchSampleStore.at(app.filesDir)
        val prefs = app.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
        val from = prefs.getLong(WatchSync.KEY_SYNCED_THROUGH_MS, 0L)
        val pending = store.readAfter(from)
        if (pending.isEmpty()) return 0

        val nodeId = findPhoneNode(app) ?: error("Телефон не рядом. Открой Health Sync на телефоне.")
        val client = Wearable.getMessageClient(app)
        var maxT = from
        for (chunk in WatchSyncCodec.chunk(pending)) {
            val samples = WatchSyncCodec.decodeMessage(chunk.toString(Charsets.UTF_8))
            val code = client.sendMessage(nodeId, WatchSync.PATH_SAMPLES, chunk).awaitTask()
            if (code < 0) error("Wear Data Layer не принял сообщение")
            maxT = maxOf(maxT, samples.maxOf { it.timeEpochMs })
        }
        prefs.edit().putLong(WatchSync.KEY_SYNCED_THROUGH_MS, maxT).apply()
        store.pruneOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
        return pending.size
    }

    private suspend fun findPhoneNode(context: Context): String? {
        val cap = Wearable.getCapabilityClient(context)
            .getCapability(WatchSync.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
            .awaitTask()
        val nearby = cap.nodes.firstOrNull { it.isNearby }
        return (nearby ?: cap.nodes.firstOrNull())?.id
    }
}

class WatchSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        WatchPhoneSync.syncNow(applicationContext)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            when {
                task.isCanceled -> cont.cancel()
                task.isSuccessful -> cont.resume(task.result)
                else -> cont.resumeWithException(task.exception ?: RuntimeException("GMS task failed"))
            }
        }
    }
