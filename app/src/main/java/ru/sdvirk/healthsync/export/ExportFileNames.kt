package ru.sdvirk.healthsync.export

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object ExportFileNames {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")

    fun zipName(now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): String =
        "health_export_${now.format(stamp)}.zip"
}
