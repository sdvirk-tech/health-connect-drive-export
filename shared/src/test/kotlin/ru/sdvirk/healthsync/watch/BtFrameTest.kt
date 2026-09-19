package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BtFrameTest {
    @Test
    fun roundTrip() {
        val payload = BtPayload.pack(BtPayload.SAMPLES, "{\"v\":1}")
        val buf = ByteArrayOutputStream()
        BtFrame.write(buf, payload)
        val read = BtFrame.read(ByteArrayInputStream(buf.toByteArray()))
        assertArrayEquals(payload, read)
        val unpacked = BtPayload.unpack(read!!)
        assertEquals(BtPayload.SAMPLES, unpacked?.first)
        assertEquals("{\"v\":1}", unpacked?.second)
    }
}
