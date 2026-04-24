package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MissionPackageFileStoreTest {

    private val writer = MissionPackageWriter()
    private val store = MissionPackageFileStore()

    @Test
    fun `store writes mission package into deterministic mission directory`() {
        val tempDir = createTempDirectory("cachekid-mission-store").toFile()
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)

        val result = store.store(tempDir, missionPackage)

        assertTrue(result.isSuccess)
        val missionDirectory = requireNotNull(result.missionDirectory)
        assertEquals(missionPackage.missionId, missionDirectory.name)
        assertTrue(File(missionDirectory, "manifest.json").exists())
        assertTrue(File(missionDirectory, "mission.json").exists())
        assertTrue(File(missionDirectory, "integrity.json").exists())
    }

    @Test
    fun `store rejects mission package with missing required files`() {
        val tempDir = createTempDirectory("cachekid-mission-store-invalid").toFile()
        val invalidPackage = MissionPackage(
            missionId = "gc12345-test",
            manifest = MissionManifest(
                schemaVersion = MissionPackageSchema.CURRENT_SCHEMA_VERSION,
                missionId = "gc12345-test",
                files = MissionPackageSchema.requiredCoreFiles,
            ),
            files = listOf(
                MissionPackageFile("mission.json", "{}"),
            ),
        )

        val result = store.store(tempDir, invalidPackage)

        assertFalse(result.isSuccess)
        assertEquals(null, result.missionDirectory)
        assertNotNull(result.errors.firstOrNull())
    }

    private fun validDraft(): MissionDraft {
        return MissionDraft(
            cacheCode = "GC12345",
            sourceTitle = "Old Oak Cache",
            childTitle = "Der Schatz im Wald",
            summary = "Folge der Karte bis zum grossen X.",
            target = MissionTarget(
                latitude = 52.520008,
                longitude = 13.404954,
            ),
            sourceApp = "geocaching",
        )
    }
}
