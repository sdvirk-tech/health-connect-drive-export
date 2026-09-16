package ru.sdvirk.healthsync.wear

import android.content.Context
import android.os.SystemClock
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.SampleDataPoint
import ru.sdvirk.healthsync.watch.WatchSample
import ru.sdvirk.healthsync.watch.WatchSampleStore
import ru.sdvirk.healthsync.watch.WatchSync
import java.time.Instant

object WatchHealth {

    fun store(context: Context): WatchSampleStore =
        WatchSampleStore.at(context.filesDir)

    fun bootInstant(): Instant =
        Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())

    fun toSamples(container: DataPointContainer, boot: Instant = bootInstant()): List<WatchSample> {
        val out = ArrayList<WatchSample>()
        container.getData(DataType.HEART_RATE_BPM).forEach { point ->
            val bpm = point.value
            if (bpm in 20.0..250.0) {
                out += WatchSample(
                    type = WatchSample.HEART_RATE,
                    timeEpochMs = sampleInstant(point, boot).toEpochMilli(),
                    value = bpm,
                )
            }
        }
        container.getData(DataType.STEPS).forEach { point ->
            val count = point.value.toDouble()
            if (count >= 0) {
                out += WatchSample(
                    type = WatchSample.STEPS,
                    timeEpochMs = intervalEndInstant(point, boot).toEpochMilli(),
                    value = count,
                )
            }
        }
        return out
    }

    suspend fun supportedPassiveTypes(context: Context): Set<DataType<*, *>> {
        val caps = HealthServices.getClient(context).passiveMonitoringClient
            .getCapabilitiesAsync()
            .await()
        val wanted = listOf<DataType<*, *>>(DataType.HEART_RATE_BPM, DataType.STEPS)
        return wanted.filter { it in caps.supportedDataTypesPassiveMonitoring }.toSet()
    }

    suspend fun registerPassive(context: Context): Set<DataType<*, *>> {
        val types = supportedPassiveTypes(context)
        if (types.isEmpty()) {
            error("Health Services не отдаёт пульс/шаги на этих часах")
        }
        val config = PassiveListenerConfig.builder()
            .setDataTypes(types)
            .build()
        HealthServices.getClient(context).passiveMonitoringClient
            .setPassiveListenerServiceAsync(HrPassiveService::class.java, config)
            .await()
        context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WatchSync.KEY_PASSIVE_ENABLED, true)
            .apply()
        return types
    }

    suspend fun unregisterPassive(context: Context) {
        HealthServices.getClient(context).passiveMonitoringClient
            .clearPassiveListenerServiceAsync()
            .await()
        context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WatchSync.KEY_PASSIVE_ENABLED, false)
            .apply()
    }

    fun isPassiveEnabled(context: Context): Boolean =
        context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
            .getBoolean(WatchSync.KEY_PASSIVE_ENABLED, false)

    private fun sampleInstant(point: SampleDataPoint<*>, boot: Instant): Instant =
        boot.plus(point.timeDurationFromBoot)

    private fun intervalEndInstant(point: IntervalDataPoint<*>, boot: Instant): Instant =
        boot.plus(point.endDurationFromBoot)
}

class HrPassiveService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val samples = WatchHealth.toSamples(dataPoints)
        if (samples.isEmpty()) return
        WatchHealth.store(this).append(samples)
        WatchPhoneSync.enqueue(this)
    }
}
