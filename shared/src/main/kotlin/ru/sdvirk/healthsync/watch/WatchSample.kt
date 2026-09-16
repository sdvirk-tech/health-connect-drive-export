package ru.sdvirk.healthsync.watch

data class WatchSample(
    val type: String,
    val timeEpochMs: Long,
    val value: Double,
) {
    companion object {
        const val HEART_RATE = "heart_rate"
        const val SPO2 = "spo2"
        const val HRV = "hrv_rmssd"
        const val STEPS = "steps"
    }
}

object WatchSync {
    const val PATH_SAMPLES = "/healthsync/samples"
    const val CAPABILITY_PHONE = "healthsync_phone"
    const val CAPABILITY_WATCH = "healthsync_watch"
    const val PREFS_WATCH = "health_sync_wear"
    const val KEY_SYNCED_THROUGH_MS = "synced_through_ms"
    const val KEY_PASSIVE_ENABLED = "passive_enabled"
    const val STORE_FILE = "watch_samples.jsonl"
}
