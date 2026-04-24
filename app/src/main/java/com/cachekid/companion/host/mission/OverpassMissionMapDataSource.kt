package com.cachekid.companion.host.mission

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OverpassMissionMapDataSource(
    private val queryBuilder: OverpassQueryBuilder = OverpassQueryBuilder(),
    private val endpointUrls: List<String> = DEFAULT_ENDPOINT_URLS,
) : MissionMapDataSource {

    override fun fetch(bounds: MissionMapBounds): String? {
        val requestBody = "data=${queryBuilder.build(bounds)}"
        return endpointUrls.firstNotNullOfOrNull { endpointUrl ->
            fetchFromEndpoint(endpointUrl, requestBody)
        }
    }

    private fun fetchFromEndpoint(endpointUrl: String, requestBody: String): String? {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "CacheKidCompanion/1.0")
        }

        return runCatching {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            } ?: return null

            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .takeIf { responseCode in 200..299 }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

    private companion object {
        val DEFAULT_ENDPOINT_URLS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://lz4.overpass-api.de/api/interpreter",
        )
    }
}
