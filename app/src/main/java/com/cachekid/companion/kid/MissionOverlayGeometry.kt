package com.cachekid.companion.kid

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Pure functions for building GeoJSON overlay geometry.
 *
 * These functions have no Android or MapLibre map-runtime dependencies
 * beyond the geojson data classes, making them unit-testable on the JVM.
 */
object MissionOverlayGeometry {

    fun buildTargetFeatures(targetPoint: Point): List<Feature> {
        return listOf(Feature.fromGeometry(targetPoint))
    }

    fun buildPlayerFeatures(playerPoint: Point): List<Feature> {
        return listOf(Feature.fromGeometry(playerPoint))
    }

    fun buildWaypointFeatures(waypoints: List<Point>): List<Feature> {
        return waypoints.map { Feature.fromGeometry(it) }
    }

    fun buildRouteFeatures(
        routeStartPoint: Point,
        waypoints: List<Point>,
        targetPoint: Point,
    ): List<Feature> {
        val routePoints = buildList {
            add(routeStartPoint)
            addAll(waypoints)
            add(targetPoint)
        }
        if (routePoints.size < 2) {
            return emptyList()
        }
        val primaryPoints = routePoints
        val sketchA = Feature.fromGeometry(LineString.fromLngLats(primaryPoints))
        val sketchB = Feature.fromGeometry(LineString.fromLngLats(primaryPoints))
        return listOf(sketchA, sketchB)
    }

    fun buildWaypointFeatureCollection(waypoints: List<Point>): FeatureCollection {
        return FeatureCollection.fromFeatures(buildWaypointFeatures(waypoints))
    }

    fun buildTargetFeatureCollection(targetPoint: Point): FeatureCollection {
        return FeatureCollection.fromFeatures(buildTargetFeatures(targetPoint))
    }

    fun buildPlayerFeatureCollection(playerPoint: Point): FeatureCollection {
        return FeatureCollection.fromFeatures(buildPlayerFeatures(playerPoint))
    }

    fun buildRouteFeatureCollection(
        routeStartPoint: Point,
        waypoints: List<Point>,
        targetPoint: Point,
    ): FeatureCollection {
        return FeatureCollection.fromFeatures(
            buildRouteFeatures(routeStartPoint, waypoints, targetPoint),
        )
    }
}
