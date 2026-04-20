package com.cachekid.companion.host.mission

import java.io.BufferedInputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class MissionPackageReceiverServer(
    private val baseDirectory: File,
    private val importer: MissionPackageZipImporter = MissionPackageZipImporter(),
    private val port: Int = DEFAULT_PORT,
    private val onStatusChanged: (String) -> Unit,
) {

    private val running = AtomicBoolean(false)
    private var executor: ExecutorService? = null
    private var serverSocket: ServerSocket? = null

    fun start(): Int {
        if (running.get()) {
            return serverSocket?.localPort ?: port
        }

        val socket = ServerSocket(port)
        executor = Executors.newSingleThreadExecutor()
        serverSocket = socket
        running.set(true)
        onStatusChanged("Empfang aktiv auf Port ${socket.localPort}.")
        executor?.execute {
            runLoop(socket)
        }
        return socket.localPort
    }

    fun stop() {
        running.set(false)
        serverSocket?.close()
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        onStatusChanged("Empfang gestoppt.")
    }

    private fun runLoop(socket: ServerSocket) {
        while (running.get()) {
            try {
                val client = socket.accept()
                client.use { handleClient(it) }
            } catch (_: SocketException) {
                if (!running.get()) {
                    return
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val headersText = readHeaders(input)
        val requestLine = headersText.lineSequence().firstOrNull().orEmpty()
        val requestParts = requestLine.split(" ")
        val method = requestParts.getOrNull(0)
        val path = requestParts.getOrNull(1)
        val contentLength = Regex("""Content-Length:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(headersText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

        if (method != "POST" || path != "/missions") {
            writeResponse(output, 404, "Unsupported endpoint.")
            return
        }

        val body = ByteArray(contentLength)
        var bytesRead = 0
        while (bytesRead < contentLength) {
            val read = input.read(body, bytesRead, contentLength - bytesRead)
            if (read <= 0) break
            bytesRead += read
        }

        val result = importer.import(baseDirectory, body)
        if (result.isSuccess) {
            onStatusChanged("Mission empfangen: ${result.missionId}")
            writeResponse(output, 200, "Stored ${result.missionId}")
        } else {
            onStatusChanged(result.errors.firstOrNull() ?: "Mission-Empfang fehlgeschlagen.")
            writeResponse(output, 400, result.errors.joinToString(" "))
        }
    }

    private fun readHeaders(input: BufferedInputStream): String {
        val headerBytes = mutableListOf<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) break
            headerBytes.add(next.toByte())
            if (headerBytes.size >= 4) {
                val end = headerBytes.takeLast(4)
                if (end[0] == '\r'.code.toByte() &&
                    end[1] == '\n'.code.toByte() &&
                    end[2] == '\r'.code.toByte() &&
                    end[3] == '\n'.code.toByte()
                ) {
                    break
                }
            }
        }
        return headerBytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun writeResponse(output: java.io.OutputStream, statusCode: Int, body: String) {
        val statusText = if (statusCode == 200) "OK" else "Bad Request"
        val payload = body.toByteArray(Charsets.UTF_8)
        output.write(
            (
                "HTTP/1.1 $statusCode $statusText\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Content-Length: ${payload.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.UTF_8),
        )
        output.write(payload)
        output.flush()
    }

    private companion object {
        const val DEFAULT_PORT = 8765
    }
}
