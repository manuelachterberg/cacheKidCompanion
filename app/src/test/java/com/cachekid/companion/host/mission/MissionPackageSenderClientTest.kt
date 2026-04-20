package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class MissionPackageSenderClientTest {

    private val writer = MissionPackageWriter()
    private val senderClient = MissionPackageSenderClient()

    @Test
    fun `sender client posts a mission package to the local receiver`() {
        val tempDir = createTempDirectory("cachekid-sender").toFile()
        val statusMessages = mutableListOf<String>()
        val receiver = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = { statusMessages.add(it) },
        )
        val port = requireNotNull(receiver.start())

        try {
            val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
            val result = senderClient.send(
                host = "127.0.0.1",
                port = port,
                missionPackage = missionPackage,
            )

            assertTrue(result.isSuccess)
            assertTrue(result.message.contains("Stored ${missionPackage.missionId}"))
            assertTrue(statusMessages.any { it.contains("Mission empfangen") })
        } finally {
            receiver.stop()
        }
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
