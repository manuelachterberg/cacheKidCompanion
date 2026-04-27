package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
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
            assertEquals(MissionPackageSendStatus.SENT, result.status)
            assertTrue(result.message.contains("Stored ${missionPackage.missionId}"))
            assertTrue(statusMessages.any { it.contains("Mission empfangen") })
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun `sender client accepts pasted receiver url`() {
        val tempDir = createTempDirectory("cachekid-sender-url").toFile()
        val receiver = MissionPackageReceiverServer(
            baseDirectory = tempDir,
            port = 0,
            onStatusChanged = {},
        )
        val port = requireNotNull(receiver.start())

        try {
            val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
            val result = senderClient.send(
                host = "http://127.0.0.1:$port/missions",
                port = port,
                missionPackage = missionPackage,
            )

            assertTrue(result.isSuccess)
            assertEquals(MissionPackageSendStatus.SENT, result.status)
        } finally {
            receiver.stop()
        }
    }

    @Test
    fun `sender client reports missing address`() {
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
        val result = senderClient.send(
            host = " ",
            port = 8765,
            missionPackage = missionPackage,
        )

        assertEquals(MissionPackageSendStatus.MISSING_ADDRESS, result.status)
        assertTrue(result.message.contains("fehlt"))
    }

    @Test
    fun `sender client reports invalid port`() {
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
        val result = senderClient.send(
            host = "127.0.0.1",
            port = 0,
            missionPackage = missionPackage,
        )

        assertEquals(MissionPackageSendStatus.INVALID_PORT, result.status)
        assertTrue(result.message.contains("Port"))
    }

    @Test
    fun `sender client reports connection failure`() {
        val unusedPort = findUnusedPort()
        val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
        val result = senderClient.send(
            host = "127.0.0.1",
            port = unusedPort,
            missionPackage = missionPackage,
        )

        assertEquals(MissionPackageSendStatus.CONNECTION_FAILED, result.status)
        assertTrue(result.message.contains("Verbindung"))
    }

    @Test
    fun `sender client reports http error response`() {
        val server = SingleResponseServer(statusCode = 400, body = "Invalid mission package.")
        val port = server.start()

        try {
            val missionPackage = requireNotNull(writer.write(validDraft()).missionPackage)
            val result = senderClient.send(
                host = "127.0.0.1",
                port = port,
                missionPackage = missionPackage,
            )

            assertEquals(MissionPackageSendStatus.HTTP_ERROR, result.status)
            assertEquals(400, result.statusCode)
            assertTrue(result.message.contains("Invalid mission package"))
        } finally {
            server.stop()
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

    private fun findUnusedPort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }

    private class SingleResponseServer(
        private val statusCode: Int,
        private val body: String,
    ) {
        private val socket = ServerSocket(0)
        private val thread = Thread {
            socket.accept().use { client ->
                consumeRequest(client)
                val statusText = if (statusCode == 200) "OK" else "Bad Request"
                val payload = body.toByteArray(Charsets.UTF_8)
                client.getOutputStream().write(
                    (
                        "HTTP/1.1 $statusCode $statusText\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Content-Length: ${payload.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8),
                )
                client.getOutputStream().write(payload)
                client.getOutputStream().flush()
            }
        }

        fun start(): Int {
            thread.start()
            return socket.localPort
        }

        fun stop() {
            socket.close()
            thread.join(500)
        }

        private fun consumeRequest(client: Socket) {
            val input = client.getInputStream()
            var previous = -1
            var current: Int
            var headerEndMatches = 0
            val headers = StringBuilder()
            while (input.read().also { current = it } != -1) {
                headers.append(current.toChar())
                headerEndMatches = when {
                    previous == '\r'.code && current == '\n'.code && headerEndMatches == 0 -> 1
                    previous == '\n'.code && current == '\r'.code && headerEndMatches == 1 -> 2
                    previous == '\r'.code && current == '\n'.code && headerEndMatches == 2 -> 3
                    else -> headerEndMatches
                }
                if (headerEndMatches == 3) {
                    break
                }
                previous = current
            }
            val contentLength = headers.lineSequence()
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?: 0
            repeat(contentLength) {
                if (input.read() == -1) {
                    return
                }
            }
        }
    }
}
