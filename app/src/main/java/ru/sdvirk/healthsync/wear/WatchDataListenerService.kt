package ru.sdvirk.healthsync.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec

class WatchDataListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WatchSync.PATH_SAMPLES) return
        val json = messageEvent.data.toString(Charsets.UTF_8)
        val samples = WatchSyncCodec.decodeMessage(json)
        if (samples.isEmpty()) return
        val store = WatchSampleStore.at(filesDir)
        store.append(samples)
        store.pruneOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
    }
}
