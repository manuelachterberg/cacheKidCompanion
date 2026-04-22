package com.cachekid.companion.kid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Path
import android.location.Location
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.cachekid.companion.host.mission.ActiveMission
import org.json.JSONArray
import org.json.JSONObject
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class KidNativeMapController(
    context: Context,
    private val mapContainer: FrameLayout,
) {
    private companion object {
        const val TARGET_SOURCE_ID = "cachekid-target-source"
        const val PLAYER_SOURCE_ID = "cachekid-player-source"
        const val ROUTE_SOURCE_ID = "cachekid-route-source"
        const val TARGET_LAYER_ID = "cachekid-target-layer"
        const val PLAYER_LAYER_ID = "cachekid-player-layer"
        const val ROUTE_UNDERLAY_LAYER_ID = "cachekid-route-underlay-layer"
        const val ROUTE_LAYER_ID = "cachekid-route-layer"
        const val ROUTE_ACCENT_LAYER_ID = "cachekid-route-accent-layer"
        const val TARGET_ICON_ID = "cachekid-target-icon"
        const val PLAYER_ICON_ID = "cachekid-player-icon"
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
    private var displayedMissionId: String? = null
    private var displayedRouteStart: LatLng? = null
    private var lastCameraDebugInfo: CameraDebugInfo? = null
    private var viewportTopInsetPx: Float? = null
    private var viewportBottomInsetPx: Float? = null

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
            map.setStyle(Style.Builder().fromJson(buildRasterStyleJson())) {
                ensureMissionOverlayLayers(it)
                mapStyleLoaded = true
                Log.d(CAMERA_LOG_TAG, "style-ready mission=${currentMission?.missionId ?: "--"}")
                updateMissionOverlays()
                updateCamera()
            }
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
            displayedMissionId = null
            displayedRouteStart = null
            previousCourseLocation = null
            currentCourseBearingDegrees = null
        } else if (missionChanged || displayedMissionId != mission.missionId || displayedRouteStart == null) {
            lastAppliedBearingDegrees = null
            displayedMissionId = mission.missionId
            displayedRouteStart = resolveDisplayRouteStart(mission)
            previousCourseLocation = null
            currentCourseBearingDegrees = null
        }
        mapContainer.visibility = if (mission != null) View.VISIBLE else View.GONE
        updateMissionOverlays()
        if (missionChanged || mission == null) {
            updateCamera(animate = false)
        }
    }

    fun updateLocation(location: Location?) {
        updateCourseBearing(location)
        currentLocation = location
        updateMissionOverlays()
        applyOrientationBearing()
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
        val routeStart = displayedRouteStart ?: resolveDisplayRouteStart(mission).also {
            displayedRouteStart = it
        }
        val routePoints = buildRouteLatLngs(mission, routeStart, missionTarget)
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
            maximizeZoomForVisibleRoute(
                map = map,
                routePoints = routePoints,
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
            .tilt(16.0)
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
        val targetPoint = Point.fromLngLat(mission.target.longitude, mission.target.latitude)
        targetSource.setGeoJson(
            FeatureCollection.fromFeatures(
                buildTargetFeatures(targetPoint),
            ),
        )

        val routeStartLatLng = displayedRouteStart ?: resolveDisplayRouteStart(mission).also {
            displayedRouteStart = it
        }
        val routeStartPoint = Point.fromLngLat(routeStartLatLng.longitude, routeStartLatLng.latitude)
        val playerPoint = resolvePlayerPoint(routeStartPoint)
        playerSource.setGeoJson(
            FeatureCollection.fromFeatures(
                buildPlayerFeatures(playerPoint),
            ),
        )

        val routeFeatures = buildRouteFeatures(
            routeStartPoint = routeStartPoint,
            waypoints = mission.waypoints.map { waypoint ->
                Point.fromLngLat(waypoint.longitude, waypoint.latitude)
            },
            targetPoint = targetPoint,
        )
        Log.d(
            CAMERA_LOG_TAG,
            "updateMissionOverlays mission=${mission.missionId} routeStart=${routeStartLatLng.latitude},${routeStartLatLng.longitude} waypointCount=${mission.waypoints.size} target=${mission.target.latitude},${mission.target.longitude}",
        )
        routeSource.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))
    }

    private fun ensureMissionOverlayLayers(style: Style) {
        if (style.getImage(TARGET_ICON_ID) == null) {
            style.addImage(TARGET_ICON_ID, buildTargetMarkerBitmap())
        }
        if (style.getImage(PLAYER_ICON_ID) == null) {
            style.addImage(PLAYER_ICON_ID, buildPlayerMarkerBitmap())
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

    private fun buildTargetFeatures(targetPoint: Point): List<Feature> {
        return listOf(Feature.fromGeometry(targetPoint))
    }

    private fun buildPlayerFeatures(playerPoint: Point): List<Feature> {
        return listOf(Feature.fromGeometry(playerPoint))
    }

    private fun buildRouteFeatures(
        routeStartPoint: Point,
        waypoints: List<Point>,
        targetPoint: Point,
    ): List<Feature> {
        if (waypoints.isEmpty()) {
            return emptyList()
        }
        val routePoints = buildList {
            add(routeStartPoint)
            addAll(waypoints)
            add(targetPoint)
        }
        if (routePoints.size < 2) {
            return emptyList()
        }
        val primaryPoints = routePoints
        val sketchA = Feature.fromGeometry(
            LineString.fromLngLats(
                primaryPoints,
            ),
        )
        val sketchB = Feature.fromGeometry(
            LineString.fromLngLats(
                primaryPoints,
            ),
        )
        return listOf(sketchA, sketchB)
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

    private fun resolvePlayerPoint(routeStartPoint: Point): Point {
        return routeStartPoint
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
        val routeStart = displayedRouteStart ?: return null
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

    private fun applyOrientationBearing() {
        val map = mapLibreMap ?: return
        val mission = currentMission ?: return
        val desiredBearing = resolveNavigationBearing(mission) ?: return
        val currentCamera = map.cameraPosition
        val currentBearing = normalizeDegrees(currentCamera.bearing)
        val normalizedDesired = normalizeDegrees(desiredBearing)
        val delta = smallestAngleDifference(currentBearing, normalizedDesired)
        if (kotlin.math.abs(delta) < 2.0) {
            lastAppliedBearingDegrees = currentBearing
            return
        }
        val updatedCamera = CameraPosition.Builder(currentCamera)
            .bearing(normalizedDesired)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(updatedCamera))
        lastAppliedBearingDegrees = normalizedDesired
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

    private fun maximizeZoomForVisibleRoute(
        map: MapLibreMap,
        routePoints: List<LatLng>,
        width: Int,
        height: Int,
        topPadding: Int,
        bottomPadding: Int,
        sidePadding: Int,
    ) {
        if (routePoints.size < 2) {
            return
        }

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

            val fits = routePoints.all { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                screenPoint.x >= left + safetyInset &&
                    screenPoint.x <= right - safetyInset &&
                    screenPoint.y >= top + safetyInset &&
                    screenPoint.y <= bottom - safetyInset
            }

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
        val routePoints = buildRouteLatLngs(mission, routeStart, missionTarget)
        if (routePoints.size < 2) {
            return
        }

        val sidePadding = (width * 0.10f).toInt().coerceAtLeast(40)
        val topPadding = ((viewportTopInsetPx ?: (height * 0.37f)) + (height * 0.02f)).toInt()
        val bottomPadding = ((viewportBottomInsetPx ?: (height * 0.14f)) + (height * 0.04f)).toInt()
        val left = sidePadding.toFloat()
        val top = topPadding.toFloat()
        val right = (width - sidePadding).toFloat()
        val bottom = (height - bottomPadding).toFloat()
        val safetyInset = 12f

        repeat(14) {
            val currentCamera = map.cameraPosition
            val fits = routePoints.all { point ->
                val screenPoint = map.projection.toScreenLocation(point)
                screenPoint.x >= left + safetyInset &&
                    screenPoint.x <= right - safetyInset &&
                    screenPoint.y >= top + safetyInset &&
                    screenPoint.y <= bottom - safetyInset
            }
            if (fits) {
                return@repeat
            }
            val zoomedOut = CameraPosition.Builder(currentCamera)
                .zoom((currentCamera.zoom - 0.26).coerceAtLeast(9.5))
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(zoomedOut))
        }

        maximizeZoomForVisibleRoute(
            map = map,
            routePoints = routePoints,
            width = width,
            height = height,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            sidePadding = sidePadding,
        )

        alignEndpointsToZones(
            map = map,
            routeStart = routeStart,
            missionTarget = missionTarget,
            width = width,
            height = height,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            sidePadding = sidePadding,
        )
    }

    private fun alignEndpointsToZones(
        map: MapLibreMap,
        routeStart: LatLng,
        missionTarget: LatLng,
        width: Int,
        height: Int,
        topPadding: Int,
        bottomPadding: Int,
        sidePadding: Int,
    ) {
        val availableHeight = (height - topPadding - bottomPadding).coerceAtLeast(1)
        val targetCenterY = topPadding + (availableHeight * 0.02f)
        val originCenterY = (height - bottomPadding - 28f).coerceAtLeast(targetCenterY + 120f)
        val desiredMidY = (targetCenterY + originCenterY) / 2f
        val desiredSeparation = (originCenterY - targetCenterY).coerceAtLeast(1f)
        val left = sidePadding.toFloat()
        val top = topPadding.toFloat()
        val right = (width - sidePadding).toFloat()
        val bottom = (height - bottomPadding).toFloat()
        val safetyInset = 20f

        repeat(8) {
            val currentCamera = map.cameraPosition
            val originPoint = map.projection.toScreenLocation(routeStart)
            val targetPoint = map.projection.toScreenLocation(missionTarget)
            val currentSeparation = (originPoint.y - targetPoint.y).coerceAtLeast(1f)
            val currentMidY = (originPoint.y + targetPoint.y) / 2f
            val zoomDelta = kotlin.math.log2((desiredSeparation / currentSeparation).toDouble())
                .coerceIn(-0.35, 0.35)
            val candidateZoom = (currentCamera.zoom + zoomDelta).coerceIn(9.5, 18.8)
            val zoomCamera = CameraPosition.Builder(currentCamera)
                .zoom(candidateZoom)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(zoomCamera))

            val postZoomOrigin = map.projection.toScreenLocation(routeStart)
            val postZoomTarget = map.projection.toScreenLocation(missionTarget)
            val postZoomMidY = (postZoomOrigin.y + postZoomTarget.y) / 2f
            val deltaMidY = desiredMidY - postZoomMidY
            val anchorX = width / 2f
            val anchorY = (top + bottom) / 2f
            val shiftedTarget = map.projection.fromScreenLocation(
                PointF(anchorX, anchorY - deltaMidY),
            )
            val shiftedCamera = CameraPosition.Builder(map.cameraPosition)
                .target(shiftedTarget)
                .build()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(shiftedCamera))

            val alignedOrigin = map.projection.toScreenLocation(routeStart)
            val alignedTarget = map.projection.toScreenLocation(missionTarget)
            val fits =
                alignedOrigin.x >= left + safetyInset &&
                    alignedOrigin.x <= right - safetyInset &&
                    alignedOrigin.y >= top + safetyInset &&
                    alignedOrigin.y <= bottom - safetyInset &&
                    alignedTarget.x >= left + safetyInset &&
                    alignedTarget.x <= right - safetyInset &&
                    alignedTarget.y >= top + safetyInset &&
                    alignedTarget.y <= bottom - safetyInset
            if (!fits) {
                map.moveCamera(CameraUpdateFactory.newCameraPosition(currentCamera))
                return
            }
        }

        val finalOrigin = map.projection.toScreenLocation(routeStart)
        val finalTarget = map.projection.toScreenLocation(missionTarget)
        Log.d(
            CAMERA_LOG_TAG,
            "zone-fit originY=${finalOrigin.y} targetY=${finalTarget.y} desiredOriginY=$originCenterY desiredTargetY=$targetCenterY top=$topPadding bottom=$bottomPadding height=$height zoom=${map.cameraPosition.zoom} bearing=${map.cameraPosition.bearing}",
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

    private fun buildRasterStyleJson(): String {
        return JSONObject().apply {
            put("version", 8)
            put("name", "CacheKid Native Kid Map")
            put(
                "sources",
                JSONObject().apply {
                    put(
                        "osm-raster",
                        JSONObject().apply {
                            put("type", "raster")
                            put(
                                "tiles",
                                JSONArray().put("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
                            )
                            put("tileSize", 256)
                            put("minzoom", 0)
                            put("maxzoom", 19)
                        },
                    )
                },
            )
            put(
                "layers",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("id", "background")
                            put("type", "background")
                            put(
                                "paint",
                                JSONObject().apply {
                                    put("background-color", "#f4f2ea")
                                },
                            )
                        },
                    )
                    .put(
                        JSONObject().apply {
                            put("id", "osm-raster")
                            put("type", "raster")
                            put("source", "osm-raster")
                            put(
                                "paint",
                                JSONObject().apply {
                                    put("raster-saturation", -1)
                                    put("raster-contrast", 0.08)
                                    put("raster-brightness-min", 0.28)
                                    put("raster-brightness-max", 1.02)
                                    put("raster-opacity", 0.93)
                                },
                            )
                        },
                    ),
            )
        }.toString()
    }
}
