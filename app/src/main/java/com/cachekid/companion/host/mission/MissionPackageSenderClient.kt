package com.cachekid.companion.host.mission

import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class MissionPackageSenderClient(
    private val zipCodec: MissionPackageZipCodec = MissionPackageZipCodec(),
) {

    fun send(
        host: String,
        port: Int,
        missionPackage: MissionPackage,
    ): MissionPackageSendResult {
        val normalizedHost = host.trim()
        if (normalizedHost.isBlank()) {
            return MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = "Empfangsadresse fehlt.",
            )
        }
        if (port !in 1..65535) {
            return MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = "Port ist ungueltig.",
            )
        }

        val payload = zipCodec.encode(missionPackage)
        val connection = (URL("http://$normalizedHost:$port/missions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/zip")
            setRequestProperty("Content-Length", payload.size.toString())
        }

        return runCatching {
            connection.outputStream.use { output ->
                output.write(payload)
            }
            val statusCode = connection.responseCode
            val responseText = runCatching {
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            MissionPackageSendResult(
                isSuccess = statusCode in 200..299,
                statusCode = statusCode,
                message = responseText.ifBlank {
                    if (statusCode in 200..299) "Mission gesendet." else "Transfer fehlgeschlagen."
                },
            )
        }.getOrElse { error ->
            MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = when (error) {
                    is ConnectException -> "Verbindung zum Empfaenger fehlgeschlagen. Laeuft der Empfangsmodus?"
                    is SocketTimeoutException -> "Empfaenger antwortet nicht rechtzeitig."
                    else -> error.message ?: "Transfer fehlgeschlagen."
                },
            )
        }.also {
            connection.disconnect()
        }
    }
}
