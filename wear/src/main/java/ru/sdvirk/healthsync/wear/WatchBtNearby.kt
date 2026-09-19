package ru.sdvirk.healthsync.wear

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
import com.google.android.gms.tasks.Tasks
import ru.sdvirk.healthsync.watch.BtPayload
import ru.sdvirk.healthsync.watch.WatchSync
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object WatchBtNearby {
    @Volatile
    private var client: ConnectionsClient? = null
    private val connected = ConcurrentHashMap<String, String>()
    private val lastAckMs = AtomicLong(0)
    @Volatile
    var lastStatus: String = "Bluetooth: ещё не запускали"
        private set

    fun start(context: Context) {
        val app = context.applicationContext
        if (client == null) {
            client = Nearby.getConnectionsClient(app)
        }
        if (isConnected()) {
            lastStatus = "Bluetooth: телефон на связи"
            return
        }
        startAdvertising()
        startDiscovery()
        if (!lastStatus.contains("на связи")) {
            lastStatus = "Bluetooth: ищем телефон"
        }
    }

    fun stop() {
        runCatching { client?.stopAdvertising() }
        runCatching { client?.stopDiscovery() }
        runCatching { client?.stopAllEndpoints() }
        connected.clear()
        client = null
        lastStatus = "Bluetooth: выкл"
    }

    fun isConnected(): Boolean = connected.isNotEmpty()

    fun sendSamples(json: String): Boolean = send(BtPayload.SAMPLES, json)

    fun sendDiag(text: String): Boolean {
        val clipped = if (text.toByteArray(Charsets.UTF_8).size > WatchSync.NEARBY_MAX_BYTES) {
            text.take(WatchSync.NEARBY_MAX_BYTES / 2)
        } else {
            text
        }
        return send(BtPayload.DIAG, clipped)
    }

    fun sendHeartbeat(): Boolean = send(BtPayload.HEARTBEAT, "hb")

    fun waitUntilConnected(timeoutMs: Long = 8_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isConnected()) return true
            Thread.sleep(200)
        }
        return isConnected()
    }

    private fun send(type: Int, body: String): Boolean {
        val endpoint = connected.keys.firstOrNull() ?: return false
        val conn = client ?: return false
        val before = lastAckMs.get()
        return try {
            Tasks.await(
                conn.sendPayload(endpoint, Payload.fromBytes(BtPayload.pack(type, body))),
                5,
                TimeUnit.SECONDS,
            )
            val deadline = System.currentTimeMillis() + 2_500
            while (System.currentTimeMillis() < deadline) {
                if (lastAckMs.get() > before) return true
                Thread.sleep(50)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "send", e)
            false
        }
    }

    private fun startAdvertising() {
        val conn = client ?: return
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        conn.startAdvertising(
            WatchSync.NEARBY_WATCH_NAME,
            WatchSync.NEARBY_SERVICE_ID,
            lifecycle,
            options,
        ).addOnFailureListener { e ->
            Log.w(TAG, "advertise", e)
            if (!isConnected() && lastStatus.contains("нет разрешения").not()) {
                val msg = e.message.orEmpty()
                if (!msg.contains("already", ignoreCase = true) &&
                    !msg.contains("STATUS_ALREADY", ignoreCase = true)
                ) {
                    lastStatus = "Bluetooth: нет разрешения"
                }
            }
        }
    }

    private fun startDiscovery() {
        val conn = client ?: return
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        conn.startDiscovery(WatchSync.NEARBY_SERVICE_ID, discovery, options)
            .addOnFailureListener { e -> Log.w(TAG, "discover", e) }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (connected.isNotEmpty()) return
            client?.requestConnection(WatchSync.NEARBY_WATCH_NAME, endpointId, lifecycle)
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            client?.acceptConnection(endpointId, payloads)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connected[endpointId] = endpointId
                lastStatus = "Bluetooth: телефон на связи"
            } else {
                lastStatus = "Bluetooth: не соединились, ищем снова"
            }
        }

        override fun onDisconnected(endpointId: String) {
            connected.remove(endpointId)
            lastStatus = "Bluetooth: телефон отключился, ищем снова"
            startAdvertising()
            startDiscovery()
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val unpacked = BtPayload.unpack(bytes) ?: return
            if (unpacked.first == BtPayload.ACK) {
                lastAckMs.set(System.currentTimeMillis())
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private const val TAG = "WatchBtNearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
}
