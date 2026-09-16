package ru.sdvirk.healthsync.export

import android.content.Context
import ru.sdvirk.healthsync.health.HealthSnapshot
import ru.sdvirk.healthsync.watch.WatchSampleStore

fun HealthSnapshot.withWatchSamples(context: Context): HealthSnapshot {
    val samples = WatchSampleStore.at(context.filesDir)
        .read(rangeStart.toEpochMilli(), rangeEnd.toEpochMilli())
    return copy(watchSamples = samples)
}

fun watchSampleCount(context: Context): Int = WatchSampleStore.at(context.filesDir).count()
