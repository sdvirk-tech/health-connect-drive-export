package ru.sdvirk.healthsync.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchSampleTypeKeyTest {
    @Test
    fun mapsHealthServicesNames() {
        assertEquals(WatchSample.HEART_RATE, WatchSample.typeKey("HeartRate"))
        assertEquals(WatchSample.STEPS, WatchSample.typeKey("Steps"))
        assertEquals(WatchSample.STEPS_DAILY, WatchSample.typeKey("Daily Steps"))
        assertEquals(WatchSample.CALORIES, WatchSample.typeKey("Calories"))
        assertEquals(WatchSample.CALORIES_DAILY, WatchSample.typeKey("Daily Calories"))
        assertEquals(WatchSample.DISTANCE, WatchSample.typeKey("Distance"))
        assertEquals(WatchSample.DISTANCE_DAILY, WatchSample.typeKey("Daily Distance"))
        assertEquals(WatchSample.FLOORS, WatchSample.typeKey("Floors"))
        assertEquals(WatchSample.FLOORS_DAILY, WatchSample.typeKey("Daily Floors"))
        assertEquals(WatchSample.ELEVATION_GAIN, WatchSample.typeKey("Elevation Gain"))
        assertEquals(WatchSample.ELEVATION_GAIN_DAILY, WatchSample.typeKey("Daily Elevation Gain"))
        assertEquals("custom_metric", WatchSample.typeKey("Custom Metric"))
    }
}
