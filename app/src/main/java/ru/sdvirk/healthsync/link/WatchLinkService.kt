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
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import ru.sdvirk.healthsync.watch.WatchSyncCodec
import ru.sdvirk.healthsync.wear.WatchDiagStore

class WatchLinkService : Service() {

    private var http: PhoneLogServer? = null
    private var udp: PhoneUdpResponder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val ip = PhoneLogServer.localIpv4() ?: "…"
        val notification = buildNotification("Wi-Fi $ip:${WatchSync.LAN_HTTP_PORT}. Не закрывайте приложение.")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        http = PhoneLogServer(
            onDiag = {
                WatchDiagStore.save(this, it)
                updateLinkNotification("Лог часов получен. Открой Health Sync → Показать лог часов.")
            },
            onSamples = { json ->
                val samples = WatchSyncCodec.decodeMessage(json)
                if (samples.isEmpty()) return@PhoneLogServer
                val store = WatchSampleStore.at(filesDir)
                store.append(samples)
                store.pruneOlderThan(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
                getSharedPreferences(WatchSync.PREFS_PHONE, MODE_PRIVATE)
                    .edit()
                    .putLong(WatchSync.KEY_LAST_WATCH_MSG_MS, System.currentTimeMillis())
                    .apply()
                updateLinkNotification("Пробы с часов: ${samples.size}")
            },
            onReady = { ok ->
                val ip = PhoneLogServer.localIpv4() ?: "…"
                updateLinkNotification(
                    if (ok) "Wi-Fi $ip:${WatchSync.LAN_HTTP_PORT}. Не закрывайте приложение."
                    else "Порт ${WatchSync.LAN_HTTP_PORT} не открылся. Перезапусти Health Sync.",
                )
            },
        ).also { it.start() }
        udp = PhoneUdpResponder(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        http?.stop()
        udp?.stop()
        http = null
        udp = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Связь с часами", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun updateLinkNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Health Sync ждёт часы")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "watch_link"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val i = Intent(context, WatchLinkService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}
