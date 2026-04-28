package com.cachekid.companion.host.mission

import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MissionPackageSenderClient(
    private val zipCodec: MissionPackageZipCodec = MissionPackageZipCodec(),
    private val endpointParser: MissionPackageReceiverEndpointParser = MissionPackageReceiverEndpointParser(),
) {

    fun send(
        host: String,
        port: Int,
        missionPackage: MissionPackage,
    ): MissionPackageSendResult {
        val endpointResult = endpointParser.parse(host, port)
        val endpoint = endpointResult.endpoint ?: return MissionPackageSendResult(
            isSuccess = false,
            statusCode = null,
            message = endpointResult.message,
            status = endpointResult.status,
        )

        val payload = zipCodec.encode(missionPackage)
        val connection = try {
            endpoint.toUrl().openConnection() as HttpURLConnection
        } catch (_: MalformedURLException) {
            return MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = "Empfangsadresse ist ungueltig.",
                status = MissionPackageSendStatus.INVALID_ADDRESS,
            )
        }
        connection.apply {
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
                status = if (statusCode in 200..299) {
                    MissionPackageSendStatus.SENT
                } else {
                    MissionPackageSendStatus.HTTP_ERROR
                },
            )
        }.getOrElse { error ->
            MissionPackageSendResult(
                isSuccess = false,
                statusCode = null,
                message = when (error) {
                    is ConnectException,
                    is NoRouteToHostException,
                    is UnknownHostException,
                    -> "Verbindung zum Empfaenger fehlgeschlagen. Laeuft der Empfangsmodus?"
                    is SocketTimeoutException -> "Empfaenger antwortet nicht rechtzeitig."
                    else -> error.message ?: "Transfer fehlgeschlagen."
                },
                status = when (error) {
                    is ConnectException,
                    is NoRouteToHostException,
                    is UnknownHostException,
                    -> MissionPackageSendStatus.CONNECTION_FAILED
                    is SocketTimeoutException -> MissionPackageSendStatus.TIMEOUT
                    else -> MissionPackageSendStatus.FAILED
                },
            )
        }.also {
            connection.disconnect()
        }
    }
}
