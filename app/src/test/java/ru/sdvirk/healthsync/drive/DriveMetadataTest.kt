package ru.sdvirk.healthsync.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMetadataTest {

    @Test
    fun folderIdUnchanged() {
        assertEquals("10pwzTmlxVLAshc7_DfOuecSktde-HKjw", DriveConfig.FOLDER_ID)
    }

    @Test
    fun createFileJsonUsesParentsAndUniqueName() {
        val json = DriveMetadata.createFileJson("health_export_2026-09-07_1200.zip")
        assertEquals(
            "{\"name\":\"health_export_2026-09-07_1200.zip\"," +
                "\"mimeType\":\"application/zip\"," +
                "\"parents\":[\"10pwzTmlxVLAshc7_DfOuecSktde-HKjw\"]}",
            json
        )
        assertTrue(json.contains("\"parents\":[\"10pwzTmlxVLAshc7_DfOuecSktde-HKjw\"]"))
    }

    @Test
    fun createFileJsonEscapesQuotes() {
        val json = DriveMetadata.createFileJson("a\"b.zip")
        assertTrue(json.contains("\"name\":\"a\\\"b.zip\""))
    }

    @Test
    fun summarizeUploadReadsDriveFields() {
        val json =
            """{"id":"abc123","name":"health_export_2026-09-07_1200.zip","webViewLink":"https://drive.google.com/file/d/abc123/view"}"""
        val summary = DriveMetadata.summarizeUpload(json)
        assertTrue(summary.contains("health_export_2026-09-07_1200.zip"))
        assertTrue(summary.contains("id=abc123"))
        assertTrue(summary.contains("https://drive.google.com/file/d/abc123/view"))
    }
}
