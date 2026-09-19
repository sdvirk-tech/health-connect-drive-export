package ru.sdvirk.healthsync.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import ru.sdvirk.healthsync.watch.BtFrame
import ru.sdvirk.healthsync.watch.BtPayload
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.wear.PhoneIngest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class PhoneRfcomm(private val context: Context) {
    private val running = AtomicBoolean(false)
    private var server: BluetoothServerSocket? = null
    private var client: BluetoothSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({
            while (running.get()) {
                val sock = listen()
                if (sock == null) {
                    try {
                        Thread.sleep(3_000)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                server = sock
                PhoneIngest.markBluetooth(context, "Bluetooth RFCOMM: ждём часы")
                try {
                    val remote = sock.accept()
                    client = remote
                    PhoneIngest.markBluetooth(context, "Bluetooth: часы на связи (RFCOMM)")
                    serve(remote)
                } catch (e: Exception) {
                    if (running.get()) Log.w(TAG, "accept", e)
                } finally {
                    runCatching { client?.close() }
                    client = null
                    runCatching { sock.close() }
                    server = null
                }
            }
        }, "healthsync-rfcomm").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { client?.close() }
        runCatching { server?.close() }
        client = null
        server = null
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun listen(): BluetoothServerSocket? {
        val adapter = adapter() ?: run {
            PhoneIngest.markBluetooth(context, "Bluetooth выключен на телефоне")
            return null
        }
        val uuid = UUID.fromString(WatchSync.BT_UUID)
        return try {
            adapter.listenUsingInsecureRfcommWithServiceRecord(WatchSync.BT_SDP_NAME, uuid)
        } catch (e: Exception) {
            Log.w(TAG, "insecure listen", e)
            try {
                adapter.listenUsingRfcommWithServiceRecord(WatchSync.BT_SDP_NAME, uuid)
            } catch (e2: Exception) {
                Log.w(TAG, "secure listen", e2)
                PhoneIngest.markBluetooth(context, "Bluetooth RFCOMM: ${e2.message ?: "не слушается"}")
                null
            }
        }
    }

    private fun serve(socket: BluetoothSocket) {
        val input = socket.inputStream
        val output = socket.outputStream
        while (running.get() && socket.isConnected) {
            val bytes = BtFrame.read(input) ?: break
            val unpacked = BtPayload.unpack(bytes) ?: continue
            when (unpacked.first) {
                BtPayload.SAMPLES -> PhoneIngest.onSamplesJson(context, unpacked.second)
                BtPayload.DIAG -> PhoneIngest.onDiag(context, unpacked.second)
                BtPayload.HEARTBEAT -> PhoneIngest.markBluetooth(context, "Bluetooth: часы на связи (RFCOMM)")
            }
            if (unpacked.first != BtPayload.ACK) {
                runCatching { BtFrame.write(output, BtPayload.pack(BtPayload.ACK)) }
            }
        }
    }

    private fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(BluetoothManager::class.java)
        val adapter = mgr?.adapter
        return adapter?.takeIf { it.isEnabled }
    }

    companion object {
        private const val TAG = "PhoneRfcomm"
    }
}
