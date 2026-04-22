package com.cachekid.companion.host.mission

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class OsmMissionMapSvgRenderer : MissionMapRenderer {

    override fun render(mapData: String, bounds: MissionMapBounds): String {
        val ways = WAY_BLOCK_REGEX.findAll(mapData).mapNotNull { match ->
            val block = match.value
            val geometryBlock = GEOMETRY_REGEX.find(block)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val cssClass = classifyWay(block)
            val geometry = geometryToPathGeometry(geometryBlock, bounds) ?: return@mapNotNull null
            if (geometry.length < minimumLengthFor(cssClass)) {
                return@mapNotNull null
            }
            RenderableWay(
                cssClass = cssClass,
                pathData = geometry.pathData,
                length = geometry.length,
                isClosed = geometry.isClosed,
            )
        }.toList()

        val paths = ways
            .groupBy { it.cssClass }
            .flatMap { (cssClass, groupedWays) ->
                groupedWays
                    .sortedByDescending { it.length }
                    .take(maxWaysFor(cssClass))
            }
            .sortedBy { wayOrder(it.cssClass) }
            .map { way ->
                val areaClass = fillClassFor(way.cssClass).takeIf { way.isClosed }
                if (areaClass != null) {
                    """<path class="$areaClass" d="${way.pathData} Z"></path><path class="${way.cssClass}" d="${way.pathData}"></path>"""
                } else {
                    """<path class="${way.cssClass}" d="${way.pathData}"></path>"""
                }
            }

        return buildString {
            append("""<g class="kid-map-osm-layer">""")
            paths.forEach { append(it) }
            append("</g>")
        }
    }

    private fun geometryToPathGeometry(geometryBlock: String, bounds: MissionMapBounds): PathGeometry? {
        val points = POINT_REGEX.findAll(geometryBlock).map { match ->
            val latitude = match.groupValues[1].toDoubleOrNull() ?: return@map null
            val longitude = match.groupValues[2].toDoubleOrNull() ?: return@map null
            SvgPoint(
                x = scaleLongitude(longitude, bounds),
                y = scaleLatitude(latitude, bounds),
            )
        }.filterNotNull().toList()

        val closed = points.size >= 3 && points.first().distanceTo(points.last()) <= CLOSED_LOOP_DISTANCE_PX
        val simplifiedPoints = simplifyPoints(points, closed)
        if (simplifiedPoints.size < 2) {
            return null
        }

        val pathData = simplifiedPoints
            .mapIndexed { index, point ->
                val prefix = if (index == 0) "M" else "L"
                "$prefix ${point.x.roundToInt()} ${point.y.roundToInt()}"
            }
            .joinToString(" ")

        val length = simplifiedPoints
            .zipWithNext()
            .sumOf { (start, end) -> hypot(end.x - start.x, end.y - start.y) }

        return PathGeometry(pathData = pathData, length = length)
            .copy(isClosed = closed)
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

    private fun simplifyPoints(points: List<SvgPoint>, closed: Boolean): List<SvgPoint> {
        if (points.size <= 2) {
            return points
        }

        val sourcePoints = if (closed && points.first().distanceTo(points.last()) <= CLOSED_LOOP_DISTANCE_PX) {
            points.dropLast(1)
        } else {
            points
        }

        val reducedPoints = douglasPeucker(sourcePoints, SIMPLIFICATION_EPSILON_PX)
        if (reducedPoints.size <= 2) {
            return if (closed && reducedPoints.isNotEmpty()) reducedPoints + reducedPoints.first() else reducedPoints
        }

        val simplified = mutableListOf(reducedPoints.first())
        var lastKept = reducedPoints.first()

        for (index in 1 until reducedPoints.lastIndex) {
            val candidate = reducedPoints[index]
            val distance = hypot(candidate.x - lastKept.x, candidate.y - lastKept.y)
            if (distance >= MIN_POINT_DISTANCE_PX) {
                simplified += candidate
                lastKept = candidate
            }
        }

        val finalPoint = reducedPoints.last()
        if (finalPoint != simplified.last()) {
            simplified += finalPoint
        }

        return if (closed && simplified.size >= 3) simplified + simplified.first() else simplified
    }

    private fun douglasPeucker(points: List<SvgPoint>, epsilon: Double): List<SvgPoint> {
        if (points.size < 3) {
            return points
        }

        var maxDistance = 0.0
        var maxIndex = 0
        val first = points.first()
        val last = points.last()

        for (index in 1 until points.lastIndex) {
            val distance = perpendicularDistance(points[index], first, last)
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = index
            }
        }

        return if (maxDistance > epsilon) {
            val firstHalf = douglasPeucker(points.subList(0, maxIndex + 1), epsilon)
            val secondHalf = douglasPeucker(points.subList(maxIndex, points.size), epsilon)
            firstHalf.dropLast(1) + secondHalf
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(point: SvgPoint, lineStart: SvgPoint, lineEnd: SvgPoint): Double {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        if (dx == 0.0 && dy == 0.0) {
            return point.distanceTo(lineStart)
        }

        val numerator = kotlin.math.abs(dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt(dx.pow(2) + dy.pow(2))
        return numerator / denominator
    }

    private fun minimumLengthFor(cssClass: String): Double {
        return when (cssClass) {
            "kid-map-osm-road-strong" -> 4.0
            "kid-map-osm-road" -> 6.0
            "kid-map-osm-water" -> 8.0
            "kid-map-osm-forest" -> 10.0
            else -> Double.MAX_VALUE
        }
    }

    private fun maxWaysFor(cssClass: String): Int {
        return when (cssClass) {
            "kid-map-osm-road-strong" -> 10
            "kid-map-osm-road" -> 18
            "kid-map-osm-water" -> 6
            "kid-map-osm-forest" -> 8
            else -> 0
        }
    }

    private fun wayOrder(cssClass: String): Int {
        return when (cssClass) {
            "kid-map-osm-forest" -> 0
            "kid-map-osm-water" -> 1
            "kid-map-osm-road" -> 2
            "kid-map-osm-road-strong" -> 3
            else -> 5
        }
    }

    private fun fillClassFor(cssClass: String): String? {
        return when (cssClass) {
            "kid-map-osm-water" -> "kid-map-osm-water-fill"
            "kid-map-osm-forest" -> "kid-map-osm-forest-fill"
            else -> null
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
        val POINT_REGEX = Regex("""lat"\s*:\s*(-?\d+(?:\.\d+)?)\s*,\s*"lon"\s*:\s*(-?\d+(?:\.\d+)?)""")
        const val MIN_POINT_DISTANCE_PX = 5.0
        const val SIMPLIFICATION_EPSILON_PX = 2.0
        const val CLOSED_LOOP_DISTANCE_PX = 4.0
    }
}

private data class SvgPoint(
    val x: Double,
    val y: Double,
) {
    fun distanceTo(other: SvgPoint): Double = hypot(x - other.x, y - other.y)
}

private data class PathGeometry(
    val pathData: String,
    val length: Double,
    val isClosed: Boolean = false,
)

private data class RenderableWay(
    val cssClass: String,
    val pathData: String,
    val length: Double,
    val isClosed: Boolean,
)
