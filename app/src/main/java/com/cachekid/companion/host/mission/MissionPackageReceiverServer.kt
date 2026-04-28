package com.cachekid.companion.host.mission

import java.io.BufferedInputStream
import java.io.File
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MissionPackageReceiverServer(
    private val baseDirectory: File,
    private val importer: MissionPackageZipImporter = MissionPackageZipImporter(),
    private val port: Int = DEFAULT_PORT,
    private val onStatusChanged: (String) -> Unit,
    private val onMissionImported: (MissionPackageImportResult) -> Unit = {},
) {

    private val running = AtomicBoolean(false)
    private var executor: ExecutorService? = null
    private var serverSocket: ServerSocket? = null

    fun start(): Int? {
        if (running.get()) {
            return serverSocket?.localPort ?: port
        }

        val socket = try {
            ServerSocket(port)
        } catch (error: BindException) {
            onStatusChanged("Empfang konnte nicht gestartet werden. Port $port ist bereits belegt.")
            return null
        }
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
        val contentLengthHeader = Regex("""Content-Length:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
            .find(headersText)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val contentLength = contentLengthHeader?.toIntOrNull()
        val contentType = Regex("""Content-Type:\s*([^\r\n;]+)""", RegexOption.IGNORE_CASE)
            .find(headersText)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()

        if (method != "POST" || path != "/missions") {
            writeJsonResponse(
                output = output,
                statusCode = 404,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.UNSUPPORTED_ENDPOINT,
                    missionId = null,
                    message = "Unsupported endpoint.",
                    errors = listOf("Expected POST /missions."),
                ),
            )
            return
        }
        if (contentLengthHeader == null) {
            writeJsonResponse(
                output = output,
                statusCode = 411,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.MISSING_LENGTH,
                    missionId = null,
                    message = "Content-Length header is required.",
                    errors = listOf("Mission package length is missing."),
                ),
            )
            return
        }
        if (contentLength == null || contentLength < 0) {
            writeJsonResponse(
                output = output,
                statusCode = 400,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.INVALID_LENGTH,
                    missionId = null,
                    message = "Content-Length header is invalid.",
                    errors = listOf("Mission package length is invalid."),
                ),
            )
            return
        }
        if (contentType != "application/zip") {
            drainRequestBody(input, contentLength)
            writeJsonResponse(
                output = output,
                statusCode = 415,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.UNSUPPORTED_MEDIA_TYPE,
                    missionId = null,
                    message = "Content-Type must be application/zip.",
                    errors = listOf("Mission package upload must use application/zip."),
                ),
            )
            return
        }
        if (contentLength == 0) {
            writeJsonResponse(
                output = output,
                statusCode = 400,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.EMPTY_BODY,
                    missionId = null,
                    message = "Mission package body is empty.",
                    errors = listOf("Mission package body is empty."),
                ),
            )
            return
        }

        val body = ByteArray(contentLength)
        val bytesRead = readRequestBody(input, body, contentLength)
        if (bytesRead != contentLength) {
            writeJsonResponse(
                output = output,
                statusCode = 400,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.INCOMPLETE_BODY,
                    missionId = null,
                    message = "Mission package body ended before Content-Length bytes were received.",
                    errors = listOf("Mission package upload was incomplete."),
                ),
            )
            return
        }

        val result = importer.import(baseDirectory, body)
        if (result.isSuccess) {
            onStatusChanged("Mission empfangen: ${result.missionId}")
            onMissionImported(result)
            writeJsonResponse(
                output = output,
                statusCode = 200,
                response = MissionPackageReceiveResponse(
                    status = MissionPackageReceiveStatus.IMPORTED,
                    missionId = result.missionId,
                    message = "Mission empfangen: ${result.missionId}",
                ),
            )
        } else {
            val response = result.toReceiveResponse()
            onStatusChanged(response.message)
            writeJsonResponse(
                output = output,
                statusCode = result.toHttpStatusCode(),
                response = response,
            )
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

    private fun drainRequestBody(input: BufferedInputStream, contentLength: Int) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead = 0
        while (bytesRead < contentLength) {
            val read = input.read(buffer, 0, minOf(buffer.size, contentLength - bytesRead))
            if (read <= 0) break
            bytesRead += read
        }
    }

    private fun readRequestBody(
        input: BufferedInputStream,
        body: ByteArray,
        contentLength: Int,
    ): Int {
        var bytesRead = 0
        while (bytesRead < contentLength) {
            val read = input.read(body, bytesRead, contentLength - bytesRead)
            if (read <= 0) break
            bytesRead += read
        }
        return bytesRead
    }

    private fun writeJsonResponse(
        output: java.io.OutputStream,
        statusCode: Int,
        response: MissionPackageReceiveResponse,
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            411 -> "Length Required"
            415 -> "Unsupported Media Type"
            422 -> "Unprocessable Content"
            500 -> "Internal Server Error"
            else -> "Error"
        }
        val body = response.toJson()
        val payload = body.toByteArray(Charsets.UTF_8)
        output.write(
            (
                "HTTP/1.1 $statusCode $statusText\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${payload.size}\r\n" +
                    "Connection: close\r\n\r\n"
                ).toByteArray(Charsets.UTF_8),
        )
        output.write(payload)
        output.flush()
    }

    private fun MissionPackageImportResult.toHttpStatusCode(): Int {
        return when (status) {
            MissionPackageImportStatus.ZIP_DECODE_FAILED,
            MissionPackageImportStatus.MANIFEST_INVALID,
            MissionPackageImportStatus.VALIDATION_FAILED,
            -> 422
            MissionPackageImportStatus.STORE_FAILED -> 500
            MissionPackageImportStatus.FAILED -> 400
            MissionPackageImportStatus.IMPORTED -> 200
        }
    }

    private fun MissionPackageImportResult.toReceiveResponse(): MissionPackageReceiveResponse {
        val receiveStatus = when (status) {
            MissionPackageImportStatus.ZIP_DECODE_FAILED -> MissionPackageReceiveStatus.INVALID_ZIP
            MissionPackageImportStatus.MANIFEST_INVALID -> MissionPackageReceiveStatus.INVALID_MANIFEST
            MissionPackageImportStatus.VALIDATION_FAILED -> MissionPackageReceiveStatus.VALIDATION_FAILED
            MissionPackageImportStatus.STORE_FAILED -> MissionPackageReceiveStatus.STORE_FAILED
            MissionPackageImportStatus.FAILED -> MissionPackageReceiveStatus.FAILED
            MissionPackageImportStatus.IMPORTED -> MissionPackageReceiveStatus.IMPORTED
        }
        val fallbackMessage = when (receiveStatus) {
            MissionPackageReceiveStatus.INVALID_ZIP -> "Mission package ZIP could not be decoded."
            MissionPackageReceiveStatus.INVALID_MANIFEST -> "Mission package manifest is missing or invalid."
            MissionPackageReceiveStatus.VALIDATION_FAILED -> "Mission package validation failed."
            MissionPackageReceiveStatus.STORE_FAILED -> "Mission package could not be stored."
            MissionPackageReceiveStatus.FAILED -> "Mission-Empfang fehlgeschlagen."
            else -> "Mission-Empfang fehlgeschlagen."
        }
        return MissionPackageReceiveResponse(
            status = receiveStatus,
            missionId = missionId,
            message = errors.firstOrNull() ?: fallbackMessage,
            errors = errors,
        )
    }

    private companion object {
        const val DEFAULT_PORT = 8765
    }
}
