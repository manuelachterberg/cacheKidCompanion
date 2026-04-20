package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ActiveMissionRepositoryTest {

    private val writer = MissionPackageWriter()
    private val fileStore = MissionPackageFileStore()
    private val repository = ActiveMissionRepository()

    @Test
    fun `repository loads latest stored mission`() {
        val baseDirectory = createTempDirectory("cachekid-active-mission").toFile()
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
        val storeResult = fileStore.store(baseDirectory, missionPackage)
        val missionDirectory = requireNotNull(storeResult.missionDirectory)

        val mission = repository.loadLatest(baseDirectory)

        assertNotNull(mission)
        assertEquals(missionDirectory.name, mission?.missionId)
        assertEquals("GC12345", mission?.cacheCode)
        assertEquals("Der Schatz im Wald", mission?.childTitle)
    }

    @Test
    fun `repository returns null without mission json`() {
        val emptyDirectory = createTempDirectory("cachekid-empty-mission").toFile()
        File(emptyDirectory, "broken").mkdirs()

        assertNull(repository.loadLatest(emptyDirectory))
    }

    private fun validDraft(): MissionDraft {
        return MissionDraft(
            cacheCode = "GC12345",
            sourceTitle = "Old Oak Cache",
            childTitle = "Der Schatz im Wald",
            summary = "Folge dem Pfeil bis zum Schatz.",
            target = MissionTarget(52.520008, 13.404954),
            sourceApp = "geocaching",
        )
    }
}
