package ru.sdvirk.healthsync.wear

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import ru.sdvirk.healthsync.watch.WatchSample
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * Samsung Health / Samsung Health Monitor пишут в Health Connect на часах (Wear OS 4+),
 * если пользователь включил запись. Живые датчики Health Services пульс/шаги не заменяют
 * сон, SpO2, давление и ЭКГ.
 */
object WatchHealthConnect {

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun client(context: Context): HealthConnectClient? =
        if (availability(context) == SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    suspend fun pull(context: Context, days: Long = 30): Pair<List<WatchSample>, String> {
        val sdk = availability(context)
        if (sdk != SDK_AVAILABLE) {
            return emptyList<WatchSample>() to hcUnavailable(sdk)
        }
        val hc = client(context)
            ?: return emptyList<WatchSample>() to "Health Connect на часах не открылся"
        val granted = hc.permissionController.getGrantedPermissions()
        val missing = permissions - granted
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        val range = TimeRangeFilter.between(start, end)
        val out = ArrayList<WatchSample>()
        val notes = ArrayList<String>()
        if (missing.isNotEmpty()) {
            notes += "HC-разрешения не все (${granted.size}/${permissions.size})"
        }

        suspend fun <T : Record> read(clazz: KClass<T>): List<T> = try {
            val all = mutableListOf<T>()
            var pageToken: String? = null
            do {
                val page = hc.readRecords(
                    ReadRecordsRequest(
                        recordType = clazz,
                        timeRangeFilter = range,
                        pageSize = 1000,
                        pageToken = pageToken,
                    )
                )
                all += page.records
                pageToken = page.pageToken
            } while (pageToken != null)
            all
        } catch (e: Exception) {
            notes += "${clazz.simpleName}: ${e.javaClass.simpleName}"
            emptyList()
        }

        read(HeartRateRecord::class).forEach { rec ->
            rec.samples.forEach { sample ->
                val bpm = sample.beatsPerMinute.toDouble()
                if (bpm in 20.0..250.0) {
                    out += WatchSample(
                        WatchSample.HEART_RATE,
                        sample.time.toEpochMilli(),
                        bpm,
                        source = WatchSample.SOURCE_HC,
                        extra = rec.metadata.dataOrigin.packageName,
                    )
                }
            }
        }
        read(HeartRateVariabilityRmssdRecord::class).forEach { rec ->
            out += WatchSample(
                WatchSample.HRV,
                rec.time.toEpochMilli(),
                rec.heartRateVariabilityMillis,
                extra = rec.metadata.dataOrigin.packageName,
                source = WatchSample.SOURCE_HC,
            )
        }
        read(OxygenSaturationRecord::class).forEach { rec ->
            out += WatchSample(
                WatchSample.SPO2,
                rec.time.toEpochMilli(),
                rec.percentage.value,
                extra = rec.metadata.dataOrigin.packageName,
                source = WatchSample.SOURCE_HC,
            )
        }
        read(BloodPressureRecord::class).forEach { rec ->
            out += WatchSample(
                WatchSample.BLOOD_PRESSURE,
                rec.time.toEpochMilli(),
                rec.systolic.inMillimetersOfMercury,
                rec.diastolic.inMillimetersOfMercury,
                extra = rec.metadata.dataOrigin.packageName,
                source = WatchSample.SOURCE_HC,
            )
        }
        read(SleepSessionRecord::class).forEach { rec ->
            val startMs = rec.startTime.toEpochMilli()
            val endMs = rec.endTime.toEpochMilli()
            out += WatchSample(
                WatchSample.SLEEP,
                startMs,
                ((endMs - startMs) / 60_000.0),
                endMs.toDouble(),
                extra = "session:" + rec.metadata.dataOrigin.packageName,
                source = WatchSample.SOURCE_HC,
            )
            rec.stages.forEach { stage ->
                out += WatchSample(
                    WatchSample.SLEEP,
                    stage.startTime.toEpochMilli(),
                    ((stage.endTime.toEpochMilli() - stage.startTime.toEpochMilli()) / 60_000.0),
                    stage.endTime.toEpochMilli().toDouble(),
                    extra = "stage:" + stage.stage,
                    source = WatchSample.SOURCE_HC,
                )
            }
        }

        notes += "HC записей → проб: ${out.size}"
        notes += "ЭКГ: Health Connect 1.1 не отдаёт ECG; Samsung Health Monitor держит ЭКГ у себя"
        return out to notes.joinToString("\n")
    }

    fun hcUnavailable(sdk: Int): String = when (sdk) {
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            "Обнови Health Connect на часах"
        else ->
            "Health Connect на этих часах нет (Samsung не ставит его на Ultra). Сон — из состояния asleep. SpO2/давление/ЭКГ — только если Samsung пишет их в Health Connect на телефоне."
    }
}
