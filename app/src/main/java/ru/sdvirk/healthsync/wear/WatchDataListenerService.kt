package ru.sdvirk.healthsync.wear

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec

class WatchDataListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WatchSync.PATH_SAMPLES -> {
                val json = messageEvent.data.toString(Charsets.UTF_8)
                val samples = WatchSyncCodec.decodeMessage(json)
                if (samples.isEmpty()) return
                val store = WatchSampleStore.at(filesDir)
                store.append(samples)
                store.pruneOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
                getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(WatchSync.KEY_LAST_WATCH_MSG_MS, System.currentTimeMillis())
                    .apply()
            }
            WatchSync.PATH_DIAG -> {
                val text = messageEvent.data.toString(Charsets.UTF_8)
                WatchDiagStore.save(this, text)
            }
        }
    }
}
