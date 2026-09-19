package ru.sdvirk.healthsync.link

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import ru.sdvirk.healthsync.watch.BtPayload
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.wear.PhoneIngest
import java.util.concurrent.ConcurrentHashMap

class PhoneBtNearby(private val context: Context) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val connected = ConcurrentHashMap<String, String>()

    fun start() {
        startAdvertising()
        startDiscovery()
        PhoneIngest.markBluetooth(context, "Bluetooth: ищем часы")
    }

    fun stop() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        connected.clear()
    }

    private fun startAdvertising() {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(
            WatchSync.NEARBY_PHONE_NAME,
            WatchSync.NEARBY_SERVICE_ID,
            lifecycle,
            options,
        ).addOnFailureListener { e ->
            Log.w(TAG, "advertise", e)
            PhoneIngest.markBluetooth(context, "Bluetooth: нет разрешения или Play Services")
        }
    }

    private fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(WatchSync.NEARBY_SERVICE_ID, discovery, options)
            .addOnFailureListener { e -> Log.w(TAG, "discover", e) }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connected.isNotEmpty()) return
            client.requestConnection(WatchSync.NEARBY_PHONE_NAME, endpointId, lifecycle)
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            client.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected[endpointId] = endpointId
                PhoneIngest.markBluetooth(context, "Bluetooth: часы на связи")
            } else {
                PhoneIngest.markBluetooth(context, "Bluetooth: ждём часы")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            PhoneIngest.markBluetooth(context, "Bluetooth: часы отключились, ищем снова")
            startAdvertising()
            startDiscovery()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val unpacked = BtPayload.unpack(bytes) ?: return
            when (unpacked.first) {
                BtPayload.SAMPLES -> PhoneIngest.onSamplesJson(context, unpacked.second)
                BtPayload.DIAG -> PhoneIngest.onDiag(context, unpacked.second)
                BtPayload.HEARTBEAT -> PhoneIngest.markBluetooth(context, "Bluetooth: часы на связи")
            }
            if (unpacked.first != BtPayload.ACK) {
                client.sendPayload(endpointId, Payload.fromBytes(BtPayload.pack(BtPayload.ACK)))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    companion object {
        private const val TAG = "PhoneBtNearby"
        private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }
}
