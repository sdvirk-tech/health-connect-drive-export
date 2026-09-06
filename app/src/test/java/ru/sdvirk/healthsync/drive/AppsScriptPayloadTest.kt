package ru.sdvirk.healthsync.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AppsScriptPayloadTest {

    @Test
    fun encodeContainsBase64AndFileName() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)
        val json = AppsScriptPayload.encode(
            fileName = "health_export_2026-09-06_1200.zip",
            fileBytes = bytes,
            secret = "s3cret",
        )
        assertTrue(json.contains("\"fileName\":\"health_export_2026-09-06_1200.zip\""))
        assertTrue(json.contains("\"mimeType\":\"application/zip\""))
        assertTrue(json.contains("\"secret\":\"s3cret\""))
        assertTrue(json.contains("\"fileBase64\":\"" + Base64.getEncoder().encodeToString(bytes) + "\""))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun omitBlankSecret() {
        val json = AppsScriptPayload.encode("a.zip", byteArrayOf(1), secret = "")
        assertFalse(json.contains("secret"))
    }

    @Test
    fun escapeQuotesInFileName() {
        val json = AppsScriptPayload.toJsonObject(mapOf("fileName" to "a\"b\\c.zip"))
        assertEquals("{\"fileName\":\"a\\\"b\\\\c.zip\"}", json)
    }
}
