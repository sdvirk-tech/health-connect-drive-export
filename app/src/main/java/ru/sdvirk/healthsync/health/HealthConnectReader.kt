package ru.sdvirk.healthsync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Читает данные из Health Connect (куда Samsung Health пишет при включённой синхронизации).
 * Не логинимся в Samsung — только локальный API на телефоне.
 */
class HealthConnectReader(private val context: Context) {

    val client: HealthConnectClient?
        get() = try {
            HealthConnectClient.getOrCreate(context)
        } catch (_: Exception) {
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
    )

    suspend fun availability(): Int =
        HealthConnectClient.getSdkStatus(context)

    suspend fun readSince(start: Instant, end: Instant = Instant.now()): HealthSnapshot {
        val hc = client ?: return HealthSnapshot.empty()
        val range = TimeRangeFilter.between(start, end)

        suspend fun <T : androidx.health.connect.client.records.Record> read(
            clazz: kotlin.reflect.KClass<T>
        ): List<T> = try {
            hc.readRecords(ReadRecordsRequest(clazz, timeRangeFilter = range)).records
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
) {
    companion object {
        fun empty() = HealthSnapshot(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            Instant.now(), Instant.EPOCH, Instant.now()
        )
    }

    fun summaryLines(): List<String> = listOf(
        "HR samples: ${heartRate.size}",
        "Resting HR: ${restingHeartRate.size}",
        "HRV: ${hrv.size}",
        "Sleep: ${sleep.size}",
        "SpO2: ${spo2.size}",
        "BP: ${bloodPressure.size}",
        "Weight: ${weight.size}",
        "Steps: ${steps.size}",
        "Distance: ${distance.size}",
        "Exercise: ${exercise.size}",
    )
}
