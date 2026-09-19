package ru.sdvirk.healthsync.wear

import android.content.Context
import ru.sdvirk.healthsync.link.WatchLinkService
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec

object PhoneIngest {

    fun onSamplesJson(context: Context, json: String): Int {
        val samples = WatchSyncCodec.decodeMessage(json)
        if (samples.isEmpty()) return 0
        val store = WatchSampleStore.at(context.filesDir)
        store.append(samples)
        store.pruneOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
        context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
            .edit()
            .putLong(WatchSync.KEY_LAST_WATCH_MSG_MS, System.currentTimeMillis())
            .apply()
        WatchLinkService.notifyStatus(context, "Bluetooth: пробы с часов ${samples.size}")
        return samples.size
    }

    fun onDiag(context: Context, text: String) {
        WatchDiagStore.save(context, text)
        WatchLinkService.notifyStatus(context, "Bluetooth: лог часов получен")
    }

    fun markBluetooth(context: Context, status: String) {
        context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
            .edit()
            .putString(WatchSync.KEY_BT_STATUS, status)
            .putLong(WatchSync.KEY_LAST_BT_MS, System.currentTimeMillis())
            .apply()
        WatchLinkService.notifyStatus(context, status)
    }

    fun bluetoothLine(context: Context): String {
        val prefs = context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
        val status = prefs.getString(WatchSync.KEY_BT_STATUS, null)?.trim().orEmpty()
        val ms = prefs.getLong(WatchSync.KEY_LAST_BT_MS, 0L)
        return when {
            status.isNotEmpty() && ms > 0L -> "Bluetooth: $status"
            else -> "Bluetooth: телефон рекламирует Health Sync, ждёт часы"
        }
    }
}
