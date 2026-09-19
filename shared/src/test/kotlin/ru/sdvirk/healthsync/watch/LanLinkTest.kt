package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanLinkTest {
    @Test
    fun roundTripBeacon() {
        val encoded = LanLink.encodeBeacon("192.168.2.10", 8765)
        assertEquals("192.168.2.10" to 8765, LanLink.parseBeacon(encoded))
    }

    @Test
    fun rejectGarbage() {
        assertNull(LanLink.parseBeacon("hello"))
        assertNull(LanLink.parseBeacon("HEALTHSYNC/1 not-an-ip 8765"))
    }
}
