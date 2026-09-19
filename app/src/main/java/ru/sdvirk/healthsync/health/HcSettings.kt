package ru.sdvirk.healthsync.health

import android.content.Context
import android.content.Intent

object HcSettings {
    const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    const val HEALTH_CONNECT = "com.google.android.apps.healthdata"

    fun samsungHealthInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(SAMSUNG_HEALTH, 0)
            true
        }.getOrDefault(false)

    fun openHealthConnect(context: Context): Boolean {
        val intents = listOf(
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
            context.packageManager.getLaunchIntentForPackage(HEALTH_CONNECT),
        )
        return startFirst(context, intents)
    }

    fun openSamsungHealth(context: Context): Boolean =
        startFirst(context, listOf(context.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH)))

    private fun startFirst(context: Context, intents: List<Intent?>): Boolean {
        for (intent in intents) {
            if (intent == null) continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }
}
