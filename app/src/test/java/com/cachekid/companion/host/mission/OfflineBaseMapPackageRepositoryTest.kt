package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class OfflineBaseMapPackageRepositoryTest {

    @Test
    fun `repository discovers covering pmtiles package`() {
        val baseDirectory = createTempDirectory("cachekid-real-offline-map").toFile()
        createOfflinePackage(
            baseDirectory = baseDirectory,
            directoryName = "lower-saxony",
            metadata = """
                {
                  "id": "de-ni",
                  "displayName": "Lower Saxony",
                  "version": "2026.04",
                  "format": "pmtiles-vector",
                  "tileAssetPath": "map.pmtiles",
                  "styleAssetPath": "style.json",
                  "minZoom": 0,
                  "maxZoom": 14,
                  "attribution": "OpenStreetMap contributors",
                  "bounds": {
                    "minLatitude": 51.20,
                    "minLongitude": 6.30,
                    "maxLatitude": 53.90,
                    "maxLongitude": 11.70
                  }
                }
            """.trimIndent(),
        )

        val repository = OfflineBaseMapPackageRepository(baseDirectory)

        val offlinePackage = repository.findCovering(MissionTarget(52.617, 10.0543))

        assertNotNull(offlinePackage)
        assertEquals("de-ni", offlinePackage?.id)
        assertEquals("Lower Saxony", offlinePackage?.displayName)
        assertEquals(OfflineBaseMapPackageFormat.PMTILES_VECTOR, offlinePackage?.format)
        assertEquals("map.pmtiles", offlinePackage?.tileAssetPath)
        assertEquals("style.json", offlinePackage?.styleAssetPath)
        assertEquals("lower-saxony", offlinePackage?.packageDirectory?.name)
        assertEquals(14, offlinePackage?.maxZoom)
    }

    @Test
    fun `repository rejects static legacy basemap package as real offline map`() {
        val baseDirectory = createTempDirectory("cachekid-legacy-basemap").toFile()
        val legacyDirectory = File(baseDirectory, "celle-tile").apply { mkdirs() }
        File(legacyDirectory, MissionPackageSchema.MAP_PNG_FILE).writeBytes(byteArrayOf(1, 2, 3))
        File(legacyDirectory, MissionPackageSchema.MAP_METADATA_FILE).writeText(
            """
                {
                  "assetPath": "map.png",
                  "bounds": {
                    "minLatitude": 52.60,
                    "minLongitude": 10.04,
                    "maxLatitude": 52.65,
                    "maxLongitude": 10.11
                  }
                }
            """.trimIndent(),
        )

        val repository = OfflineBaseMapPackageRepository(baseDirectory)

        assertNull(repository.findCovering(MissionTarget(52.617, 10.0543)))
    }

    @Test
    fun `repository does not use nearest package when target is outside coverage`() {
        val baseDirectory = createTempDirectory("cachekid-real-offline-map-outside").toFile()
        createOfflinePackage(
            baseDirectory = baseDirectory,
            directoryName = "berlin",
            metadata = """
                {
                  "format": "pmtiles-vector",
                  "bounds": {
                    "minLatitude": 52.40,
                    "minLongitude": 13.20,
                    "maxLatitude": 52.70,
                    "maxLongitude": 13.60
                  }
                }
            """.trimIndent(),
        )

        val repository = OfflineBaseMapPackageRepository(baseDirectory)

        assertNull(repository.findCovering(MissionTarget(52.617, 10.0543)))
    }

    private fun createOfflinePackage(
        baseDirectory: File,
        directoryName: String,
        metadata: String,
    ) {
        val packageDirectory = File(baseDirectory, directoryName).apply { mkdirs() }
        File(packageDirectory, MissionPackageSchema.OFFLINE_MAP_PMTILES_FILE).writeBytes(byteArrayOf(1, 2, 3))
        File(packageDirectory, MissionPackageSchema.OFFLINE_MAP_STYLE_FILE).writeText("""{"version":8}""")
        File(packageDirectory, MissionPackageSchema.OFFLINE_MAP_METADATA_FILE).writeText(metadata)
    }
}
