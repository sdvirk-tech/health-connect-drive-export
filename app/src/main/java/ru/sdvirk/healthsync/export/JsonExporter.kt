package ru.sdvirk.healthsync.export

import org.json.JSONArray
import org.json.JSONObject
import ru.sdvirk.healthsync.health.HealthSnapshot
import java.io.File
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Пишет снимок в JSON и упаковывает в zip (как сейчас лежит в Drive-папке).
 * TODO: при желании заменить на SQLite как в health_connect_export.db.
 */
object JsonExporter {

    private val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun writeZip(snapshot: HealthSnapshot, outFile: File): File {
        outFile.parentFile?.mkdirs()
        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("health_export.json"))
            zos.write(toJson(snapshot).toString(2).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("summary.txt"))
            zos.write(snapshot.summaryLines().joinToString("\n").toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return outFile
    }

    fun toJson(s: HealthSnapshot): JSONObject {
        fun inst(i: java.time.Instant) =
            iso.format(i.atOffset(ZoneOffset.UTC))

        return JSONObject().apply {
            put("exportedAt", inst(s.exportedAt))
            put("rangeStart", inst(s.rangeStart))
            put("rangeEnd", inst(s.rangeEnd))
            put("heartRate", JSONArray().apply {
                s.heartRate.forEach { rec ->
                    rec.samples.forEach { sample ->
                        put(JSONObject().put("time", inst(sample.time)).put("bpm", sample.beatsPerMinute))
                    }
                }
            })
            put("restingHeartRate", JSONArray().apply {
                s.restingHeartRate.forEach { r ->
                    put(JSONObject().put("time", inst(r.time)).put("bpm", r.beatsPerMinute))
                }
            })
            put("hrvRmssd", JSONArray().apply {
                s.hrv.forEach { r ->
                    put(JSONObject().put("time", inst(r.time)).put("ms", r.heartRateVariabilityMillis))
                }
            })
            put("sleep", JSONArray().apply {
                s.sleep.forEach { r ->
                    put(
                        JSONObject()
                            .put("start", inst(r.startTime))
                            .put("end", inst(r.endTime))
                            .put("stages", r.stages.size)
                    )
                }
            })
            put("spo2", JSONArray().apply {
                s.spo2.forEach { r ->
                    put(JSONObject().put("time", inst(r.time)).put("percent", r.percentage.value))
                }
            })
            put("bloodPressure", JSONArray().apply {
                s.bloodPressure.forEach { r ->
                    put(
                        JSONObject()
                            .put("time", inst(r.time))
                            .put("systolic", r.systolic.inMillimetersOfMercury)
                            .put("diastolic", r.diastolic.inMillimetersOfMercury)
                    )
                }
            })
            put("weight", JSONArray().apply {
                s.weight.forEach { r ->
                    put(JSONObject().put("time", inst(r.time)).put("kg", r.weight.inKilograms))
                }
            })
            put("steps", JSONArray().apply {
                s.steps.forEach { r ->
                    put(
                        JSONObject()
                            .put("start", inst(r.startTime))
                            .put("end", inst(r.endTime))
                            .put("count", r.count)
                    )
                }
            })
            put("distance", JSONArray().apply {
                s.distance.forEach { r ->
                    put(
                        JSONObject()
                            .put("start", inst(r.startTime))
                            .put("end", inst(r.endTime))
                            .put("meters", r.distance.inMeters)
                    )
                }
            })
            put("exercise", JSONArray().apply {
                s.exercise.forEach { r ->
                    put(
                        JSONObject()
                            .put("start", inst(r.startTime))
                            .put("end", inst(r.endTime))
                            .put("type", r.exerciseType)
                            .put("title", r.title ?: "")
                    )
                }
            })
        }
    }
}
