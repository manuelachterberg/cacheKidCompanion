package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionPackageWriterTest {

    private val writer = MissionPackageWriter()

    @Test
    fun `writer produces deterministic mission package layout`() {
        val draft = validDraft()

        val first = writer.write(draft)
        val second = writer.write(draft)

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(first.missionPackage, second.missionPackage)
        assertEquals(
            listOf("integrity.json", "manifest.json", "mission.json"),
            first.missionPackage?.files?.map { it.path },
        )
    }

    @Test
    fun `writer includes optional offline map files when present`() {
        val result = writer.write(
            validDraft().copy(
                offlineMap = MissionOfflineMap(
                    svgContent = "<path d=\"M 0 0 L 10 10\" />",
                    bounds = MissionMapBounds(
                        minLatitude = 52.5,
                        minLongitude = 13.3,
                        maxLatitude = 52.6,
                        maxLongitude = 13.5,
                    ),
                ),
            ),
        )

        val missionPackage = requireNotNull(result.missionPackage)

        assertTrue(missionPackage.files.any { it.path == MissionPackageSchema.MAP_SVG_FILE })
        assertTrue(missionPackage.files.any { it.path == MissionPackageSchema.MAP_METADATA_FILE })
    }

    @Test
    fun `writer includes integrity checksum for mission json`() {
        val result = writer.write(validDraft())

        val missionPackage = requireNotNull(result.missionPackage)
        val integrityFile = missionPackage.files.firstOrNull { it.path == "integrity.json" }
        val missionFile = missionPackage.files.firstOrNull { it.path == "mission.json" }

        assertNotNull(integrityFile)
        assertNotNull(missionFile)
        assertTrue(integrityFile!!.content.contains("\"algorithm\": \"sha256\""))
        assertTrue(integrityFile.content.contains("missionJsonSha256"))
        assertTrue(missionFile!!.content.contains("\"missionId\": \"gc12345-der-schatz-im-wald\""))
    }

    @Test
    fun `writer returns validation errors for invalid draft`() {
        val result = writer.write(
            MissionDraft(
                cacheCode = "",
                sourceTitle = "",
                childTitle = "",
                summary = "",
                target = MissionTarget(999.0, 999.0),
            ),
        )

        assertFalse(result.isSuccess)
        assertEquals(null, result.missionPackage)
        assertTrue(result.errors.isNotEmpty())
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
