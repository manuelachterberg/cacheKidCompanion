package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DeviceOfflineBaseMapRepositoryTest {

    @Test
    fun `repository loads matching base map for mission target`() {
        val baseDirectory = createTempDirectory("cachekid-offline-basemap").toFile()
        val mapDirectory = File(baseDirectory, "berlin")
        mapDirectory.mkdirs()
        File(mapDirectory, MissionPackageSchema.MAP_PNG_FILE).writeBytes(byteArrayOf(1, 2, 3, 4))
        File(mapDirectory, MissionPackageSchema.MAP_METADATA_FILE).writeText(
            """
                {
                  "assetPath": "map.png",
                  "bounds": {
                    "minLatitude": 52.40,
                    "minLongitude": 13.20,
                    "maxLatitude": 52.70,
                    "maxLongitude": 13.60
                  }
                }
            """.trimIndent(),
        )

        val repository = DeviceOfflineBaseMapRepository(baseDirectory)

        val map = repository.loadFor(MissionTarget(52.520008, 13.404954))

        assertNotNull(map)
        assertEquals("map.png", map?.assetPath)
        assertTrue(map?.svgContent?.contains("data:image/png;base64") == true)
    }

    @Test
    fun `repository returns null when no base map matches target`() {
        val baseDirectory = createTempDirectory("cachekid-offline-basemap-empty").toFile()
        val repository = DeviceOfflineBaseMapRepository(baseDirectory)

        val map = repository.loadFor(MissionTarget(52.520008, 13.404954))

        assertNull(map)
    }

    @Test
    fun `repository falls back to nearest base map when target is just outside bounds`() {
        val baseDirectory = createTempDirectory("cachekid-offline-basemap-nearest").toFile()
        val mapDirectory = File(baseDirectory, "celle")
        mapDirectory.mkdirs()
        File(mapDirectory, MissionPackageSchema.MAP_PNG_FILE).writeBytes(byteArrayOf(1, 2, 3, 4))
        File(mapDirectory, MissionPackageSchema.MAP_METADATA_FILE).writeText(
            """
                {
                  "assetPath": "map.png",
                  "bounds": {
                    "minLatitude": 52.629728867183566,
                    "minLongitude": 10.0634765625,
                    "maxLatitude": 52.643063436658906,
                    "maxLongitude": 10.08544921875
                  }
                }
            """.trimIndent(),
        )

        val repository = DeviceOfflineBaseMapRepository(baseDirectory)

        val map = repository.loadFor(MissionTarget(52.6292, 10.0685))

        assertNotNull(map)
        assertEquals("map.png", map?.assetPath)
    }

    @Test
    fun `repository uses nearest available base map even when target is outside all bounds`() {
        val baseDirectory = createTempDirectory("cachekid-offline-basemap-global-nearest").toFile()

        val berlinDirectory = File(baseDirectory, "berlin").apply { mkdirs() }
        File(berlinDirectory, MissionPackageSchema.MAP_PNG_FILE).writeBytes(byteArrayOf(1))
        File(berlinDirectory, MissionPackageSchema.MAP_METADATA_FILE).writeText(
            """
                {
                  "assetPath": "map.png",
                  "bounds": {
                    "minLatitude": 52.50,
                    "minLongitude": 13.30,
                    "maxLatitude": 52.60,
                    "maxLongitude": 13.50
                  }
                }
            """.trimIndent(),
        )

        val celleDirectory = File(baseDirectory, "celle").apply { mkdirs() }
        File(celleDirectory, MissionPackageSchema.MAP_PNG_FILE).writeBytes(byteArrayOf(2))
        File(celleDirectory, MissionPackageSchema.MAP_METADATA_FILE).writeText(
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

        val repository = DeviceOfflineBaseMapRepository(baseDirectory)

        val map = repository.loadFor(MissionTarget(52.70, 10.09))

        assertNotNull(map)
        assertEquals("map.png", map?.assetPath)
    }

    @Test
    fun `repository prefers png asset even when metadata points to svg`() {
        val baseDirectory = createTempDirectory("cachekid-offline-basemap-prefer-png").toFile()
        val mapDirectory = File(baseDirectory, "celle").apply { mkdirs() }
        File(mapDirectory, MissionPackageSchema.MAP_PNG_FILE).writeBytes(byteArrayOf(1, 2, 3, 4))
        File(mapDirectory, MissionPackageSchema.MAP_SVG_FILE).writeText("<svg><path d=\"M 0 0 L 10 10\" /></svg>")
        File(mapDirectory, MissionPackageSchema.MAP_METADATA_FILE).writeText(
            """
                {
                  "assetPath": "map.svg",
                  "bounds": {
                    "minLatitude": 52.60,
                    "minLongitude": 10.04,
                    "maxLatitude": 52.65,
                    "maxLongitude": 10.11
                  }
                }
            """.trimIndent(),
        )

        val repository = DeviceOfflineBaseMapRepository(baseDirectory)

        val map = repository.loadFor(MissionTarget(52.617, 10.0543))

        assertNotNull(map)
        assertEquals("map.png", map?.assetPath)
        assertTrue(map?.svgContent?.contains("data:image/png;base64") == true)
    }
}
