package ru.sdvirk.healthsync.watch

import java.io.File

class WatchSampleStore(private val file: File) {

    @Synchronized
    fun append(samples: List<WatchSample>) {
        if (samples.isEmpty()) return
        file.parentFile?.mkdirs()
        val existing = readAll().map { key(it) }.toMutableSet()
        file.appendText(
            buildString {
                for (sample in samples) {
                    val k = key(sample)
                    if (k in existing) continue
                    existing += k
                    append(WatchSyncCodec.toLine(sample)).append('\n')
                }
            },
            Charsets.UTF_8
        )
    }

    @Synchronized
    fun read(fromInclusiveMs: Long, toInclusiveMs: Long): List<WatchSample> =
        readAll().filter { it.timeEpochMs in fromInclusiveMs..toInclusiveMs }

    @Synchronized
    fun readAfter(fromExclusiveMs: Long): List<WatchSample> =
        readAll().filter { it.timeEpochMs > fromExclusiveMs }.sortedBy { it.timeEpochMs }

    @Synchronized
    fun count(): Int = readAll().size

    @Synchronized
    fun lastHeartRate(): WatchSample? = lastOf(WatchSample.HEART_RATE)

    @Synchronized
    fun lastOf(type: String): WatchSample? =
        readAll().asReversed().firstOrNull { it.type == type }

    @Synchronized
    fun countsByType(): Map<String, Int> =
        readAll().groupingBy { it.type }.eachCount()

    @Synchronized
    fun pruneOlderThan(epochMs: Long) {
        val keep = readAll().filter { it.timeEpochMs >= epochMs }
        file.parentFile?.mkdirs()
        file.writeText(
            keep.joinToString(separator = "\n", postfix = if (keep.isEmpty()) "" else "\n") {
                WatchSyncCodec.toLine(it)
            },
            Charsets.UTF_8
        )
    }

    @Synchronized
    fun readAll(): List<WatchSample> {
        if (!file.exists()) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { WatchSyncCodec.fromLine(it) }
    }

    private fun key(sample: WatchSample) =
        sample.type + ":" + sample.timeEpochMs + ":" + (sample.extra ?: "") + ":" + (sample.value2 ?: "")

    companion object {
        fun at(dir: File): WatchSampleStore = WatchSampleStore(File(dir, WatchSync.STORE_FILE))
    }
}
