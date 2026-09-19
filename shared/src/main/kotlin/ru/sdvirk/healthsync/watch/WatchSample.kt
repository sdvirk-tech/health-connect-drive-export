package ru.sdvirk.healthsync.watch

data class WatchSample(
    val type: String,
    val timeEpochMs: Long,
    val value: Double,
    val value2: Double? = null,
    val extra: String? = null,
    val source: String = SOURCE_SENSOR,
) {
    companion object {
        const val HEART_RATE = "heart_rate"
        const val SPO2 = "spo2"
        const val HRV = "hrv_rmssd"
        const val STEPS = "steps"
        const val SLEEP = "sleep"
        const val BLOOD_PRESSURE = "blood_pressure"
        const val ECG = "ecg"

        const val SOURCE_SENSOR = "health_services"
        const val SOURCE_HC = "health_connect"
    }
}

object WatchSync {
    const val PATH_SAMPLES = "/healthsync/samples"
    const val PATH_DIAG = "/healthsync/diag"
    const val CAPABILITY_PHONE = "healthsync_phone"
    const val CAPABILITY_WATCH = "healthsync_watch"
    const val PREFS_WATCH = "health_sync_wear"
    const val PREFS_PHONE = "health_sync"
    const val KEY_SYNCED_THROUGH_MS = "synced_through_ms"
    const val KEY_PASSIVE_ENABLED = "passive_enabled"
    const val KEY_LAST_WATCH_MSG_MS = "last_watch_msg_ms"
    const val KEY_LAST_WATCH_DIAG = "last_watch_diag"
    const val KEY_LAST_WATCH_DIAG_MS = "last_watch_diag_ms"
    const val STORE_FILE = "watch_samples.jsonl"
    const val DIAG_FILE = "watch_diag.txt"
    const val MAX_DIAG_BYTES = 90_000
    const val LAN_HTTP_PORT = 8765
    const val LAN_UDP_PORT = 18765
    const val LAN_PROBE = "HEALTHSYNC?"
    const val LAN_MAGIC = "HEALTHSYNC/1"
    const val KEY_LAST_LAN_IP = "last_lan_ip"
    const val KEY_BT_STATUS = "bt_status"
    const val KEY_LAST_BT_MS = "last_bt_ms"
    const val NEARBY_SERVICE_ID = "ru.sdvirk.healthsync.bt"
    const val NEARBY_PHONE_NAME = "HealthSync"
    const val NEARBY_WATCH_NAME = "HealthSyncWatch"
    const val NEARBY_MAX_BYTES = 20_000
}
