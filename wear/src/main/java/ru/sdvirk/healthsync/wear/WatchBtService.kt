package ru.sdvirk.healthsync.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WatchBtService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Health Sync")
            .setContentText("Bluetooth с телефоном")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } catch (_: SecurityException) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            }
        } else {
            startForeground(NOTIF_ID, notification)
        }
        WatchBtNearby.start(this)
        WatchPhoneSync.schedule(this)
        scope.launch {
            if (WatchHealth.hasBodySensors(this@WatchBtService)) {
                runCatching { WatchHealth.registerPassive(this@WatchBtService) }
            }
            while (isActive) {
                WatchRfcomm.ensureConnected(this@WatchBtService)
                WatchBtNearby.start(this@WatchBtService)
                if (WatchRfcomm.isConnected()) {
                    WatchRfcomm.sendHeartbeat()
                } else {
                    WatchBtNearby.sendHeartbeat()
                }
                runCatching { WatchPhoneSync.syncNow(this@WatchBtService) }
                delay(5_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bluetooth", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val CHANNEL = "watch_bt"
        private const val NOTIF_ID = 43

        fun start(context: Context) {
            val i = Intent(context, WatchBtService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
