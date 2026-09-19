package ru.sdvirk.healthsync.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.sdvirk.healthsync.R
import ru.sdvirk.healthsync.ui.MainActivity
import ru.sdvirk.healthsync.wear.PhoneIngest

class WatchLinkService : Service() {

    private var http: PhoneLogServer? = null
    private var udp: PhoneUdpResponder? = null
    private var bt: PhoneBtNearby? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        val notification = buildNotification(this, "Bluetooth: ищем часы. Не закрывайте приложение.")
        if (Build.VERSION.SDK_INT >= 34) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            try {
                startForeground(NOTIF_ID, notification, types)
            } catch (_: SecurityException) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            }
        } else {
            startForeground(NOTIF_ID, notification)
        }
        http = PhoneLogServer(
            onDiag = { PhoneIngest.onDiag(this, it) },
            onSamples = { PhoneIngest.onSamplesJson(this, it) },
            onReady = { },
        ).also { it.start() }
        udp = PhoneUdpResponder(this).also { it.start() }
        bt = PhoneBtNearby(this).also { it.start() }
        PhoneIngest.markBluetooth(this, "Bluetooth: ищем часы")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        http?.stop()
        udp?.stop()
        bt?.stop()
        http = null
        udp = null
        bt = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "watch_link"
        const val NOTIF_ID = 42

        fun start(context: Context) {
            val i = Intent(context, WatchLinkService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun notifyStatus(context: Context, text: String) {
            createChannel(context)
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.notify(NOTIF_ID, buildNotification(context, text))
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Связь с часами", NotificationManager.IMPORTANCE_LOW)
            )
        }

        private fun buildNotification(context: Context, text: String): Notification {
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("Health Sync: Bluetooth с часами")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
    }
}
