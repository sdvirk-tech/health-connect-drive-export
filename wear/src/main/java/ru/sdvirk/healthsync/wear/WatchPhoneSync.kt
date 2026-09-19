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
import ru.sdvirk.healthsync.watch.WatchNodePicker
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec
import ru.sdvirk.healthsync.watch.WearNodeRef
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WatchPhoneSync {

    fun enqueue(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "watch_phone_sync",
            ExistingWorkPolicy.REPLACE,
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

        val node = findPhone(app)
        val nodeId = node?.id ?: error(lastLinkDetail ?: "Телефон не рядом. Открой Health Sync на телефоне.")
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

    suspend fun sendDiag(context: Context, text: String) {
        val app = context.applicationContext
        val node = findPhone(app)
            ?: error(lastLinkDetail ?: "Телефон не рядом. Открой Health Sync на телефоне.")
        var bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > WatchSync.MAX_DIAG_BYTES) {
            bytes = text.take(WatchSync.MAX_DIAG_BYTES / 2).toByteArray(Charsets.UTF_8)
        }
        val code = Wearable.getMessageClient(app)
            .sendMessage(node.id, WatchSync.PATH_DIAG, bytes)
            .awaitTask()
        if (code < 0) error("Wear Data Layer не принял лог")
    }

    suspend fun linkStatus(context: Context): String {
        findPhone(context.applicationContext)
        return lastLinkDetail ?: "телефон не найден"
    }

    @Volatile
    private var lastLinkDetail: String? = null

    private suspend fun findPhone(context: Context): WearNodeRef? {
        val capNodes = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WatchSync.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .awaitTask()
                .nodes
                .map { WearNodeRef(it.id, it.isNearby, it.displayName) }
        }.getOrDefault(emptyList())
        val connected = runCatching {
            Wearable.getNodeClient(context).connectedNodes.awaitTask()
                .map { WearNodeRef(it.id, it.isNearby, it.displayName) }
        }.getOrDefault(emptyList())
        val picked = WatchNodePicker.pick(capNodes, connected)
        lastLinkDetail = if (picked == null) {
            "телефон не найден (capability=${capNodes.size}, connected=${connected.size}). Открой Health Sync на телефоне, Bluetooth вкл."
        } else if (capNodes.none { it.id == picked.id }) {
            "телефон ${picked.displayName} без capability healthsync_phone — поставь Health Sync 0.3.2 на телефон"
        } else {
            "телефон ${picked.displayName}" + if (picked.nearby) " рядом" else ""
        }
        return picked
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
