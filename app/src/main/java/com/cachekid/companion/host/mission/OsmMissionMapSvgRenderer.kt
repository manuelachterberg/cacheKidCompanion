package com.cachekid.companion.host.mission

import kotlin.math.roundToInt

class OsmMissionMapSvgRenderer : MissionMapRenderer {

    override fun render(mapData: String, bounds: MissionMapBounds): String {
        val paths = WAY_BLOCK_REGEX.findAll(mapData).mapNotNull { match ->
            val block = match.value
            val geometryBlock = GEOMETRY_REGEX.find(block)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val pathData = geometryToPath(geometryBlock, bounds) ?: return@mapNotNull null
            val cssClass = classifyWay(block)
            """<path class="$cssClass" d="$pathData"></path>"""
        }.toList()

        return buildString {
            append("""<g class="kid-map-osm-layer">""")
            paths.forEach { append(it) }
            append("</g>")
        }
    }

    private fun geometryToPath(geometryBlock: String, bounds: MissionMapBounds): String? {
        val points = POINT_REGEX.findAll(geometryBlock).map { match ->
            val latitude = match.groupValues[1].toDoubleOrNull() ?: return@map null
            val longitude = match.groupValues[2].toDoubleOrNull() ?: return@map null
            val x = scaleLongitude(longitude, bounds)
            val y = scaleLatitude(latitude, bounds)
            "${x.roundToInt()} ${y.roundToInt()}"
        }.filterNotNull().toList()

        return points.takeIf { it.size >= 2 }
            ?.mapIndexed { index, point -> "${if (index == 0) "M" else "L"} $point" }
            ?.joinToString(" ")
    }

    private fun classifyWay(block: String): String {
        val waterway = extractTagValue(block, "waterway")
        val natural = extractTagValue(block, "natural")
        val landuse = extractTagValue(block, "landuse")
        val highway = extractTagValue(block, "highway")

        return when {
            waterway.isNotBlank() || natural == "water" -> "kid-map-osm-water"
            landuse == "forest" -> "kid-map-osm-forest"
            highway in setOf("motorway", "trunk", "primary", "secondary", "tertiary") -> "kid-map-osm-road-strong"
            highway.isNotBlank() -> "kid-map-osm-road"
            else -> "kid-map-osm-terrain"
        }
    }

    private fun extractTagValue(block: String, key: String): String {
        val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return regex.find(block)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun scaleLongitude(longitude: Double, bounds: MissionMapBounds): Double {
        val span = (bounds.maxLongitude - bounds.minLongitude).takeIf { it != 0.0 } ?: 1.0
        return ((longitude - bounds.minLongitude) / span) * 100.0
    }

    private fun scaleLatitude(latitude: Double, bounds: MissionMapBounds): Double {
        val span = (bounds.maxLatitude - bounds.minLatitude).takeIf { it != 0.0 } ?: 1.0
        return ((bounds.maxLatitude - latitude) / span) * 140.0
    }

    private companion object {
        val WAY_BLOCK_REGEX = Regex(""""type"\s*:\s*"way".*?"geometry"\s*:\s*\[(.*?)\]""", setOf(RegexOption.DOT_MATCHES_ALL))
        val GEOMETRY_REGEX = Regex(""""geometry"\s*:\s*\[(.*)]""", setOf(RegexOption.DOT_MATCHES_ALL))
        val POINT_REGEX = Regex("""\{\s*"lat"\s*:\s*(-?\d+(?:\.\d+)?)\s*,\s*"lon"\s*:\s*(-?\d+(?:\.\d+)?)\s*}""")
    }
}
