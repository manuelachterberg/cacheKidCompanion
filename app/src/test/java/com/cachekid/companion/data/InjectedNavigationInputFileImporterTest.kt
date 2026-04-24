package com.cachekid.companion.data

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectedNavigationInputFileImporterTest {

    private val importer = InjectedNavigationInputFileImporter(clock = { 1_700_000_000_000L })

    @Test
    fun `importer reads latest navigation json and archives it`() {
        val tempRoot = createTempDirectory("cachekid-nav-input-success").toFile()
        val inputDirectory = File(tempRoot, "nav-input").apply { mkdirs() }
        val jsonFile = File(inputDirectory, "current.json")
        jsonFile.writeText(
            """
                {
                  "latitude": 52.617,
                  "longitude": 10.0543,
                  "accuracyMeters": 5.0,
                  "headingDegrees": 281.4
                }
            """.trimIndent(),
        )

        val result = importer.importLatest(listOf(inputDirectory))

        assertEquals(InjectedNavigationInputImportStatus.IMPORTED, result.status)
        assertNotNull(result.injectedInput)
        assertEquals(52.617, result.injectedInput?.latitude ?: 0.0, 0.000001)
        assertEquals(281.4f, result.injectedInput?.headingDegrees ?: 0f, 0.0001f)
        assertFalse(jsonFile.exists())
        assertTrue(File(inputDirectory, "imported/current.json").exists())
    }

    @Test
    fun `importer archives invalid navigation json as failed`() {
        val tempRoot = createTempDirectory("cachekid-nav-input-failure").toFile()
        val inputDirectory = File(tempRoot, "nav-input").apply { mkdirs() }
        val jsonFile = File(inputDirectory, "broken.json")
        jsonFile.writeText("""{ "latitude": 52.617 }""")

        val result = importer.importLatest(listOf(inputDirectory))

        assertEquals(InjectedNavigationInputImportStatus.IMPORT_FAILED, result.status)
        assertTrue(result.errors.isNotEmpty())
        assertFalse(jsonFile.exists())
        assertTrue(File(inputDirectory, "failed/broken.json").exists())
        assertTrue(File(inputDirectory, "failed/broken.json.error.txt").exists())
    }
}
