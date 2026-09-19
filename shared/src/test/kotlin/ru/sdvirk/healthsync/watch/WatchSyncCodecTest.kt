package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class WatchSyncCodecTest {

    @Test
    fun roundTripLineAndMessage() {
        val sample = WatchSample(WatchSample.HEART_RATE, 1_725_000_000_000L, 72.5)
        assertEquals(sample, WatchSyncCodec.fromLine(WatchSyncCodec.toLine(sample)))
        val extra = WatchSample(WatchSample.BLOOD_PRESSURE, 3_000L, 120.0, 80.0, "mmHg", WatchSample.SOURCE_HC)
        assertEquals(extra, WatchSyncCodec.fromLine(WatchSyncCodec.toLine(extra)))
        val old = WatchSyncCodec.fromLine("{\"type\":\"heart_rate\",\"t\":1,\"v\":72.5}")
        assertEquals(WatchSample.HEART_RATE, old?.type)
        assertEquals(72.5, old?.value ?: 0.0, 0.0)
        assertEquals(WatchSample.SOURCE_SENSOR, old?.source)
        val json = WatchSyncCodec.encodeMessage(listOf(sample, sample.copy(timeEpochMs = 1_725_000_001_000L)))
        val decoded = WatchSyncCodec.decodeMessage(json)
        assertEquals(2, decoded.size)
        assertEquals(72.5, decoded[0].value, 0.0)
        assertEquals(WatchSample.HEART_RATE, decoded[0].type)
    }

    @Test
    fun chunkSplitsWhenOverLimit() {
        val samples = (0 until 50).map {
            WatchSample(WatchSample.HEART_RATE, 1_000L + it, 60.0 + it)
        }
        val chunks = WatchSyncCodec.chunk(samples, maxBytes = 400)
        assertTrue(chunks.size > 1)
        val restored = chunks.flatMap { WatchSyncCodec.decodeMessage(it.toString(Charsets.UTF_8)) }
        assertEquals(samples, restored)
    }
}

class WatchSampleStoreTest {

    @Test
    fun appendDedupAndRange() {
        val dir = createTempDirectory("watch-store").toFile()
        val store = WatchSampleStore.at(dir)
        val a = WatchSample(WatchSample.HEART_RATE, 1000L, 70.0)
        val b = WatchSample(WatchSample.HEART_RATE, 2000L, 80.0)
        store.append(listOf(a, a, b))
        assertEquals(2, store.count())
        assertEquals(listOf(b), store.read(1500L, 2500L))
        assertEquals(listOf(b), store.readAfter(1000L))
        store.pruneOlderThan(1500L)
        assertEquals(listOf(b), store.readAll())
        dir.deleteRecursively()
    }
}
