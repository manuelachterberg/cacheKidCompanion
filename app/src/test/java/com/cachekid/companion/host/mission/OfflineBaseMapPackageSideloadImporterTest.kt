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

class OfflineBaseMapPackageSideloadImporterTest {

    private val sideloadImporter = OfflineBaseMapPackageSideloadImporter()

    @Test
    fun `sideload importer installs latest offline map package and archives it`() {
        val tempRoot = createTempDirectory("cachekid-offline-map-sideload").toFile()
        val offlineMapBaseDirectory = File(tempRoot, "offline-maps")
        val sideloadDirectory = File(tempRoot, "offline-map-sideload").apply { mkdirs() }
        val zipFile = File(sideloadDirectory, "de-ni.zip")
        zipFile.writeBytes(validPackageZip())

        val result = sideloadImporter.importLatest(
            offlineMapBaseDirectory = offlineMapBaseDirectory,
            candidateDirectories = listOf(sideloadDirectory),
        )

        assertEquals(OfflineBaseMapPackageSideloadStatus.IMPORTED, result.status)
        assertTrue(requireNotNull(result.installResult).isSuccess)
        assertFalse(zipFile.exists())
        assertTrue(File(sideloadDirectory, "imported/de-ni.zip").exists())
        assertTrue(File(offlineMapBaseDirectory, "de-ni/map.pmtiles").exists())
    }

    @Test
    fun `sideload importer archives invalid package as failed`() {
        val tempRoot = createTempDirectory("cachekid-offline-map-sideload-failed").toFile()
        val offlineMapBaseDirectory = File(tempRoot, "offline-maps")
        val sideloadDirectory = File(tempRoot, "offline-map-sideload").apply { mkdirs() }
        val zipFile = File(sideloadDirectory, "broken.zip")
        zipFile.writeText("not-a-zip")

        val result = sideloadImporter.importLatest(
            offlineMapBaseDirectory = offlineMapBaseDirectory,
            candidateDirectories = listOf(sideloadDirectory),
        )

        assertEquals(OfflineBaseMapPackageSideloadStatus.IMPORT_FAILED, result.status)
        assertTrue(result.errors.isNotEmpty())
        assertFalse(zipFile.exists())
        assertTrue(File(sideloadDirectory, "failed/broken.zip").exists())
        assertTrue(File(sideloadDirectory, "failed/broken.zip.error.txt").exists())
    }

    private fun validPackageZip(): ByteArray {
        return zipOf(
            MissionPackageSchema.OFFLINE_MAP_METADATA_FILE to """
                {
                  "id": "de-ni",
                  "displayName": "Lower Saxony",
                  "format": "pmtiles-vector",
                  "bounds": {
                    "minLatitude": 51.20,
                    "minLongitude": 6.30,
                    "maxLatitude": 53.90,
                    "maxLongitude": 11.70
                  }
                }
            """.trimIndent().toByteArray(),
            MissionPackageSchema.OFFLINE_MAP_PMTILES_FILE to byteArrayOf(1, 2, 3),
            MissionPackageSchema.OFFLINE_MAP_STYLE_FILE to """{"version":8}""".toByteArray(),
        )
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
