package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class BtPayloadTest {
    @Test
    fun roundTrip() {
        val packed = BtPayload.pack(BtPayload.SAMPLES, "{\"v\":1}")
        val unpacked = BtPayload.unpack(packed)
        assertEquals(BtPayload.SAMPLES, unpacked?.first)
        assertEquals("{\"v\":1}", unpacked?.second)
    }

    @Test
    fun ackHasEmptyBody() {
        val unpacked = BtPayload.unpack(BtPayload.pack(BtPayload.ACK))
        assertEquals(BtPayload.ACK, unpacked?.first)
        assertEquals("", unpacked?.second)
    }
}
