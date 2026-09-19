package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNodePickerTest {
    @Test
    fun prefersNearbyCapabilityNode() {
        val cap = listOf(
            WearNodeRef("far", nearby = false),
            WearNodeRef("near", nearby = true),
        )
        assertEquals("near", WatchNodePicker.pick(cap, emptyList())?.id)
    }

    @Test
    fun fallsBackToConnectedWhenCapabilityEmpty() {
        val connected = listOf(WearNodeRef("phone", nearby = true, displayName = "Pixel"))
        assertEquals("phone", WatchNodePicker.pick(emptyList(), connected)?.id)
    }

    @Test
    fun noneWhenNoNodes() {
        assertNull(WatchNodePicker.pick(emptyList(), emptyList()))
    }
}
