package com.cachekid.companion.kid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Path
import android.location.Location
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.cachekid.companion.host.mission.ActiveMission
import com.cachekid.companion.host.mission.OfflineBaseMapPackage
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
import org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point
import java.io.File

class KidNativeMapController(
    context: Context,
    private val mapContainer: FrameLayout,
) {
    private companion object {
        const val OFFLINE_BASEMAP_SOURCE_ID = "cachekid-offline-basemap-source"
        const val OFFLINE_BASEMAP_LAYER_ID = "cachekid-offline-basemap-layer"
        const val TARGET_SOURCE_ID = "cachekid-target-source"
        const val PLAYER_SOURCE_ID = "cachekid-player-source"
        const val ROUTE_SOURCE_ID = "cachekid-route-source"
        const val TARGET_LAYER_ID = "cachekid-target-layer"
        const val PLAYER_LAYER_ID = "cachekid-player-layer"
        const val ROUTE_UNDERLAY_LAYER_ID = "cachekid-route-underlay-layer"
        const val ROUTE_LAYER_ID = "cachekid-route-layer"
        const val ROUTE_ACCENT_LAYER_ID = "cachekid-route-accent-layer"
        const val WAYPOINT_SOURCE_ID = "cachekid-waypoint-source"
        const val WAYPOINT_LAYER_ID = "cachekid-waypoint-layer"
        const val TARGET_ICON_ID = "cachekid-target-icon"
        const val PLAYER_ICON_ID = "cachekid-player-icon"
        const val WAYPOINT_ICON_ID = "cachekid-waypoint-icon"
        const val CAMERA_LOG_TAG = "CacheKidCamera"
    }

    data class CameraDebugInfo(
        val latitude: Double,
        val longitude: Double,
        val usedFallback: Boolean,
        val missionTargetLatitude: Double,
        val missionTargetLongitude: Double,
    )

    private val germanyFallback = LatLng(52.625, 10.08)

    private val mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var mapStyleLoaded = false
    private var currentMission: ActiveMission? = null
    private var currentLocation: Location? = null
    private var currentHeadingDegrees: Float? = null
    private var previousCourseLocation: Location? = null
    private var currentCourseBearingDegrees: Double? = null
    private var lastAppliedBearingDegrees: Double? = null
    private var lastOrientationCommitAtMillis: Long = 0L
    private var displayedMissionId: String? = null
    private var displayedRouteStart: LatLng? = null
    private var displayedStyleKey: String? = null
    private var lastCameraDebugInfo: CameraDebugInfo? = null
    private var viewportTopInsetPx: Float? = null
    private var viewportBottomInsetPx: Float? = null
    private var lastZoneFitUsedStrict = true

    init {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions()
            .textureMode(true)
            .translucentTextureSurface(false)
            .compassEnabled(false)
            .logoEnabled(false)
            .attributionEnabled(false)
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
        mapView = MapView(context, options)
        mapView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        mapContainer.addView(mapView)
    }

    fun onCreate(savedInstanceState: android.os.Bundle?) {
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            applyStyleForMission(map, currentMission)
        }
    }

    fun onStart() = mapView.onStart()

    fun onResume() = mapView.onResume()

    fun onPause() = mapView.onPause()

    fun onStop() = mapView.onStop()

    fun onLowMemory() = mapView.onLowMemory()

    fun onDestroy() = mapView.onDestroy()

    fun onSaveInstanceState(outState: android.os.Bundle) {
        mapView.onSaveInstanceState(outState)
    }

    fun showMission(mission: ActiveMission?, location: Location?) {
        Log.d(
            CAMERA_LOG_TAG,
            "showMission mission=${mission?.missionId ?: "--"} location=${location?.latitude},${location?.longitude} routeOrigin=${mission?.routeOrigin?.latitude},${mission?.routeOrigin?.longitude} waypointCount=${mission?.waypoints?.size ?: 0} firstWaypoint=${mission?.waypoints?.firstOrNull()?.latitude},${mission?.waypoints?.firstOrNull()?.longitude} target=${mission?.target?.latitude},${mission?.target?.longitude}",
        )
        val missionChanged = currentMission?.missionId != mission?.missionId
        currentMission = mission
        currentLocation = location
        if (mission == null) {
            lastAppliedBearingDegrees = null
            lastOrientationCommitAtMillis = 0L
            displayedMissionId = null
            displayedRouteStart = null
            displayedStyleKey = null
            previousCourseLocation = null
            currentCourseBearingDegrees = null
        } else if (missionChanged || displayedMissionId != mission.missionId || displayedRouteStart == null) {
            lastAppliedBearingDegrees = null
            lastOrientationCommitAtMillis = 0L
            displayedMissionId = mission.missionId
            displayedRouteStart = resolveDisplayRouteStart(mission)
            previousCourseLocation = null
            currentCourseBearingDegrees = null
            lastZoneFitUsedStrict = true
        }
        mapContainer.visibility = if (mission != null) View.VISIBLE else View.GONE
        if (missionChanged || mission == null) {
            applyStyleForMission(mapLibreMap ?: return, mission)
        } else {
            updateMissionOverlays()
        }
        if (missionChanged || mission == null) {
            updateCamera(animate = false)
        }
    }

    fun updateLocation(location: Location?) {
        updateCourseBearing(location)
        currentLocation = location
        Log.d(
            CAMERA_LOG_TAG,
            "updateLocation raw=${location?.latitude},${location?.longitude} accuracy=${location?.accuracy} bearing=${location?.bearing}",
        )
        updateMissionOverlays()
        applyOrientationBearing()
        val map = mapLibreMap ?: return
        val mission = currentMission ?: return
        refitZoomForCurrentBearing(map, mission)
    }

    fun updateHeading(headingDegrees: Float?) {
        currentHeadingDegrees = headingDegrees
        applyOrientationBearing()
    }

    fun getLastCameraDebugInfo(): CameraDebugInfo? = lastCameraDebugInfo

    fun getCurrentMapBearingDegrees(): Double? =
        mapLibreMap?.cameraPosition?.bearing?.let { normalizeDegrees(it) } ?: lastAppliedBearingDegrees

    fun getCurrentTargetBearingDegrees(): Double? = currentMission?.let { routeBearingForMission(it) }

    fun updateViewportInsets(topInsetPx: Float, bottomInsetPx: Float) {
        viewportTopInsetPx = topInsetPx
        viewportBottomInsetPx = bottomInsetPx
    }

    private fun updateCamera(animate: Boolean = false) {
        val map = mapLibreMap ?: return
        if (!mapStyleLoaded) {
            Log.d(CAMERA_LOG_TAG, "updateCamera skipped mapStyleLoaded=false")
            return
        }

        val width = mapContainer.width
        val height = mapContainer.height
        var availableMapHeightPx = 0
        if (width > 0 && height > 0) {
            val topPadding = (viewportTopInsetPx ?: (height * 0.37f)).toInt()
            val sidePadding = (width * 0.08f).toInt()
            val bottomPadding = (viewportBottomInsetPx ?: (height * 0.14f)).toInt()
            map.setPadding(sidePadding, topPadding, sidePadding, bottomPadding)
            availableMapHeightPx = (height - topPadding - bottomPadding).coerceAtLeast(1)
        }

        val mission = currentMission ?: return
        val missionTarget = LatLng(mission.target.latitude, mission.target.longitude)
        val fallbackRouteStart = displayedRouteStart ?: resolveDisplayRouteStart(mission).also {
            displayedRouteStart = it
        }
        val activeRoute = resolveActiveRouteState(mission, fallbackRouteStart)
        val routeStart = activeRoute.routeStart
        val routePoints = buildList {
            add(routeStart)
            activeRoute.remainingWaypoints.forEach { waypoint ->
                add(LatLng(waypoint.latitude, waypoint.longitude))
            }
            add(missionTarget)
        }
        Log.d(
            CAMERA_LOG_TAG,
            "updateCamera mission=${mission.missionId} animate=$animate routeStart=${routeStart.latitude},${routeStart.longitude} target=${missionTarget.latitude},${missionTarget.longitude} routePoints=${routePoints.size}",
        )
        val useFallback = !looksLikeCentralEurope(missionTarget)
        val cameraTarget = if (useFallback) germanyFallback else missionTarget
        lastCameraDebugInfo = CameraDebugInfo(
            latitude = cameraTarget.latitude,
            longitude = cameraTarget.longitude,
            usedFallback = useFallback,
            missionTargetLatitude = missionTarget.latitude,
            missionTargetLongitude = missionTarget.longitude,
        )
        val routeBounds = buildRouteBounds(routePoints)
        if (routeBounds != null && width > 0 && height > 0) {
            val sidePadding = (width * 0.10f).toInt().coerceAtLeast(40)
            val topPadding = ((viewportTopInsetPx ?: (height * 0.37f)) + (height * 0.02f)).toInt()
            val bottomPadding = ((viewportBottomInsetPx ?: (height * 0.14f)) + (height * 0.04f)).toInt()
            Log.d(
                CAMERA_LOG_TAG,
                "updateCamera routeBounds points=${routePoints.size} top=$topPadding bottom=$bottomPadding side=$sidePadding",
            )
            val update = CameraUpdateFactory.newLatLngBounds(
                routeBounds,
                sidePadding,
                topPadding,
                sidePadding,
                bottomPadding,
            )
            map.moveCamera(update)
            maximizeZoomForVisibleEndpoints(
                map = map,
                routeStart = routeStart,
                missionTarget = missionTarget,
                width = width,
                height = height,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                sidePadding = sidePadding,
            )
            applyOrientationBearing()
            return
        }

        val fallbackCamera = CameraPosition.Builder()
            .target(cameraTarget)
            .zoom(16.2)
            .tilt(20.0)
            .build()
        Log.d(
            CAMERA_LOG_TAG,
            "updateCamera fallback bearing=${fallbackCamera.bearing} zoom=${fallbackCamera.zoom} target=${fallbackCamera.target?.latitude},${fallbackCamera.target?.longitude}",
        )
        val update = CameraUpdateFactory.newCameraPosition(fallbackCamera)
        map.moveCamera(update)
        applyOrientationBearing()
    }

    private fun updateMissionOverlays() {
        val map = mapLibreMap ?: return
        val mission = currentMission ?: return
        val style = map.style ?: return

        val targetSource = style.getSourceAs<GeoJsonSource>(TARGET_SOURCE_ID) ?: return
        val playerSource = style.getSourceAs<GeoJsonSource>(PLAYER_SOURCE_ID) ?: return
        val routeSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return
        val waypointSource = style.getSourceAs<GeoJsonSource>(WAYPOINT_SOURCE_ID) ?: return
        val targetPoint = Point.fromLngLat(mission.target.longitude, mission.target.latitude)
        targetSource.setGeoJson(
            MissionOverlayGeometry.buildTargetFeatureCollection(targetPoint),
        )

        val fallbackRouteStart = displayedRouteStart ?: resolveDisplayRouteStart(mission).also {
            displayedRouteStart = it
        }
        val activeRoute = resolveActiveRouteState(mission, fallbackRouteStart)
        val routeStartPoint = Point.fromLngLat(activeRoute.routeStart.longitude, activeRoute.routeStart.latitude)
        val playerPoint = Point.fromLngLat(activeRoute.playerPoint.longitude, activeRoute.playerPoint.latitude)
        playerSource.setGeoJson(
            MissionOverlayGeometry.buildPlayerFeatureCollection(playerPoint),
        )

        val routeWaypoints = activeRoute.remainingWaypoints.map { waypoint ->
            Point.fromLngLat(waypoint.longitude, waypoint.latitude)
        }
        val allWaypoints = mission.waypoints.map { waypoint ->
            Point.fromLngLat(waypoint.longitude, waypoint.latitude)
        }
        routeSource.setGeoJson(
            MissionOverlayGeometry.buildRouteFeatureCollection(
                routeStartPoint = routeStartPoint,
                waypoints = routeWaypoints,
                targetPoint = targetPoint,
            ),
        )
        waypointSource.setGeoJson(
            MissionOverlayGeometry.buildWaypointFeatureCollection(allWaypoints),
        )
        Log.d(
            CAMERA_LOG_TAG,
            "updateMissionOverlays mission=${mission.missionId} currentLocation=${currentLocation?.latitude},${currentLocation?.longitude} routeStart=${activeRoute.routeStart.latitude},${activeRoute.routeStart.longitude} player=${activeRoute.playerPoint.latitude},${activeRoute.playerPoint.longitude} waypointCount=${activeRoute.remainingWaypoints.size}/${mission.waypoints.size} target=${mission.target.latitude},${mission.target.longitude}",
        )
    }

    private fun ensureMissionOverlayLayers(style: Style) {
        if (style.getImage(TARGET_ICON_ID) == null) {
            style.addImage(TARGET_ICON_ID, buildTargetMarkerBitmap())
        }
        if (style.getImage(PLAYER_ICON_ID) == null) {
            style.addImage(PLAYER_ICON_ID, buildPlayerMarkerBitmap())
        }
        if (style.getImage(WAYPOINT_ICON_ID) == null) {
            style.addImage(WAYPOINT_ICON_ID, buildWaypointMarkerBitmap())
        }
        if (style.getSource(TARGET_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(TARGET_SOURCE_ID))
        }
        if (style.getSource(PLAYER_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(PLAYER_SOURCE_ID))
        }
        if (style.getSource(ROUTE_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
        }
        if (style.getSource(WAYPOINT_SOURCE_ID) == null) {
            style.addSource(GeoJsonSource(WAYPOINT_SOURCE_ID))
        }
        if (style.getLayer(ROUTE_UNDERLAY_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(ROUTE_UNDERLAY_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    lineColor("#ffffff"),
                    lineWidth(7.0f),
                    lineOpacity(0.92f),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(ROUTE_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    lineColor("#111111"),
                    lineWidth(3.8f),
                    lineDasharray(arrayOf(1.0f, 2.4f)),
                    lineOpacity(0.95f),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(ROUTE_ACCENT_LAYER_ID) == null) {
            style.addLayer(
                LineLayer(ROUTE_ACCENT_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    lineColor("#111111"),
                    lineWidth(1.6f),
                    lineOpacity(0.35f),
                    lineDasharray(arrayOf(0.6f, 3.2f)),
                    lineCap(LINE_CAP_ROUND),
                    lineJoin(LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getLayer(WAYPOINT_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(WAYPOINT_LAYER_ID, WAYPOINT_SOURCE_ID).withProperties(
                    iconImage(WAYPOINT_ICON_ID),
                    iconSize(1.0f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
            )
        }
        if (style.getLayer(TARGET_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(TARGET_LAYER_ID, TARGET_SOURCE_ID).withProperties(
                    iconImage(TARGET_ICON_ID),
                    iconSize(1.0f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
            )
        }
        if (style.getLayer(PLAYER_LAYER_ID) == null) {
            style.addLayer(
                SymbolLayer(PLAYER_LAYER_ID, PLAYER_SOURCE_ID).withProperties(
                    iconImage(PLAYER_ICON_ID),
                    iconSize(1.0f),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
            )
        }
    }

    private fun applyStyleForMission(map: MapLibreMap, mission: ActiveMission?) {
        val styleKey = buildStyleKey(mission)
        if (styleKey == displayedStyleKey && mapStyleLoaded) {
            updateMissionOverlays()
            return
        }

        mapStyleLoaded = false
        displayedStyleKey = styleKey
        map.setStyle(Style.Builder().fromJson(buildStyleJsonForMission(mission))) {
            ensureMissionOverlayLayers(it)
            mapStyleLoaded = true
            Log.d(CAMERA_LOG_TAG, "style-ready mission=${currentMission?.missionId ?: "--"} style=$styleKey")
            updateMissionOverlays()
            updateCamera()
        }
    }

    private fun buildStyleKey(mission: ActiveMission?): String {
        val offlinePackage = mission?.offlineBaseMapPackage
        return if (offlinePackage != null) {
            listOf(
                "offline",
                offlinePackage.id,
                offlinePackage.version,
                offlinePackage.tileAssetPath,
                offlinePackage.styleAssetPath,
            ).joinToString("|")
        } else {
            "missing-offline-map"
        }
    }

    private fun buildStyleJsonForMission(mission: ActiveMission?): String {
        val offlinePackage = mission?.offlineBaseMapPackage
        return if (offlinePackage != null) {
            buildOfflinePackageStyleJson(offlinePackage)
        } else {
            buildMissingOfflineMapStyleJson()
        }
    }



    private fun buildTargetMarkerBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(124, 124, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val loop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.STROKE
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val loopHalo = Paint(halo).apply { strokeWidth = 17f }
        val path = Path().apply {
            moveTo(29f, 24f)
            lineTo(95f, 98f)
            moveTo(29f, 98f)
            lineTo(95f, 24f)
            moveTo(40f, 20f)
            lineTo(92f, 39f)
            lineTo(73f, 93f)
            lineTo(28f, 74f)
            close()
        }
        canvas.drawPath(path, loopHalo)
        canvas.drawPath(path, halo)
        canvas.drawLine(29f, 24f, 95f, 98f, ink)
        canvas.drawLine(29f, 98f, 95f, 24f, ink)
        canvas.drawPath(
            Path().apply {
                moveTo(40f, 20f)
                lineTo(92f, 39f)
                lineTo(73f, 93f)
                lineTo(28f, 74f)
                close()
            },
            loop,
        )
        return bitmap
    }

    private fun buildPlayerMarkerBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 20f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(56f, 56f, 26f, halo)
        canvas.drawCircle(56f, 56f, 26f, ink)
        canvas.drawCircle(56f, 56f, 10f, dot)
        return bitmap
    }

    private fun buildWaypointMarkerBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            style = Paint.Style.FILL
        }
        // Draw a small filled circle with white halo for E-ink contrast
        val cx = 40f
        val cy = 40f
        val size = 18f
        canvas.drawCircle(cx, cy, size + 8f, halo)
        canvas.drawCircle(cx, cy, size, ink)
        return bitmap
    }

    private fun buildRouteLatLngs(
        mission: ActiveMission,
        routeStart: LatLng,
        missionTarget: LatLng,
    ): List<LatLng> {
        return buildList {
            add(routeStart)
            mission.waypoints.forEach { waypoint ->
                add(LatLng(waypoint.latitude, waypoint.longitude))
            }
            add(missionTarget)
        }
    }

    private fun buildRouteBounds(routePoints: List<LatLng>): LatLngBounds? {
        if (routePoints.size < 2 || routePoints.any { !looksLikeCentralEurope(it) }) {
            return null
        }
        val builder = LatLngBounds.Builder()
        routePoints.forEach { point -> builder.include(point) }
        return builder.build()
    }

    private fun routeBearingForMission(mission: ActiveMission): Double? {
        val routeStart = resolveLiveRouteStart(mission) ?: displayedRouteStart ?: return null
        val missionTarget = LatLng(mission.target.latitude, mission.target.longitude)
        if (!looksLikeCentralEurope(routeStart) || !looksLikeCentralEurope(missionTarget)) {
            return null
        }
        return bearingBetween(routeStart, missionTarget)
    }

    private fun resolveDisplayRouteStart(mission: ActiveMission): LatLng {
        mission.routeOrigin?.let {
            return LatLng(it.latitude, it.longitude)
        }

        val current = currentLocation
        if (current != null) {
            val currentLatLng = LatLng(current.latitude, current.longitude)
            val targetLatLng = LatLng(mission.target.latitude, mission.target.longitude)
            val isNearTarget = distanceMeters(currentLatLng, targetLatLng) <= 2500.0
            if (looksLikeCentralEurope(currentLatLng) && isNearTarget) {
                return currentLatLng
            }
        }

        return LatLng(
            mission.target.latitude - 0.00058,
            mission.target.longitude - 0.00016,
        )
    }

    private fun resolveLiveRouteStart(mission: ActiveMission): LatLng? {
        val current = currentLocation ?: return null
        val currentLatLng = LatLng(current.latitude, current.longitude)
        val targetLatLng = LatLng(mission.target.latitude, mission.target.longitude)
        val isNearTarget = distanceMeters(currentLatLng, targetLatLng) <= 5000.0
        Log.d(
            CAMERA_LOG_TAG,
            "resolveLiveRouteStart current=${currentLatLng.latitude},${currentLatLng.longitude} target=${targetLatLng.latitude},${targetLatLng.longitude} near=$isNearTarget central=${looksLikeCentralEurope(currentLatLng)}",
        )
        return currentLatLng.takeIf {
            looksLikeCentralEurope(it) && isNearTarget
        }
    }

    private fun resolveActiveRouteState(
        mission: ActiveMission,
        fallbackRouteStart: LatLng,
    ): ActiveRouteState {
        val liveRouteStart = resolveLiveRouteStart(mission)
        if (liveRouteStart == null) {
            return ActiveRouteState(
                routeStart = fallbackRouteStart,
                playerPoint = fallbackRouteStart,
                remainingWaypoints = mission.waypoints,
            )
        }
        if (mission.waypoints.isEmpty()) {
            return ActiveRouteState(
                routeStart = liveRouteStart,
                playerPoint = liveRouteStart,
                remainingWaypoints = emptyList(),
            )
        }

        val routePoints = buildList {
            add(fallbackRouteStart)
            addAll(mission.waypoints.map { LatLng(it.latitude, it.longitude) })
            add(LatLng(mission.target.latitude, mission.target.longitude))
        }
        val nearestIndex = routePoints.indices.minByOrNull { index ->
            distanceMeters(liveRouteStart, routePoints[index])
        } ?: 0
        val dropWaypoints = nearestIndex.coerceIn(0, mission.waypoints.size)

        return ActiveRouteState(
            routeStart = liveRouteStart,
            playerPoint = liveRouteStart,
            remainingWaypoints = mission.waypoints.drop(dropWaypoints),
        )
    }

    private fun applyOrientationBearing() {
        val map = mapLibreMap ?: return
        val mission = currentMission ?: return
        val desiredBearing = resolveNavigationBearing(mission) ?: return
        val currentCamera = map.cameraPosition
        val currentBearing = normalizeDegrees(currentCamera.bearing)
        val normalizedDesired = normalizeDegrees(desiredBearing)
        val delta = smallestAngleDifference(currentBearing, normalizedDesired)
        val now = SystemClock.elapsedRealtime()
        val elapsedSinceCommit = now - lastOrientationCommitAtMillis
        if (kotlin.math.abs(delta) < 4.5) {
            lastAppliedBearingDegrees = currentBearing
            return
        }
        if (elapsedSinceCommit < 320L && kotlin.math.abs(delta) < 12.0) {
            return
        }
        val updatedCamera = CameraPosition.Builder(currentCamera)
            .bearing(normalizedDesired)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(updatedCamera))
        lastAppliedBearingDegrees = normalizedDesired
        lastOrientationCommitAtMillis = now
        refitZoomForCurrentBearing(map, mission)
    }

    private fun resolveNavigationBearing(mission: ActiveMission): Double? {
        val headingBearing = currentHeadingDegrees
            ?.takeIf { it.isFinite() }
            ?.toDouble()
            ?.let(::normalizeDegrees)
        if (headingBearing != null) {
            return headingBearing
        }

        val courseBearing = currentCourseBearingDegrees?.let(::normalizeDegrees)
        if (courseBearing != null) {
            return courseBearing
        }

        return routeBearingForMission(mission)?.let(::normalizeDegrees)
    }

    private fun updateCourseBearing(location: Location?) {
        if (location == null) {
            return
        }
        val previous = previousCourseLocation
        previousCourseLocation = location
        if (previous == null) {
            return
        }
        val previousLatLng = LatLng(previous.latitude, previous.longitude)
        val currentLatLng = LatLng(location.latitude, location.longitude)
        if (distanceMeters(previousLatLng, currentLatLng) < 8.0) {
            return
        }
        currentCourseBearingDegrees = bearingBetween(previousLatLng, currentLatLng)
    }

    private fun smallestAngleDifference(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }

    private fun maximizeZoomForVisibleEndpoints(
        map: MapLibreMap,
        routeStart: LatLng,
        missionTarget: LatLng,
        width: Int,
        height: Int,
        topPadding: Int,
        bottomPadding: Int,
        sidePadding: Int,
    ) {
        val left = sidePadding.toFloat()
        val top = topPadding.toFloat()
        val right = (width - sidePadding).toFloat()
        val bottom = (height - bottomPadding).toFloat()
        val safetyInset = 12f

        repeat(14) {
            val currentCamera = map.cameraPosition
            val candidateZoom = (currentCamera.zoom + 0.30).coerceAtMost(19.2)
            if (candidateZoom <= currentCamera.zoom + 0.01) {
                return
            }

            val candidateCamera = CameraPosition.Builder(currentCamera)
                .zoom(candidateZoom)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(candidateCamera))

            val originPoint = map.projection.toScreenLocation(routeStart)
            val targetPoint = map.projection.toScreenLocation(missionTarget)
            val fits =
                originPoint.x >= left + safetyInset &&
                    originPoint.x <= right - safetyInset &&
                    originPoint.y >= top + safetyInset &&
                    originPoint.y <= bottom - safetyInset &&
                    targetPoint.x >= left + safetyInset &&
                    targetPoint.x <= right - safetyInset &&
                    targetPoint.y >= top + safetyInset &&
                    targetPoint.y <= bottom - safetyInset

            if (!fits) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(currentCamera))
                return
            }
        }
    }

    private fun refitZoomForCurrentBearing(
        map: MapLibreMap,
        mission: ActiveMission,
    ) {
        val width = mapContainer.width
        val height = mapContainer.height
        if (width <= 0 || height <= 0) {
            return
        }

        val routeStart = displayedRouteStart ?: resolveDisplayRouteStart(mission).also {
            displayedRouteStart = it
        }
        val missionTarget = LatLng(mission.target.latitude, mission.target.longitude)
        val activeRoute = resolveActiveRouteState(mission, routeStart)
        val livePlayer = activeRoute.playerPoint
        val activeRoutePoints = buildList {
            add(activeRoute.routeStart)
            activeRoute.remainingWaypoints.forEach { waypoint ->
                add(LatLng(waypoint.latitude, waypoint.longitude))
            }
            add(missionTarget)
        }
        val sidePadding = (width * 0.10f).toInt().coerceAtLeast(40)
        val topPadding = ((viewportTopInsetPx ?: (height * 0.37f)) + (height * 0.02f)).toInt()
        val bottomPadding = ((viewportBottomInsetPx ?: (height * 0.14f)) + (height * 0.04f)).toInt()
        solveRouteCameraForCurrentBearing(
            map = map,
            routeStart = livePlayer,
            missionTarget = missionTarget,
            activeRoutePoints = activeRoutePoints,
            width = width,
            height = height,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            sidePadding = sidePadding,
        )
    }

    private fun solveRouteCameraForCurrentBearing(
        map: MapLibreMap,
        routeStart: LatLng,
        missionTarget: LatLng,
        activeRoutePoints: List<LatLng>,
        width: Int,
        height: Int,
        topPadding: Int,
        bottomPadding: Int,
        sidePadding: Int,
    ) {
        if (activeRoutePoints.size < 2) return
        val availableHeight = (height - topPadding - bottomPadding).coerceAtLeast(1)
        val targetZoneMaxY = topPadding + (availableHeight * 0.10f)
        val originZoneMinY = height - bottomPadding - 14f
        val left = sidePadding.toFloat()
        val top = topPadding.toFloat()
        val right = (width - sidePadding).toFloat()
        val bottom = (height - bottomPadding).toFloat()
        val safetyInset = 12f
        val anchorX = (left + right) / 2f
        val anchorY = (top + bottom) / 2f
        val previousCamera = map.cameraPosition
        val routeCenter = computeRouteCenter(activeRoutePoints)
        val baseCamera = CameraPosition.Builder(map.cameraPosition)
            .target(routeCenter)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(baseCamera))

        val strictCamera = maximizeCameraUnderConstraints(
            map = map,
            baseCamera = baseCamera,
            routeStart = routeStart,
            missionTarget = missionTarget,
            activeRoutePoints = activeRoutePoints,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            safetyInset = safetyInset,
            originZoneMinY = originZoneMinY,
            targetZoneMaxY = targetZoneMaxY,
            desiredCenterX = width / 2f,
            desiredCenterY = anchorY,
            anchorX = anchorX,
            anchorY = anchorY,
            requireZones = true,
            zoneTolerancePx = if (lastZoneFitUsedStrict) 40f else -28f,
        )
        val currentCameraVisible = cameraSatisfiesConstraints(
            map = map,
            candidate = previousCamera,
            routeStart = routeStart,
            missionTarget = missionTarget,
            activeRoutePoints = activeRoutePoints,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            safetyInset = safetyInset,
            originZoneMinY = originZoneMinY,
            targetZoneMaxY = targetZoneMaxY,
            requireZones = false,
            zoneTolerancePx = 0f,
        )
        val visibilityCamera = maximizeCameraUnderConstraints(
            map = map,
            baseCamera = baseCamera,
            routeStart = routeStart,
            missionTarget = missionTarget,
            activeRoutePoints = activeRoutePoints,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            safetyInset = safetyInset,
            originZoneMinY = originZoneMinY,
            targetZoneMaxY = targetZoneMaxY,
            desiredCenterX = width / 2f,
            desiredCenterY = anchorY,
            anchorX = anchorX,
            anchorY = anchorY,
            requireZones = false,
            zoneTolerancePx = 0f,
            minZoom = if (currentCameraVisible) {
                previousCamera.zoom.coerceIn(8.5, 19.2)
            } else {
                8.5
            },
            maxZoom = if (currentCameraVisible) {
                19.2
            } else {
                previousCamera.zoom.coerceIn(8.5, 19.2)
            },
        )
        val finalCamera = strictCamera ?: visibilityCamera ?: previousCamera ?: baseCamera
        lastZoneFitUsedStrict = strictCamera != null
        map.moveCamera(CameraUpdateFactory.newCameraPosition(finalCamera))

        val finalOrigin = map.projection.toScreenLocation(routeStart)
        val finalTarget = map.projection.toScreenLocation(missionTarget)
        Log.d(
            CAMERA_LOG_TAG,
            "zone-fit originY=${finalOrigin.y} targetY=${finalTarget.y} originMinY=$originZoneMinY targetMaxY=$targetZoneMaxY top=$topPadding bottom=$bottomPadding height=$height zoom=${map.cameraPosition.zoom} bearing=${map.cameraPosition.bearing}",
        )
    }

    private fun maximizeCameraUnderConstraints(
        map: MapLibreMap,
        baseCamera: CameraPosition,
        routeStart: LatLng,
        missionTarget: LatLng,
        activeRoutePoints: List<LatLng>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        safetyInset: Float,
        originZoneMinY: Float,
        targetZoneMaxY: Float,
        desiredCenterX: Float,
        desiredCenterY: Float,
        anchorX: Float,
        anchorY: Float,
        requireZones: Boolean,
        zoneTolerancePx: Float,
        minZoom: Double = 8.5,
        maxZoom: Double = 19.2,
    ): CameraPosition? {
        var low = minZoom.coerceIn(8.5, 19.2)
        var high = maxZoom
        var bestCamera: CameraPosition? = null

        if (high < low) {
            return null
        }

        repeat(18) {
            val candidateZoom = (low + high) / 2.0
            val candidate = buildConstrainedCamera(
                map = map,
                baseCamera = baseCamera,
                zoom = candidateZoom,
                routeStart = routeStart,
                missionTarget = missionTarget,
                originZoneMinY = originZoneMinY,
                targetZoneMaxY = targetZoneMaxY,
                desiredCenterX = desiredCenterX,
                desiredCenterY = desiredCenterY,
                anchorX = anchorX,
                anchorY = anchorY,
                requireZones = requireZones,
            )
            val candidateValid = cameraSatisfiesConstraints(
                map = map,
                candidate = candidate,
                routeStart = routeStart,
                missionTarget = missionTarget,
                activeRoutePoints = activeRoutePoints,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                safetyInset = safetyInset,
                originZoneMinY = originZoneMinY,
                targetZoneMaxY = targetZoneMaxY,
                requireZones = requireZones,
                zoneTolerancePx = zoneTolerancePx,
            )

            if (candidateValid) {
                bestCamera = candidate
                low = candidateZoom
            } else {
                high = candidateZoom
            }
        }

        return bestCamera
    }

    private fun cameraSatisfiesConstraints(
        map: MapLibreMap,
        candidate: CameraPosition,
        routeStart: LatLng,
        missionTarget: LatLng,
        activeRoutePoints: List<LatLng>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        safetyInset: Float,
        originZoneMinY: Float,
        targetZoneMaxY: Float,
        requireZones: Boolean,
        zoneTolerancePx: Float,
    ): Boolean {
        map.moveCamera(CameraUpdateFactory.newCameraPosition(candidate))

        val originPoint = map.projection.toScreenLocation(routeStart)
        val targetPoint = map.projection.toScreenLocation(missionTarget)
        val routeVisible = activeRoutePoints.all { point ->
            val screenPoint = map.projection.toScreenLocation(point)
            screenPoint.x >= left + safetyInset &&
                screenPoint.x <= right - safetyInset &&
                screenPoint.y >= top + safetyInset &&
                screenPoint.y <= bottom - safetyInset
        }
        val endpointsVisible =
            originPoint.x >= left + safetyInset &&
                originPoint.x <= right - safetyInset &&
                originPoint.y >= top + safetyInset &&
                originPoint.y <= bottom - safetyInset &&
                targetPoint.x >= left + safetyInset &&
                targetPoint.x <= right - safetyInset &&
                targetPoint.y >= top + safetyInset &&
                targetPoint.y <= bottom - safetyInset
        val endpointsInZones =
            originPoint.y >= originZoneMinY - zoneTolerancePx &&
                targetPoint.y <= targetZoneMaxY + zoneTolerancePx
        return routeVisible && endpointsVisible && (!requireZones || endpointsInZones)
    }

    private fun buildConstrainedCamera(
        map: MapLibreMap,
        baseCamera: CameraPosition,
        zoom: Double,
        routeStart: LatLng,
        missionTarget: LatLng,
        originZoneMinY: Float,
        targetZoneMaxY: Float,
        desiredCenterX: Float,
        desiredCenterY: Float,
        anchorX: Float,
        anchorY: Float,
        requireZones: Boolean,
    ): CameraPosition {
        val zoomCamera = CameraPosition.Builder(baseCamera)
            .zoom(zoom)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(zoomCamera))

        val originPoint = map.projection.toScreenLocation(routeStart)
        val targetPoint = map.projection.toScreenLocation(missionTarget)
        val currentMidX = (originPoint.x + targetPoint.x) / 2f
        val currentMidY = (originPoint.y + targetPoint.y) / 2f
        val deltaX = desiredCenterX - currentMidX
        val deltaY = if (requireZones) {
            val minDeltaY = originZoneMinY - originPoint.y
            val maxDeltaY = targetZoneMaxY - targetPoint.y
            val preferredDeltaY = originZoneMinY - originPoint.y
            if (minDeltaY <= maxDeltaY) {
                preferredDeltaY.coerceIn(minDeltaY, maxDeltaY)
            } else {
                preferredDeltaY
            }
        } else {
            desiredCenterY - currentMidY
        }
        val shiftedTarget = map.projection.fromScreenLocation(
            PointF(anchorX - deltaX, anchorY - deltaY),
        )
        return CameraPosition.Builder(map.cameraPosition)
            .target(shiftedTarget)
            .zoom(zoom)
            .build()
    }

    private fun computeRouteCenter(points: List<LatLng>): LatLng {
        val latMin = points.minOf { it.latitude }
        val latMax = points.maxOf { it.latitude }
        val lonMin = points.minOf { it.longitude }
        val lonMax = points.maxOf { it.longitude }
        return LatLng(
            (latMin + latMax) / 2.0,
            (lonMin + lonMax) / 2.0,
        )
    }

    private fun bearingBetween(start: LatLng, end: LatLng): Double {
        val startLat = Math.toRadians(start.latitude)
        val startLon = Math.toRadians(start.longitude)
        val endLat = Math.toRadians(end.latitude)
        val endLon = Math.toRadians(end.longitude)
        val deltaLon = endLon - startLon
        val y = kotlin.math.sin(deltaLon) * kotlin.math.cos(endLat)
        val x = kotlin.math.cos(startLat) * kotlin.math.sin(endLat) -
            kotlin.math.sin(startLat) * kotlin.math.cos(endLat) * kotlin.math.cos(deltaLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    private fun normalizeDegrees(value: Double): Double {
        return ((value % 360.0) + 360.0) % 360.0
    }

    private fun looksLikeCentralEurope(target: LatLng): Boolean {
        return target.latitude in 47.0..56.5 && target.longitude in 5.0..16.5
    }

    private fun distanceMeters(start: LatLng, end: LatLng): Double {
        val result = FloatArray(1)
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            result,
        )
        return result[0].toDouble()
    }

    private fun buildMissingOfflineMapStyleJson(): String {
        return """
            {
              "version": 8,
              "name": "CacheKid Missing Offline Map",
              "sources": {},
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#f4f2ea"
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildOfflinePackageStyleJson(offlinePackage: OfflineBaseMapPackage): String {
        val tileUrl = "pmtiles://file://${offlinePackage.packageDirectory.absolutePath}/${offlinePackage.tileAssetPath}"
        val styleFile = File(offlinePackage.packageDirectory, offlinePackage.styleAssetPath)
        val packageStyle = runCatching { styleFile.readText() }.getOrNull()
        if (!packageStyle.isNullOrBlank()) {
            return packageStyle
                .replace("\${CACHEKID_PMTILES_URL}", tileUrl)
                .replace("CACHEKID_PMTILES_URL", tileUrl)
                .replace("pmtiles://cachekid-local-map", tileUrl)
        }

        return """
            {
              "version": 8,
              "name": "CacheKid Offline ${escapeJson(offlinePackage.id)}",
              "sources": {
                "$OFFLINE_BASEMAP_SOURCE_ID": {
                  "type": "vector",
                  "url": "${escapeJson(tileUrl)}",
                  "minzoom": ${offlinePackage.minZoom},
                  "maxzoom": ${offlinePackage.maxZoom}
                }
              },
              "layers": [
                {
                  "id": "background",
                  "type": "background",
                  "paint": {
                    "background-color": "#f4f2ea"
                  }
                },
                {
                  "id": "$OFFLINE_BASEMAP_LAYER_ID",
                  "type": "background",
                  "paint": {
                    "background-color": "#e1dfd6"
                  }
                }
              ]
            }
        """.trimIndent()
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private data class ActiveRouteState(
        val routeStart: LatLng,
        val playerPoint: LatLng,
        val remainingWaypoints: List<com.cachekid.companion.host.mission.MissionWaypoint>,
    )
}
