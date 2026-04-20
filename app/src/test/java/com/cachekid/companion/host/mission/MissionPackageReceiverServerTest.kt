package com.cachekid.companion.host.mission

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.Socket
import kotlin.io.path.createTempDirectory

class MissionPackageReceiverServerTest {

    private val writer = MissionPackageWriter()
    private val zipCodec = MissionPackageZipCodec()

    @Test
    fun `receiver server accepts mission package upload over local http`() {
        val statusMessages = mutableListOf<String>()
        val tempDir = createTempDirectory("cachekid-receiver").toFile()
        val server = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = { statusMessages.add(it) },
        )
        val port = server.start()

        try {
            val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
            val zipBytes = zipCodec.encode(missionPackage)
            val response = sendHttpPost(port, zipBytes)

            assertTrue(response.contains("200 OK"))
            assertTrue(response.contains("Stored ${missionPackage.missionId}"))
            assertTrue(statusMessages.any { it.contains("Mission empfangen") })
        } finally {
            server.stop()
        }
    }

    private fun sendHttpPost(port: Int, body: ByteArray): String {
        Socket("127.0.0.1", port).use { socket ->
            val output = socket.getOutputStream()
            output.write(
                (
                    "POST /missions HTTP/1.1\r\n" +
                        "Host: 127.0.0.1\r\n" +
                        "Content-Type: application/zip\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8),
            )
            output.write(body)
            output.flush()

            val response = ByteArrayOutputStream()
            socket.getInputStream().copyTo(response)
            return response.toString(Charsets.UTF_8.name())
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
