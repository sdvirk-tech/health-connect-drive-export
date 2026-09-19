package ru.sdvirk.healthsync.wear

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.sdvirk.healthsync.watch.WatchNodePicker
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec
import ru.sdvirk.healthsync.watch.WearNodeRef
import java.util.concurrent.TimeUnit
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

    fun schedule(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "watch_phone_sync_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchSyncWorker>(15, TimeUnit.MINUTES).build(),
        )
        enqueue(app)
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

        val nearby = runCatching {
            WatchBtNearby.start(app)
            if (!WatchBtNearby.isConnected()) WatchBtNearby.waitUntilConnected(6_000)
            if (!WatchBtNearby.isConnected()) error(WatchBtNearby.lastStatus)
            for (chunk in WatchSyncCodec.chunk(pending, WatchSync.NEARBY_MAX_BYTES)) {
                val body = chunk.toString(Charsets.UTF_8)
                if (!WatchBtNearby.sendSamples(body)) error("Bluetooth Nearby не принял пробы")
            }
            "Bluetooth Nearby"
        }
        if (nearby.isSuccess) {
            markSynced(prefs, store, from, pending)
            putDataLayer(app, WatchSync.PATH_SAMPLES, "json", json)
            return pending.size
        }
        errors += nearby.exceptionOrNull()?.message ?: "Nearby fail"

        val dataLayer = runCatching {
            putDataLayer(app, WatchSync.PATH_SAMPLES, "json", json)
            val node = findPhone(app)
            if (node != null) {
                val client = Wearable.getMessageClient(app)
                for (chunk in WatchSyncCodec.chunk(pending, WatchSync.NEARBY_MAX_BYTES)) {
                    val code = client.sendMessage(node.id, WatchSync.PATH_SAMPLES, chunk).awaitTask()
                    if (code < 0) error("Wear Data Layer не принял сообщение")
                }
                "Bluetooth Data Layer ${node.displayName}"
            } else if (lastConnectedCount > 0) {
                "Bluetooth Data Layer очередь"
            } else {
                error(lastLinkDetail ?: "Wear Data Layer: телефон не найден")
            }
        }
        if (dataLayer.isSuccess) {
            markSynced(prefs, store, from, pending)
            return pending.size
        }
        errors += dataLayer.exceptionOrNull()?.message ?: "Data Layer fail"

        val lan = runCatching {
            val (ip, port) = WatchLanClient.findPhoneHttp(app)
            WatchLanClient.post(ip, port, "/samples", json)
            "Wi-Fi $ip:$port"
        }
        if (lan.isSuccess) {
            markSynced(prefs, store, from, pending)
            return pending.size
        }
        errors += lan.exceptionOrNull()?.message ?: "Wi-Fi fail"
        error("Bluetooth не доставил. " + errors.joinToString(" | "))
    }

    suspend fun sendDiag(context: Context, text: String): String {
        val app = context.applicationContext
        val payload = if (text.toByteArray(Charsets.UTF_8).size > WatchSync.MAX_DIAG_BYTES) {
            text.take(WatchSync.MAX_DIAG_BYTES / 2)
        } else {
            text
        }
        val errors = ArrayList<String>()

        val nearby = runCatching {
            WatchBtNearby.start(app)
            if (!WatchBtNearby.isConnected()) WatchBtNearby.waitUntilConnected(8_000)
            if (!WatchBtNearby.sendDiag(payload)) error(WatchBtNearby.lastStatus)
            "Bluetooth Nearby"
        }
        if (nearby.isSuccess) {
            putDataLayer(app, WatchSync.PATH_DIAG, "text", payload)
            return nearby.getOrThrow()
        }
        errors += "Nearby: " + (nearby.exceptionOrNull()?.message ?: "fail")

        val wear = runCatching {
            putDataLayer(app, WatchSync.PATH_DIAG, "text", payload)
            val node = findPhone(app)
                ?: error(lastLinkDetail ?: "Wear Data Layer: телефон не найден")
            val code = Wearable.getMessageClient(app)
                .sendMessage(node.id, WatchSync.PATH_DIAG, payload.toByteArray(Charsets.UTF_8))
                .awaitTask()
            if (code < 0) error("Wear Data Layer не принял лог")
            "Bluetooth Data Layer"
        }
        if (wear.isSuccess) return wear.getOrThrow()
        errors += "Data Layer: " + (wear.exceptionOrNull()?.message ?: "fail")

        val lan = runCatching {
            val (ip, port) = WatchLanClient.findPhoneHttp(app)
            WatchLanClient.post(ip, port, "/diag", payload)
            "Wi-Fi $ip:$port"
        }
        if (lan.isSuccess) return lan.getOrThrow()
        errors += "Wi-Fi: " + (lan.exceptionOrNull()?.message ?: "fail")
        error(
            "Bluetooth не доставил лог. Держи Health Sync открытым на телефоне, Bluetooth вкл. " +
                errors.joinToString(" | ")
        )
    }

    private suspend fun putDataLayer(context: Context, path: String, key: String, value: String) {
        runCatching {
            val req = PutDataMapRequest.create(path)
            req.dataMap.putString(key, value)
            req.dataMap.putLong("t", System.currentTimeMillis())
            Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent()).awaitTask()
        }
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

    suspend fun linkStatus(context: Context): String {
        findPhone(context.applicationContext)
        val nearby = WatchBtNearby.lastStatus + if (WatchBtNearby.isConnected()) " (есть канал)" else ""
        return "$nearby; Data Layer: ${lastLinkDetail ?: "телефон не найден"}"
    }

    @Volatile
    private var lastLinkDetail: String? = null

    @Volatile
    private var lastConnectedCount: Int = 0

    private suspend fun findPhone(context: Context): WearNodeRef? {
        val capReachable = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WatchSync.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .awaitTask()
                .nodes
                .map { WearNodeRef(it.id, it.isNearby, it.displayName) }
        }.getOrDefault(emptyList())
        val capAll = runCatching {
            Wearable.getCapabilityClient(context)
                .getCapability(WatchSync.CAPABILITY_PHONE, CapabilityClient.FILTER_ALL)
                .awaitTask()
                .nodes
                .map { WearNodeRef(it.id, it.isNearby, it.displayName) }
        }.getOrDefault(emptyList())
        val connected = runCatching {
            Wearable.getNodeClient(context).connectedNodes.awaitTask()
                .map { WearNodeRef(it.id, it.isNearby, it.displayName) }
        }.getOrDefault(emptyList())
        lastConnectedCount = connected.size
        val picked = WatchNodePicker.pick(capReachable, capAll + connected)
        lastLinkDetail = if (picked == null) {
            "нет (capability=${capReachable.size}/${capAll.size}, connected=${connected.size})"
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
