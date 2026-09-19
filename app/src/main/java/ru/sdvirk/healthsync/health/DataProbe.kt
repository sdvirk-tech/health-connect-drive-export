package ru.sdvirk.healthsync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.permission.HealthPermission
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import ru.sdvirk.healthsync.watch.WatchSample
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

data class TypeProbe(
    val name: String,
    val count: Int,
    val origins: List<String>,
    val error: String?,
    val granted: Boolean,
)

data class DataProbeReport(
    val lines: List<String>,
) {
    fun asText(): String = lines.joinToString("\n")
}

object DataProbe {

    suspend fun run(context: Context, reader: HealthConnectReader): DataProbeReport {
        val lines = ArrayList<String>()
        val sdk = reader.availability()
        lines += when (sdk) {
            SDK_AVAILABLE -> "Health Connect на телефоне: доступен"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "Health Connect: нужно обновить провайдер"
            else -> "Health Connect недоступен. Без него телефон не читает Samsung Health."
        }

        val granted = if (sdk == SDK_AVAILABLE) {
            runCatching { reader.client?.permissionController?.getGrantedPermissions().orEmpty() }
                .getOrDefault(emptySet())
        } else {
            emptySet()
        }
        val missing = reader.permissions - granted
        lines += "Разрешения HC: выдано ${granted.size}/${reader.permissions.size}"
        if (missing.isNotEmpty()) {
            lines += "Не выданы: " + missing.joinToString { it.substringAfterLast('.') }
        }

        if (sdk == SDK_AVAILABLE && granted.isNotEmpty()) {
            val hasHistory = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
            if (!hasHistory) {
                lines += "Нет READ_HEALTH_DATA_HISTORY (доступ к прошлым данным)."
                lines += "Галочки типов в Health Connect этого не заменяют: без history новое приложение видит 0."
            }
            val end = Instant.now()
            val start = end.minus(30, ChronoUnit.DAYS)
            val probe = reader.probeSince(start, end)
            probe.forEach { t ->
                val origin = if (t.origins.isEmpty()) "нет источника" else t.origins.joinToString()
                val err = t.error?.let { " ERR=$it" } ?: ""
                val perm = if (t.granted) "ok" else "нет доступа"
                lines += "${t.name}: ${t.count} за 30д, perm=$perm, $origin$err"
            }
            val snap = runCatching { reader.readSince(start, end) }.getOrNull()
            snap?.aggregateHints?.forEach { lines += it }
            snap?.readErrors?.forEach { lines += "чтение: $it" }
            val anyRecords = probe.any { it.count > 0 } ||
                snap?.aggregateHints.orEmpty().any { it.contains("источники=") }
            if (!anyRecords && missing.isEmpty()) {
                lines += "Разрешения Health Sync только ЧИТАЮТ. Записи пишет Samsung Health."
                lines += "Samsung Health → Настройки → Health Connect → включи синхронизацию и ЗАПИСЬ тех же типов."
                lines += "Health Connect → права приложений → Samsung Health — тоже все разрешить (как источник)."
                lines += "Давление/ЭКГ: сначала замер в Samsung Health Monitor."
            }
        }

        lines += if (HcSettings.samsungHealthInstalled(context)) {
            "Samsung Health установлен (com.sec.android.app.shealth)"
        } else {
            "Samsung Health не найден на телефоне"
        }

        lines += watchLinkLine(context)

        val store = WatchSampleStore.at(context.filesDir)
        val byType = store.countsByType()
        val lastMsg = context.getSharedPreferences(WatchSync.PREFS_PHONE, Context.MODE_PRIVATE)
            .getLong(WatchSync.KEY_LAST_WATCH_MSG_MS, 0L)
        lines += "С часов на телефоне: ${store.count()} проб $byType"
        lines += if (lastMsg == 0L) {
            "Сообщений с часов ещё не было. На часах: замер/HC → «На телефон», Bluetooth, Health Sync открыт."
        } else {
            "Последнее сообщение с часов: ${Instant.ofEpochMilli(lastMsg)}"
        }
        lines += "Живой пульс — датчик часов (Health Services). Сон/HRV/SpO2/BP — Health Connect."
        lines += "ЭКГ через Health Connect 1.1 недоступно; Health Services ЭКГ не отдаёт."
        val wanted = listOf(
            WatchSample.HEART_RATE, WatchSample.HRV, WatchSample.SPO2,
            WatchSample.SLEEP, WatchSample.BLOOD_PRESSURE, WatchSample.ECG,
        )
        wanted.forEach { type ->
            if ((byType[type] ?: 0) == 0) {
                lines += "Часы $type: 0 — см. «Почему пусто» на часах."
            }
        }
        return DataProbeReport(lines)
    }

    private fun watchLinkLine(context: Context): String = try {
        val cap = Tasks.await(
            Wearable.getCapabilityClient(context)
                .getCapability(WatchSync.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE),
            5,
            TimeUnit.SECONDS,
        )
        val connected = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 5, TimeUnit.SECONDS)
        val capNames = cap.nodes.joinToString { it.displayName + if (it.isNearby) "*" else "" }
            .ifBlank { "нет" }
        val connNames = connected.joinToString { it.displayName + if (it.isNearby) "*" else "" }
            .ifBlank { "нет" }
        "Часы Data Layer: capability=$capNames, connected=$connNames"
    } catch (e: Exception) {
        "Часы Data Layer: ${e.javaClass.simpleName}"
    }
}
