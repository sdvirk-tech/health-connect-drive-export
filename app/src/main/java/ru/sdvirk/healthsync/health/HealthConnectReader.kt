package ru.sdvirk.healthsync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.SDK_AVAILABLE
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import ru.sdvirk.healthsync.watch.WatchSample
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Читает данные из Health Connect (куда Samsung Health пишет при включённой синхронизации).
 * Не логинимся в Samsung — только локальный API на телефоне.
 */
class HealthConnectReader(private val context: Context) {

    val client: HealthConnectClient?
        get() = if (availability() == SDK_AVAILABLE) {
            try {
                HealthConnectClient.getOrCreate(context)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
    )

    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun readSince(start: Instant, end: Instant = Instant.now()): HealthSnapshot {
        val hc = client ?: return HealthSnapshot.empty()
        val range = TimeRangeFilter.between(start, end)

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
        } catch (_: Exception) {
            emptyList()
        }

        return HealthSnapshot(
            heartRate = read(HeartRateRecord::class),
            restingHeartRate = read(RestingHeartRateRecord::class),
            hrv = read(HeartRateVariabilityRmssdRecord::class),
            sleep = read(SleepSessionRecord::class),
            spo2 = read(OxygenSaturationRecord::class),
            bloodPressure = read(BloodPressureRecord::class),
            weight = read(WeightRecord::class),
            steps = read(StepsRecord::class),
            distance = read(DistanceRecord::class),
            exercise = read(ExerciseSessionRecord::class),
            exportedAt = Instant.now(),
            rangeStart = start,
            rangeEnd = end,
        )
    }

    suspend fun probeSince(start: Instant, end: Instant = Instant.now()): List<TypeProbe> {
        val hc = client ?: return emptyList()
        val granted = runCatching { hc.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val range = TimeRangeFilter.between(start, end)

        suspend fun <T : Record> readOrError(clazz: KClass<T>): Pair<List<T>, String?> = try {
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
            all to null
        } catch (e: Exception) {
            emptyList<T>() to (e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }

        suspend fun <T : Record> probe(
            name: String,
            clazz: KClass<T>,
            countOf: (List<T>) -> Int = { it.size },
        ): TypeProbe {
            val perm = HealthPermission.getReadPermission(clazz)
            if (perm !in granted) {
                return TypeProbe(name, 0, emptyList(), null, granted = false)
            }
            val (recs, err) = readOrError(clazz)
            val origins = recs.map { it.metadata.dataOrigin.packageName }.distinct().sorted()
            return TypeProbe(name, countOf(recs), origins, err, granted = true)
        }

        return listOf(
            probe("пульс", HeartRateRecord::class) { recs -> recs.sumOf { it.samples.size } },
            probe("пульс покоя", RestingHeartRateRecord::class),
            probe("HRV", HeartRateVariabilityRmssdRecord::class),
            probe("сон", SleepSessionRecord::class),
            probe("SpO2", OxygenSaturationRecord::class),
            probe("давление", BloodPressureRecord::class),
            probe("вес", WeightRecord::class),
            probe("шаги", StepsRecord::class),
            probe("дистанция", DistanceRecord::class),
            probe("тренировки", ExerciseSessionRecord::class),
        )
    }
}

data class HealthSnapshot(
    val heartRate: List<HeartRateRecord>,
    val restingHeartRate: List<RestingHeartRateRecord>,
    val hrv: List<HeartRateVariabilityRmssdRecord>,
    val sleep: List<SleepSessionRecord>,
    val spo2: List<OxygenSaturationRecord>,
    val bloodPressure: List<BloodPressureRecord>,
    val weight: List<WeightRecord>,
    val steps: List<StepsRecord>,
    val distance: List<DistanceRecord>,
    val exercise: List<ExerciseSessionRecord>,
    val exportedAt: Instant,
    val rangeStart: Instant,
    val rangeEnd: Instant,
    val watchSamples: List<WatchSample> = emptyList(),
) {
    companion object {
        fun empty() = HealthSnapshot(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            Instant.now(), Instant.EPOCH, Instant.now()
        )
    }

    fun summaryLines(): List<String> = listOf(
        "HR samples: ${heartRate.sumOf { it.samples.size }}",
        "Resting HR: ${restingHeartRate.size}",
        "HRV: ${hrv.size}",
        "Sleep: ${sleep.size}",
        "SpO2: ${spo2.size}",
        "BP: ${bloodPressure.size}",
        "Weight: ${weight.size}",
        "Steps: ${steps.size}",
        "Distance: ${distance.size}",
        "Exercise: ${exercise.size}",
        "Watch HR: ${watchSamples.count { it.type == WatchSample.HEART_RATE }}",
        "Watch HRV: ${watchSamples.count { it.type == WatchSample.HRV }}",
        "Watch SpO2: ${watchSamples.count { it.type == WatchSample.SPO2 }}",
        "Watch sleep: ${watchSamples.count { it.type == WatchSample.SLEEP }}",
        "Watch BP: ${watchSamples.count { it.type == WatchSample.BLOOD_PRESSURE }}",
        "Watch ECG: ${watchSamples.count { it.type == WatchSample.ECG }}",
        "Watch steps: ${watchSamples.count { it.type == WatchSample.STEPS }}",
    )
}
