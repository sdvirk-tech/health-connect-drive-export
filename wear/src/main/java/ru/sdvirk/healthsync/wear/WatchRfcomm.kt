package ru.sdvirk.healthsync.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import ru.sdvirk.healthsync.watch.BtFrame
import ru.sdvirk.healthsync.watch.BtPayload
import ru.sdvirk.healthsync.watch.WatchSync
import java.util.UUID

object WatchRfcomm {
    @Volatile
    var lastStatus: String = "Bluetooth RFCOMM: ещё не запускали"
        private set

    @Volatile
    private var socket: BluetoothSocket? = null

    fun isConnected(): Boolean = socket?.isConnected == true

    @Synchronized
    fun ensureConnected(context: Context): Boolean {
        if (isConnected()) {
            lastStatus = "Bluetooth RFCOMM: телефон на связи"
            return true
        }
        close()
        val adapter = adapter(context) ?: run {
            lastStatus = "Bluetooth выключен на часах"
            return false
        }
        val devices = bonded(adapter)
        if (devices.isEmpty()) {
            lastStatus = "нет сопряжённого телефона. Часы должны быть связаны с телефоном."
            return false
        }
        for (device in devices) {
            if (tryConnect(device, insecure = true) || tryConnect(device, insecure = false)) {
                lastStatus = "Bluetooth RFCOMM: ${deviceName(device)}"
                startReader()
                return true
            }
        }
        lastStatus = "RFCOMM не подключился к " + devices.joinToString { deviceName(it) }
        return false
    }

    @Synchronized
    fun sendSamples(json: String): Boolean = send(BtPayload.SAMPLES, json)

    @Synchronized
    fun sendDiag(text: String): Boolean {
        val clipped = if (text.toByteArray(Charsets.UTF_8).size > WatchSync.NEARBY_MAX_BYTES) {
            text.take(WatchSync.NEARBY_MAX_BYTES / 2)
        } else {
            text
        }
        return send(BtPayload.DIAG, clipped)
    }

    @Synchronized
    fun sendHeartbeat(): Boolean = send(BtPayload.HEARTBEAT, "hb")

    fun stop() {
        close()
        lastStatus = "Bluetooth RFCOMM: выкл"
    }

    private fun send(type: Int, body: String): Boolean {
        val sock = socket ?: return false
        if (!sock.isConnected) return false
        return try {
            BtFrame.write(sock.outputStream, BtPayload.pack(type, body))
            true
        } catch (e: Exception) {
            Log.w(TAG, "send", e)
            close()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnect(device: BluetoothDevice, insecure: Boolean): Boolean {
        val uuid = UUID.fromString(WatchSync.BT_UUID)
        val sock = try {
            if (insecure) device.createInsecureRfcommSocketToServiceRecord(uuid)
            else device.createRfcommSocketToServiceRecord(uuid)
        } catch (e: Exception) {
            Log.w(TAG, "create socket", e)
            return false
        }
        val worker = Thread {
            runCatching { sock.connect() }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(5_000)
        if (sock.isConnected) {
            socket = sock
            return true
        }
        runCatching { sock.close() }
        return false
    }

    private fun startReader() {
        val sock = socket ?: return
        Thread({
            try {
                while (sock.isConnected) {
                    val bytes = BtFrame.read(sock.inputStream) ?: break
                    BtPayload.unpack(bytes)
                }
            } catch (_: Exception) {
            } finally {
                if (socket === sock) close()
            }
        }, "healthsync-rfcomm-read").also {
            it.isDaemon = true
            it.start()
        }
    }

    @Synchronized
    private fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun adapter(context: Context): BluetoothAdapter? {
        val mgr = context.getSystemService(BluetoothManager::class.java)
        return mgr?.adapter?.takeIf { it.isEnabled }
    }

    @SuppressLint("MissingPermission")
    private fun bonded(adapter: BluetoothAdapter): List<BluetoothDevice> =
        adapter.bondedDevices.orEmpty().toList()

    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice): String =
        device.name?.takeIf { it.isNotBlank() } ?: device.address

    private const val TAG = "WatchRfcomm"
}
