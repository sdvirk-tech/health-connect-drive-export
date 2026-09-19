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

        val errors = ArrayList<String>()
        val json = WatchSyncCodec.encodeMessage(pending)
        val lan = runCatching {
            val (ip, port) = resolveLan(app)
            WatchLanClient.post(ip, port, "/samples", json)
            ip
        }
        if (lan.isSuccess) {
            markSynced(prefs, store, from, pending)
            return pending.size
        }
        errors += "Wi-Fi: " + (lan.exceptionOrNull()?.message ?: "fail")

        val wear = runCatching {
            val node = findPhone(app)
                ?: error(lastLinkDetail ?: "Wear Data Layer: телефон не найден")
            val client = Wearable.getMessageClient(app)
            for (chunk in WatchSyncCodec.chunk(pending)) {
                val code = client.sendMessage(node.id, WatchSync.PATH_SAMPLES, chunk).awaitTask()
                if (code < 0) error("Wear Data Layer не принял сообщение")
            }
        }
        if (wear.isSuccess) {
            markSynced(prefs, store, from, pending)
            return pending.size
        }
        errors += "Bluetooth: " + (wear.exceptionOrNull()?.message ?: "fail")
        error(errors.joinToString(" | "))
    }

    suspend fun sendDiag(context: Context, text: String): String {
        val app = context.applicationContext
        val payload = if (text.toByteArray(Charsets.UTF_8).size > WatchSync.MAX_DIAG_BYTES) {
            text.take(WatchSync.MAX_DIAG_BYTES / 2)
        } else {
            text
        }
        val errors = ArrayList<String>()
        val lan = runCatching {
            val (ip, port) = resolveLan(app)
            WatchLanClient.post(ip, port, "/diag", payload)
            "Wi-Fi $ip:$port"
        }
        if (lan.isSuccess) return lan.getOrThrow()
        errors += "Wi-Fi: " + (lan.exceptionOrNull()?.message ?: "fail")

        val wear = runCatching {
            val node = findPhone(app)
                ?: error(lastLinkDetail ?: "Wear Data Layer: телефон не найден")
            val code = Wearable.getMessageClient(app)
                .sendMessage(node.id, WatchSync.PATH_DIAG, payload.toByteArray(Charsets.UTF_8))
                .awaitTask()
            if (code < 0) error("Wear Data Layer не принял лог")
            "Bluetooth Wear Data Layer"
        }
        if (wear.isSuccess) return wear.getOrThrow()
        errors += "Bluetooth: " + (wear.exceptionOrNull()?.message ?: "fail")
        error(
            "Связи нет. Оставьте Health Sync открытым на телефоне, часы и телефон в одной Wi-Fi. " +
                errors.joinToString(" | ")
        )
    }

    private fun markSynced(
        prefs: android.content.SharedPreferences,
        store: WatchSampleStore,
        from: Long,
        pending: List<ru.sdvirk.healthsync.watch.WatchSample>,
    ) {
        val maxT = pending.maxOf { it.timeEpochMs }
        prefs.edit().putLong(WatchSync.KEY_SYNCED_THROUGH_MS, maxOf(from, maxT)).apply()
        store.pruneOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
    }

    private fun resolveLan(context: Context): Pair<String, Int> {
        val prefs = context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
        val found = WatchLanClient.discover()
        if (found != null) {
            prefs.edit().putString(WatchSync.KEY_LAST_LAN_IP, found.first).apply()
            return found
        }
        val last = prefs.getString(WatchSync.KEY_LAST_LAN_IP, null)
        if (!last.isNullOrBlank() && WatchLanClient.ping(last, WatchSync.LAN_HTTP_PORT)) {
            return last to WatchSync.LAN_HTTP_PORT
        }
        error("телефон не ответил по Wi-Fi. Health Sync на телефоне должен быть открыт, одна сеть.")
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
            "телефон ${picked.displayName} без capability healthsync_phone — поставь Health Sync 0.3.3 на телефон"
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
