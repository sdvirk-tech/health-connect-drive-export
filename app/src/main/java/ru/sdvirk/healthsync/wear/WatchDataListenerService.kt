package ru.sdvirk.healthsync.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import ru.sdvirk.healthsync.watch.WatchSync

class WatchDataListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WatchSync.PATH_SAMPLES ->
                PhoneIngest.onSamplesJson(this, messageEvent.data.toString(Charsets.UTF_8))
            WatchSync.PATH_DIAG ->
                PhoneIngest.onDiag(this, messageEvent.data.toString(Charsets.UTF_8))
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { buffer ->
            for (event in buffer) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val path = event.dataItem.uri.path ?: continue
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                when {
                    path.startsWith(WatchSync.PATH_SAMPLES) -> {
                        val json = map.getString("json") ?: continue
                        PhoneIngest.onSamplesJson(this, json)
                    }
                    path.startsWith(WatchSync.PATH_DIAG) -> {
                        val text = map.getString("text") ?: continue
                        PhoneIngest.onDiag(this, text)
                    }
                }
            }
        }
    }
}
