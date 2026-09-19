package ru.sdvirk.healthsync.health

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build

object HcSettings {
    const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    const val HEALTH_CONNECT_PLAY = "com.google.android.apps.healthdata"
    const val HEALTH_CONNECT_SYSTEM = "com.google.android.healthconnect.controller"

    fun samsungHealthInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getPackageInfo(SAMSUNG_HEALTH, 0)
            true
        }.getOrDefault(false)

    /**
     * Samsung does not ship a standalone Health Connect launcher icon.
     * Opens system Health Connect settings when they exist. Does **not**
     * fall back to this app's own permission sheet — that looks like
     * Health Sync opening itself.
     */
    fun openHealthConnect(context: Context): Boolean {
        val intents = ArrayList<Intent>()
        intents += Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
        intents += Intent("android.settings.HEALTH_CONNECT_SETTINGS")
        intents += Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        intents += Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        if (Build.VERSION.SDK_INT >= 28) {
            context.packageManager.getLaunchIntentForPackage(HEALTH_CONNECT_SYSTEM)?.let { intents += it }
            context.packageManager.getLaunchIntentForPackage(HEALTH_CONNECT_PLAY)?.let { intents += it }
        }
        return startFirst(context, intents)
    }

    fun openSamsungHealth(context: Context): Boolean =
        startFirst(context, listOf(context.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH)))

    private fun startFirst(context: Context, intents: List<Intent?>): Boolean {
        for (intent in intents) {
            if (intent == null) continue
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }
}
