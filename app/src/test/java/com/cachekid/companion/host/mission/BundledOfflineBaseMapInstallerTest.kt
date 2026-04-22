package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class BundledOfflineBaseMapInstallerTest {

    @Test
    fun `installer copies bundled base map assets into target directory`() {
        val targetDirectory = createTempDirectory("cachekid-basemap-install").toFile()
        val installer = BundledOfflineBaseMapInstaller(
            assetProvider = object : BundledOfflineBaseMapInstaller.BaseMapAssetProvider {
                private val files = mapOf<String, ByteArray>(
                    "offline-basemaps/demo/map.png" to byteArrayOf(1, 2, 3),
                    "offline-basemaps/demo/map-meta.json" to """{"assetPath":"map.png"}""".encodeToByteArray(),
                )

                override fun list(path: String): List<String> {
                    return when (path) {
                        "offline-basemaps" -> listOf("demo")
                        "offline-basemaps/demo" -> listOf("map.png", "map-meta.json")
                        else -> emptyList()
                    }
                }

                override fun readBytes(path: String): ByteArray = requireNotNull(files[path])
            },
        )

        installer.installOrReplace(targetDirectory)

        assertTrue(targetDirectory.resolve("demo/map.png").exists())
        assertTrue(targetDirectory.resolve("demo/map-meta.json").exists())
        assertEquals(3L, targetDirectory.resolve("demo/map.png").length())
    }

    @Test
    fun `installer replaces older bundled base map assets`() {
        val targetDirectory = createTempDirectory("cachekid-basemap-replace").toFile()
        val existingMapDirectory = targetDirectory.resolve("demo").apply { mkdirs() }
        existingMapDirectory.resolve("map-meta.json").writeText("""{"assetPath":"map.svg"}""")
        existingMapDirectory.resolve("map.svg").writeText("<g>old</g>")

        val installer = BundledOfflineBaseMapInstaller(
            assetProvider = object : BundledOfflineBaseMapInstaller.BaseMapAssetProvider {
                override fun list(path: String): List<String> {
                    return when (path) {
                        "offline-basemaps" -> listOf("demo")
                        "offline-basemaps/demo" -> listOf("map.png", "map-meta.json")
                        else -> emptyList()
                    }
                }

                override fun readBytes(path: String): ByteArray {
                    return when (path) {
                        "offline-basemaps/demo/map.png" -> byteArrayOf(9, 9, 9, 9)
                        "offline-basemaps/demo/map-meta.json" -> """{"assetPath":"map.png"}""".encodeToByteArray()
                        else -> error("Unexpected asset path: $path")
                    }
                }
            },
        )

        installer.installOrReplace(targetDirectory)

        assertTrue(targetDirectory.resolve("demo/map.png").exists())
        assertEquals(4L, targetDirectory.resolve("demo/map.png").length())
    }
}
