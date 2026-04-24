package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MissionPackageSideloadImporterTest {

    private val writer = MissionPackageWriter()
    private val zipCodec = MissionPackageZipCodec()
    private val sideloadImporter = MissionPackageSideloadImporter()

    @Test
    fun `sideload importer imports latest zip package and archives it`() {
        val tempRoot = createTempDirectory("cachekid-sideload-success").toFile()
        val missionBaseDirectory = File(tempRoot, "missions")
        val sideloadDirectory = File(tempRoot, "sideload").apply { mkdirs() }
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
        val zipFile = File(sideloadDirectory, "mission.zip")
        zipFile.writeBytes(zipCodec.encode(missionPackage))

        val result = sideloadImporter.importLatest(
            missionBaseDirectory = missionBaseDirectory,
            candidateDirectories = listOf(sideloadDirectory),
        )

        assertEquals(MissionPackageSideloadStatus.IMPORTED, result.status)
        assertTrue(requireNotNull(result.importResult).isSuccess)
        assertFalse(zipFile.exists())
        assertTrue(File(sideloadDirectory, "imported/mission.zip").exists())
        assertTrue(File(missionBaseDirectory, "${missionPackage.missionId}/mission.json").exists())
    }

    @Test
    fun `sideload importer archives invalid zip package as failed`() {
        val tempRoot = createTempDirectory("cachekid-sideload-failure").toFile()
        val missionBaseDirectory = File(tempRoot, "missions")
        val sideloadDirectory = File(tempRoot, "sideload").apply { mkdirs() }
        val zipFile = File(sideloadDirectory, "broken.zip")
        zipFile.writeText("not-a-valid-zip")

        val result = sideloadImporter.importLatest(
            missionBaseDirectory = missionBaseDirectory,
            candidateDirectories = listOf(sideloadDirectory),
        )

        assertEquals(MissionPackageSideloadStatus.IMPORT_FAILED, result.status)
        assertTrue(result.errors.isNotEmpty())
        assertFalse(zipFile.exists())
        assertTrue(File(sideloadDirectory, "failed/broken.zip").exists())
        assertTrue(File(sideloadDirectory, "failed/broken.zip.error.txt").exists())
    }

    private fun validDraft(): MissionDraft {
        return MissionDraft(
            cacheCode = "GC12345",
            sourceTitle = "Old Oak Cache",
            childTitle = "Der Schatz im Wald",
            summary = "Folge der Karte bis zum grossen X.",
            target = MissionTarget(52.520008, 13.404954),
            sourceApp = "geocaching",
        )
    }
}
