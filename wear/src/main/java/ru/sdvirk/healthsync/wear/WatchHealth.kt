package ru.sdvirk.healthsync.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.HealthEvent
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
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
        for (point in container.sampleDataPoints) {
            val value = numberValue(point.value) ?: continue
            val type = WatchSample.typeKey(point.dataType.name)
            if (!accept(type, value)) continue
            out += WatchSample(
                type = type,
                timeEpochMs = point.getTimeInstant(boot).toEpochMilli(),
                value = value,
            )
        }
        for (point in container.intervalDataPoints) {
            val value = numberValue(point.value) ?: continue
            val type = WatchSample.typeKey(point.dataType.name)
            if (!accept(type, value)) continue
            out += WatchSample(
                type = type,
                timeEpochMs = point.getEndInstant(boot).toEpochMilli(),
                value = value,
                extra = "start=${point.getStartInstant(boot).toEpochMilli()}",
            )
        }
        for (point in container.cumulativeDataPoints) {
            val value = numberValue(point.total) ?: continue
            val type = WatchSample.typeKey(point.dataType.name)
            if (!accept(type, value)) continue
            out += WatchSample(
                type = type,
                timeEpochMs = point.end.toEpochMilli(),
                value = value,
                extra = "start=${point.start.toEpochMilli()};cumulative",
            )
        }
        for (point in container.statisticalDataPoints) {
            val avg = numberValue(point.average) ?: continue
            val type = WatchSample.typeKey(point.dataType.name)
            if (!accept(type, avg)) continue
            out += WatchSample(
                type = type,
                timeEpochMs = point.end.toEpochMilli(),
                value = avg,
                value2 = numberValue(point.max),
                extra = "min=${point.min};max=${point.max};start=${point.start.toEpochMilli()}",
            )
        }
        return out
    }

    private fun numberValue(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        else -> null
    }

    private fun accept(type: String, value: Double): Boolean {
        if (!value.isFinite()) return false
        return when (type) {
            WatchSample.HEART_RATE -> value in 20.0..250.0
            WatchSample.SPO2 -> value in 50.0..100.0
            else -> value >= 0.0
        }
    }

    suspend fun supportedPassiveTypes(context: Context): Set<DataType<*, *>> {
        val caps = HealthServices.getClient(context).passiveMonitoringClient
            .getCapabilitiesAsync()
            .await()
        return caps.supportedDataTypesPassiveMonitoring
    }

    suspend fun capabilitiesReport(context: Context): String {
        val hs = runCatching {
            val caps = HealthServices.getClient(context).passiveMonitoringClient
                .getCapabilitiesAsync()
                .await()
            val names = caps.supportedDataTypesPassiveMonitoring.map { it.name }.sorted()
            val events = caps.supportedHealthEventTypes.map { it.name }.sorted()
            val states = caps.supportedUserActivityStates.map { it.name }.sorted()
            buildString {
                append("Health Services фон: ")
                append(if (names.isEmpty()) "пусто" else names.joinToString())
                append("\nСобытия: ")
                append(if (events.isEmpty()) "нет" else events.joinToString())
                append("\nАктивность: ")
                append(if (states.isEmpty()) "нет" else states.joinToString())
            }
        }.getOrElse { "Health Services: ${it.javaClass.simpleName} ${it.message ?: ""}" }
        val measure = runCatching {
            val caps = HealthServices.getClient(context).measureClient.getCapabilitiesAsync().await()
            val names = caps.supportedDataTypesMeasure.map { it.name }.sorted()
            "Health Services замер: " + if (names.isEmpty()) "пусто" else names.joinToString()
        }.getOrElse { "Замер: ${it.javaClass.simpleName}" }
        return "$hs\n$measure"
    }

    suspend fun registerPassive(context: Context): Set<DataType<*, *>> {
        val client = HealthServices.getClient(context).passiveMonitoringClient
        val caps = client.getCapabilitiesAsync().await()
        val types = caps.supportedDataTypesPassiveMonitoring
        if (types.isEmpty()) {
            error("Health Services не отдаёт датчики на этих часах")
        }
        val events = caps.supportedHealthEventTypes.filter { it != HealthEvent.Type.UNKNOWN }.toSet()
        val config = PassiveListenerConfig.builder()
            .setDataTypes(types)
            .setShouldUserActivityInfoBeRequested(true)
            .setHealthEventTypes(events)
            .build()
        client.setPassiveListenerServiceAsync(HrPassiveService::class.java, config).await()
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

    fun hasBodySensors(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    fun onActivity(context: Context, info: UserActivityInfo) {
        val samples = ArrayList<WatchSample>()
        val t = info.stateChangeTime.toEpochMilli()
        samples += WatchSample(
            type = WatchSample.ACTIVITY,
            timeEpochMs = t,
            value = info.userActivityState.id.toDouble(),
            extra = info.userActivityState.name,
        )
        val prefs = context.getSharedPreferences(WatchSync.PREFS_WATCH, Context.MODE_PRIVATE)
        if (info.userActivityState == UserActivityState.USER_ACTIVITY_ASLEEP) {
            if (prefs.getLong(WatchSync.KEY_ASLEEP_START_MS, 0L) == 0L) {
                prefs.edit().putLong(WatchSync.KEY_ASLEEP_START_MS, t).apply()
            }
        } else {
            val start = prefs.getLong(WatchSync.KEY_ASLEEP_START_MS, 0L)
            if (start in 1 until t) {
                samples += WatchSample(
                    type = WatchSample.SLEEP,
                    timeEpochMs = start,
                    value = (t - start) / 60_000.0,
                    value2 = t.toDouble(),
                    extra = "activity_asleep",
                )
                prefs.edit().putLong(WatchSync.KEY_ASLEEP_START_MS, 0L).apply()
            }
        }
        store(context).append(samples)
        WatchPhoneSync.enqueue(context)
    }

    fun onHealthEvent(context: Context, event: HealthEvent) {
        store(context).append(
            listOf(
                WatchSample(
                    type = WatchSample.FALL,
                    timeEpochMs = event.eventTime.toEpochMilli(),
                    value = 1.0,
                    extra = event.type.name,
                )
            ) + toSamples(event.metrics)
        )
        WatchPhoneSync.enqueue(context)
    }
}

class HrPassiveService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val samples = WatchHealth.toSamples(dataPoints)
        if (samples.isEmpty()) return
        WatchHealth.store(this).append(samples)
        WatchPhoneSync.enqueue(this)
    }

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        WatchHealth.onActivity(this, info)
    }

    override fun onHealthEventReceived(event: HealthEvent) {
        WatchHealth.onHealthEvent(this, event)
    }
}
