package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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
        val port = requireNotNull(server.start())

        try {
            val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
            val zipBytes = zipCodec.encode(missionPackage)
            val response = sendHttpPost(port, zipBytes)
            val body = response.substringAfter("\r\n\r\n")
            val receiveResponse = requireNotNull(MissionPackageReceiveResponse.parse(body))

            assertTrue(response.contains("200 OK"))
            assertEquals(MissionPackageReceiveStatus.IMPORTED, receiveResponse.status)
            assertEquals(missionPackage.missionId, receiveResponse.missionId)
            assertTrue(receiveResponse.message.contains("Mission empfangen"))
            assertTrue(statusMessages.any { it.contains("Mission empfangen") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `receiver server rejects unsupported endpoints with machine-readable response`() {
        val tempDir = createTempDirectory("cachekid-receiver-endpoint").toFile()
        val server = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = {},
        )
        val port = requireNotNull(server.start())

        try {
            val response = sendHttpRequest(
                port = port,
                method = "GET",
                path = "/status",
                body = ByteArray(0),
                includeContentLength = true,
            )
            val receiveResponse = requireNotNull(MissionPackageReceiveResponse.parse(response.substringAfter("\r\n\r\n")))

            assertTrue(response.contains("404 Not Found"))
            assertEquals(MissionPackageReceiveStatus.UNSUPPORTED_ENDPOINT, receiveResponse.status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `receiver server rejects missing content length`() {
        val tempDir = createTempDirectory("cachekid-receiver-length").toFile()
        val server = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = {},
        )
        val port = requireNotNull(server.start())

        try {
            val response = sendHttpRequest(
                port = port,
                method = "POST",
                path = "/missions",
                body = ByteArray(0),
                includeContentLength = false,
            )
            val receiveResponse = requireNotNull(MissionPackageReceiveResponse.parse(response.substringAfter("\r\n\r\n")))

            assertTrue(response.contains("411 Length Required"))
            assertEquals(MissionPackageReceiveStatus.MISSING_LENGTH, receiveResponse.status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `receiver server rejects unsupported content type`() {
        val tempDir = createTempDirectory("cachekid-receiver-content-type").toFile()
        val server = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = {},
        )
        val port = requireNotNull(server.start())

        try {
            val response = sendHttpRequest(
                port = port,
                method = "POST",
                path = "/missions",
                body = byteArrayOf(1, 2, 3),
                contentType = "text/plain",
            )
            val receiveResponse = requireNotNull(MissionPackageReceiveResponse.parse(response.substringAfter("\r\n\r\n")))

            assertTrue(response.contains("415 Unsupported Media Type"))
            assertEquals(MissionPackageReceiveStatus.UNSUPPORTED_MEDIA_TYPE, receiveResponse.status)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `receiver server reports invalid zip packages`() {
        val statusMessages = mutableListOf<String>()
        val tempDir = createTempDirectory("cachekid-receiver-invalid-zip").toFile()
        val server = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = { statusMessages.add(it) },
        )
        val port = requireNotNull(server.start())

        try {
            val response = sendHttpPost(port, "not a zip".toByteArray(Charsets.UTF_8))
            val receiveResponse = requireNotNull(MissionPackageReceiveResponse.parse(response.substringAfter("\r\n\r\n")))

            assertTrue(response.contains("422 Unprocessable Content"))
            assertEquals(MissionPackageReceiveStatus.INVALID_ZIP, receiveResponse.status)
            assertTrue(receiveResponse.errors.any { it.contains("ZIP") })
            assertTrue(statusMessages.any { it.contains("ZIP") })
        } finally {
            server.stop()
        }
    }

    @Test
    fun `receiver server reports store failures separately from invalid packages`() {
        val statusMessages = mutableListOf<String>()
        val basePath = kotlin.io.path.createTempFile("cachekid-receiver-store-failure").toFile()
        val server = MissionPackageReceiverServer(
            baseDirectory = basePath,
            port = 0,
            onStatusChanged = { statusMessages.add(it) },
        )
        val port = requireNotNull(server.start())

        try {
            val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
            val response = sendHttpPost(port, zipCodec.encode(missionPackage))
            val receiveResponse = requireNotNull(MissionPackageReceiveResponse.parse(response.substringAfter("\r\n\r\n")))

            assertTrue(response.contains("500 Internal Server Error"))
            assertEquals(MissionPackageReceiveStatus.STORE_FAILED, receiveResponse.status)
            assertEquals(missionPackage.missionId, receiveResponse.missionId)
            assertTrue(statusMessages.any { it.contains("storage") })
        } finally {
            server.stop()
            basePath.delete()
        }
    }

    @Test
    fun `receiver server reports port conflicts instead of crashing`() {
        val firstStatusMessages = mutableListOf<String>()
        val secondStatusMessages = mutableListOf<String>()
        val tempDir = createTempDirectory("cachekid-receiver-conflict").toFile()
        val firstServer = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = { firstStatusMessages.add(it) },
        )
        val firstPort = requireNotNull(firstServer.start())

        val secondServer = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = firstPort,
            onStatusChanged = { secondStatusMessages.add(it) },
        )

        try {
            val secondPort = secondServer.start()

            assertNull(secondPort)
            assertTrue(secondStatusMessages.any { it.contains("bereits belegt") })
        } finally {
            firstServer.stop()
            secondServer.stop()
        }
    }

    private fun sendHttpPost(port: Int, body: ByteArray): String {
        return sendHttpRequest(
            port = port,
            method = "POST",
            path = "/missions",
            body = body,
        )
    }

    private fun sendHttpRequest(
        port: Int,
        method: String,
        path: String,
        body: ByteArray,
        contentType: String = "application/zip",
        includeContentLength: Boolean = true,
    ): String {
        Socket("127.0.0.1", port).use { socket ->
            val output = socket.getOutputStream()
            val contentLengthHeader = if (includeContentLength) {
                "Content-Length: ${body.size}\r\n"
            } else {
                ""
            }
            output.write(
                (
                    "$method $path HTTP/1.1\r\n" +
                        "Host: 127.0.0.1\r\n" +
                        "Content-Type: $contentType\r\n" +
                        contentLengthHeader +
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
