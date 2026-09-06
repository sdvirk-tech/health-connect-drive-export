package ru.sdvirk.healthsync.export

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ExportFileNamesTest {

    @Test
    fun datedZipName() {
        val instant = ZonedDateTime.of(
            LocalDateTime.of(2026, 9, 6, 21, 5),
            ZoneId.of("UTC")
        )
        assertEquals("health_export_2026-09-06_2105.zip", ExportFileNames.zipName(instant))
    }
}
