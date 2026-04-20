package com.cachekid.companion.host.mission

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class OverpassMissionMapDataSource(
    private val queryBuilder: OverpassQueryBuilder = OverpassQueryBuilder(),
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
) : MissionMapDataSource {

    override fun fetch(bounds: MissionMapBounds): String? {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        return runCatching {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write("data=${queryBuilder.build(bounds)}")
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
        const val DEFAULT_ENDPOINT_URL = "https://overpass-api.de/api/interpreter"
    }
}
