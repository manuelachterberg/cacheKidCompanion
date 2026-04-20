package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class MissionPackageZipImporterTest {

    private val writer = MissionPackageWriter()
    private val zipCodec = MissionPackageZipCodec()
    private val importer = MissionPackageZipImporter()

    @Test
    fun `zip importer decodes validates and stores a mission package`() {
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
        val zipBytes = zipCodec.encode(missionPackage)
        val tempDir = createTempDirectory("cachekid-importer").toFile()

        val result = importer.import(tempDir, zipBytes)
        val missionDirectory = requireNotNull(result.missionDirectory)

        assertTrue(result.isSuccess)
        assertTrue(Files.exists(missionDirectory.toPath().resolve("mission.json")))
        assertTrue(Files.exists(missionDirectory.toPath().resolve("manifest.json")))
        assertTrue(Files.exists(missionDirectory.toPath().resolve("integrity.json")))
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
