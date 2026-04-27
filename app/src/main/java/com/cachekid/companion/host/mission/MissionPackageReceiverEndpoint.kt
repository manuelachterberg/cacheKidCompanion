package com.cachekid.companion.host.mission

import java.net.URI
import java.net.URISyntaxException
import java.net.URL

data class MissionPackageReceiverEndpoint(
    val host: String,
    val port: Int,
) {
    fun toUrl(): URL = URI("http", null, host, port, "/missions", null, null).toURL()
}

class MissionPackageReceiverEndpointParser {

    fun parse(hostInput: String, port: Int): MissionPackageReceiverEndpointParseResult {
        val input = hostInput.trim()
        if (input.isBlank()) {
            return MissionPackageReceiverEndpointParseResult(
                endpoint = null,
                status = MissionPackageSendStatus.MISSING_ADDRESS,
                message = "Empfangsadresse fehlt.",
            )
        }
        if (port !in 1..65535) {
            return MissionPackageReceiverEndpointParseResult(
                endpoint = null,
                status = MissionPackageSendStatus.INVALID_PORT,
                message = "Port ist ungueltig.",
            )
        }

        val host = runCatching { extractHost(input) }.getOrNull()
        if (host.isNullOrBlank()) {
            return MissionPackageReceiverEndpointParseResult(
                endpoint = null,
                status = MissionPackageSendStatus.INVALID_ADDRESS,
                message = "Empfangsadresse ist ungueltig.",
            )
        }

        return MissionPackageReceiverEndpointParseResult(
            endpoint = MissionPackageReceiverEndpoint(host = host, port = port),
            status = MissionPackageSendStatus.SENT,
            message = "Empfangsadresse erkannt.",
        )
    }

    private fun extractHost(input: String): String? {
        val hasScheme = input.contains("://")
        val uriInput = if (hasScheme) input else "http://$input"
        val uri = try {
            URI(uriInput)
        } catch (_: URISyntaxException) {
            return null
        }

        val host = uri.host ?: return if (hasScheme) null else parseHostWithoutScheme(input)
        return host.takeIf { it.isNotBlank() }
    }

    private fun parseHostWithoutScheme(input: String): String? {
        val trimmed = input.trim()
            .removePrefix("[")
            .substringBefore("/")
            .substringBefore(":")
            .removeSuffix("]")
            .trim()
        return trimmed.takeIf { host ->
            host.isNotBlank() && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }
    }
}

data class MissionPackageReceiverEndpointParseResult(
    val endpoint: MissionPackageReceiverEndpoint?,
    val status: MissionPackageSendStatus,
    val message: String,
)
