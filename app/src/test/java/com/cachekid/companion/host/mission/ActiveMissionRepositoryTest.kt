package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `repository loads offline map when mission package contains map files`() {
        val baseDirectory = createTempDirectory("cachekid-active-mission-map").toFile()
        val missionPackage = requireNotNull(writer.write(validDraftWithOfflineMap()).missionPackage)
        val storeResult = fileStore.store(baseDirectory, missionPackage)
        val missionDirectory = requireNotNull(storeResult.missionDirectory)

        val mission = repository.loadFromDirectory(missionDirectory)

        assertNotNull(mission)
        assertNotNull(mission?.offlineMap)
        assertTrue(mission?.offlineMap?.svgContent?.contains("""<path d="M 0 0 L 100 140">""") == true)
        assertEquals(52.50, mission?.offlineMap?.bounds?.minLatitude)
        assertEquals(13.30, mission?.offlineMap?.bounds?.minLongitude)
        assertEquals(52.60, mission?.offlineMap?.bounds?.maxLatitude)
        assertEquals(13.50, mission?.offlineMap?.bounds?.maxLongitude)
    }

    @Test
    fun `repository loads waypoints from mission json`() {
        val baseDirectory = createTempDirectory("cachekid-active-mission-waypoints").toFile()
        val missionPackage = requireNotNull(
            writer.write(
                validDraft().copy(
                    routeOrigin = MissionTarget(52.5200, 13.4040),
                    waypoints = listOf(
                        MissionWaypoint(52.5205, 13.4050, "Start"),
                        MissionWaypoint(52.5210, 13.4060, "Ecke"),
                    ),
                ),
            ).missionPackage,
        )
        val storeResult = fileStore.store(baseDirectory, missionPackage)
        val missionDirectory = requireNotNull(storeResult.missionDirectory)

        val mission = repository.loadFromDirectory(missionDirectory)

        assertNotNull(mission)
        assertEquals(2, mission?.waypoints?.size)
        assertEquals(52.5200, mission?.routeOrigin?.latitude ?: 0.0, 0.000001)
        assertEquals("Start", mission?.waypoints?.firstOrNull()?.label)
        assertEquals(52.5210, mission?.waypoints?.get(1)?.latitude ?: 0.0, 0.000001)
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

    private fun validDraftWithOfflineMap(): MissionDraft {
        return validDraft().copy(
            offlineMap = MissionOfflineMap(
                svgContent = """<svg viewBox="0 0 100 140"><path d="M 0 0 L 100 140"></path></svg>""",
                assetPath = MissionPackageSchema.MAP_SVG_FILE,
                bounds = MissionMapBounds(
                    minLatitude = 52.50,
                    minLongitude = 13.30,
                    maxLatitude = 52.60,
                    maxLongitude = 13.50,
                ),
            ),
        )
    }
}
