package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class OfflineBaseMapPackageZipInstallerTest {

    private val installer = OfflineBaseMapPackageZipInstaller()

    @Test
    fun `installer stores valid pmtiles package`() {
        val baseDirectory = createTempDirectory("cachekid-offline-map-install").toFile()

        val result = installer.install(baseDirectory, validPackageZip())

        assertTrue(result.isSuccess)
        assertEquals("de-ni", result.offlinePackage?.id)
        assertTrue(File(baseDirectory, "de-ni/offline-map.json").exists())
        assertTrue(File(baseDirectory, "de-ni/map.pmtiles").exists())
        assertTrue(File(baseDirectory, "de-ni/style.json").exists())
    }

    @Test
    fun `installer rejects package with missing pmtiles archive`() {
        val baseDirectory = createTempDirectory("cachekid-offline-map-missing").toFile()
        val result = installer.install(
            baseDirectory,
            zipOf(
                MissionPackageSchema.OFFLINE_MAP_METADATA_FILE to validMetadata().toByteArray(),
                MissionPackageSchema.OFFLINE_MAP_STYLE_FILE to """{"version":8}""".toByteArray(),
            ),
        )

        assertFalse(result.isSuccess)
        assertTrue(result.errors.joinToString(" ").contains("map.pmtiles"))
    }

    @Test
    fun `installer rejects unsafe zip entry paths`() {
        val baseDirectory = createTempDirectory("cachekid-offline-map-unsafe").toFile()
        val result = installer.install(
            baseDirectory,
            zipOf("../offline-map.json" to validMetadata().toByteArray()),
        )

        assertFalse(result.isSuccess)
        assertEquals(listOf("Offline map ZIP could not be decoded."), result.errors)
    }

    private fun validPackageZip(): ByteArray {
        return zipOf(
            MissionPackageSchema.OFFLINE_MAP_METADATA_FILE to validMetadata().toByteArray(),
            MissionPackageSchema.OFFLINE_MAP_PMTILES_FILE to byteArrayOf(1, 2, 3),
            MissionPackageSchema.OFFLINE_MAP_STYLE_FILE to """{"version":8}""".toByteArray(),
        )
    }

    private fun validMetadata(): String {
        return """
            {
              "id": "de-ni",
              "displayName": "Lower Saxony",
              "version": "2026.04",
              "format": "pmtiles-vector",
              "bounds": {
                "minLatitude": 51.20,
                "minLongitude": 6.30,
                "maxLatitude": 53.90,
                "maxLongitude": 11.70
              }
            }
        """.trimIndent()
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipOutput ->
            entries.forEach { (path, bytes) ->
                zipOutput.putNextEntry(ZipEntry(path))
                zipOutput.write(bytes)
                zipOutput.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
