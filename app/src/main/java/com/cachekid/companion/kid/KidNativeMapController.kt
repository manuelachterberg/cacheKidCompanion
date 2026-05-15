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
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
    private val overlayContainer: FrameLayout,
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
    private var lastAppliedBearingDegrees: Double? = null
    private var displayedMissionId: String? = null
    private var displayedRouteStart: LatLng? = null
    private var displayedStyleKey: String? = null
    private var lastCameraDebugInfo: CameraDebugInfo? = null
    private var viewportTopInsetPx: Float? = null
    private var viewportBottomInsetPx: Float? = null
    private val sourceIndicatorView: ImageView
    private val cameraPlanner = KidMapCameraPlanner()
    private var currentCameraMode: CameraMode = CameraMode.ROUTE_OVERVIEW
    private lateinit var cameraModeToggleView: android.widget.TextView
    private val arrivalImageView: ImageView
    private var isArrivalShowing = false

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

        val cardSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 56f, context.resources.displayMetrics,
        ).toInt()
        val marginPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics,
        ).toInt()
        sourceIndicatorView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(cardSizePx, cardSizePx).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                bottomMargin = marginPx
                rightMargin = marginPx + cardSizePx + marginPx
            }
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#FFFDF8"))
            elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics,
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        overlayContainer.addView(sourceIndicatorView)
        sourceIndicatorView.bringToFront()

        val toggleSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics,
        ).toInt()
        val toggleMarginPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics,
        ).toInt()
        cameraModeToggleView = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(toggleSizePx, toggleSizePx).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                bottomMargin = toggleMarginPx
                leftMargin = toggleMarginPx
            }
            text = "O"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics,
            )
            setOnClickListener { toggleCameraMode() }
        }
        overlayContainer.addView(cameraModeToggleView)
        cameraModeToggleView.bringToFront()

        // Arrival overlay (cacheClose.png) – fullscreen, hidden by default
        arrivalImageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            visibility = View.GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadBitmapFromAssets(context, "web/cacheClose.png"))
        }
        overlayContainer.addView(arrivalImageView)
        arrivalImageView.bringToFront()
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
            displayedMissionId = null
            displayedRouteStart = null
            displayedStyleKey = null
        } else if (missionChanged || displayedMissionId != mission.missionId || displayedRouteStart == null) {
            lastAppliedBearingDegrees = null
            displayedMissionId = mission.missionId
            displayedRouteStart = resolveDisplayRouteStart(mission)
        }
        mapContainer.visibility = if (mission != null) View.VISIBLE else View.GONE
        sourceIndicatorView.visibility = if (mission != null) View.VISIBLE else View.GONE
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
        currentLocation = location
        Log.d(
            CAMERA_LOG_TAG,
            "updateLocation raw=${location?.latitude},${location?.longitude} accuracy=${location?.accuracy} bearing=${location?.bearing}",
        )
        updateMissionOverlays()
        checkArrival()
        updateCameraFromPlan(animate = true)
    }

    fun updateHeading(headingDegrees: Float?) {
        currentHeadingDegrees = headingDegrees
        if (currentCameraMode == CameraMode.FOLLOW_HEADING_UP) {
            updateCameraFromPlan(animate = true)
        }
    }

    fun updateNavigationSource(locationSource: String, headingSource: String) {
        val icon = when {
            locationSource.equals("onboard", ignoreCase = true) || headingSource.equals("onboard", ignoreCase = true) -> buildGpsIconBitmap()
            locationSource.equals("injected", ignoreCase = true) || headingSource.equals("injected", ignoreCase = true) -> buildPhoneIconBitmap()
            else -> buildNoSignalIconBitmap()
        }
        sourceIndicatorView.setImageBitmap(icon)
    }

    private fun buildGpsIconBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawCircle(60f, 60f, 40f, halo)
        canvas.drawCircle(60f, 60f, 40f, ink)
        canvas.drawCircle(60f, 60f, 16f, dot)
        return bitmap
    }

    private fun buildPhoneIconBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        // Outer rounded rect
        val outerRect = android.graphics.RectF(20f, 10f, 100f, 110f)
        canvas.drawRoundRect(outerRect, 12f, 12f, halo)
        canvas.drawRoundRect(outerRect, 12f, 12f, ink)
        canvas.drawRoundRect(outerRect, 12f, 12f, fill)
        // Inner screen
        val innerRect = android.graphics.RectF(32f, 26f, 88f, 84f)
        canvas.drawRoundRect(innerRect, 6f, 6f, inner)
        // Home button dot
        canvas.drawCircle(60f, 98f, 6f, inner)
        return bitmap
    }

    private fun buildNoSignalIconBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(60f, 60f, 40f, halo)
        canvas.drawCircle(60f, 60f, 40f, ink)
        canvas.drawLine(36f, 36f, 84f, 84f, cross)
        canvas.drawLine(84f, 36f, 36f, 84f, cross)
        return bitmap
    }

    fun getLastCameraDebugInfo(): CameraDebugInfo? = lastCameraDebugInfo

    fun getCurrentMapBearingDegrees(): Double? =
        mapLibreMap?.cameraPosition?.bearing?.let { cameraPlanner.normalizeDegrees(it) } ?: lastAppliedBearingDegrees

    fun getCurrentTargetBearingDegrees(): Double? = currentMission?.let { routeBearingForMission(it) }

    fun updateViewportInsets(topInsetPx: Float, bottomInsetPx: Float) {
        viewportTopInsetPx = topInsetPx
        viewportBottomInsetPx = bottomInsetPx
    }

    fun toggleCameraMode() {
        currentCameraMode = when (currentCameraMode) {
            CameraMode.ROUTE_OVERVIEW -> CameraMode.FOLLOW_HEADING_UP
            CameraMode.FOLLOW_HEADING_UP -> CameraMode.ROUTE_OVERVIEW
        }
        cameraModeToggleView.text = when (currentCameraMode) {
            CameraMode.ROUTE_OVERVIEW -> "O"
            CameraMode.FOLLOW_HEADING_UP -> "F"
        }
        Log.d(CAMERA_LOG_TAG, "toggleCameraMode -> $currentCameraMode")
        updateCameraFromPlan(animate = true)
    }

    private fun updateCamera(animate: Boolean = false) {
        updateCameraFromPlan(animate)
    }

    private fun currentViewport(): Viewport {
        val width = mapContainer.width
        val height = mapContainer.height
        val topPadding = (viewportTopInsetPx ?: (height * 0.37f)).toInt()
        val bottomPadding = (viewportBottomInsetPx ?: (height * 0.14f)).toInt()
        val sidePadding = (width * 0.08f).toInt()
        return Viewport(
            widthPx = width,
            heightPx = height,
            topPaddingPx = topPadding,
            bottomPaddingPx = bottomPadding,
            leftPaddingPx = sidePadding,
            rightPaddingPx = sidePadding,
        )
    }

    private fun updateCameraFromPlan(animate: Boolean = true) {
        val map = mapLibreMap ?: return
        if (!mapStyleLoaded) {
            Log.d(CAMERA_LOG_TAG, "updateCameraFromPlan skipped mapStyleLoaded=false")
            return
        }

        val mission = currentMission
        if (mission == null) {
            Log.d(CAMERA_LOG_TAG, "updateCameraFromPlan skipped no mission")
            return
        }

        val location = currentLocation?.let { LocationSnapshot(it.latitude, it.longitude) }
        val heading = currentHeadingDegrees?.toDouble()
        val viewport = currentViewport()

        val plan = cameraPlanner.plan(currentCameraMode, location, heading, mission, viewport)

        // Respektiere Auto-Switch aus dem Planner (z.B. Follow → Overview bei großer Distanz)
        if (plan.mode != currentCameraMode) {
            currentCameraMode = plan.mode
            cameraModeToggleView.text = when (plan.mode) {
                CameraMode.ROUTE_OVERVIEW -> "O"
                CameraMode.FOLLOW_HEADING_UP -> "F"
            }
            Log.d(CAMERA_LOG_TAG, "Planner auto-switched to ${plan.mode}")
        }

        val update = when {
            plan.bounds != null -> {
                map.setPadding(
                    viewport.leftPaddingPx,
                    viewport.topPaddingPx,
                    viewport.rightPaddingPx,
                    viewport.bottomPaddingPx,
                )
                CameraUpdateFactory.newLatLngBounds(
                    plan.bounds,
                    viewport.leftPaddingPx,
                    viewport.topPaddingPx,
                    viewport.rightPaddingPx,
                    viewport.bottomPaddingPx,
                )
            }
            plan.target != null && plan.zoom != null -> {
                map.setPadding(0, 0, 0, 0)
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(plan.target)
                        .zoom(plan.zoom)
                        .bearing(plan.bearing)
                        .tilt(plan.tilt)
                        .build()
                )
            }
            else -> {
                Log.d(CAMERA_LOG_TAG, "updateCameraFromPlan empty plan")
                return
            }
        }

        Log.d(
            CAMERA_LOG_TAG,
            "updateCameraFromPlan mode=${plan.mode} bearing=${plan.bearing} zoom=${plan.zoom} animate=$animate",
        )

        if (animate) {
            map.animateCamera(update)
        } else {
            map.moveCamera(update)
        }
        logScreenPositions()

        lastAppliedBearingDegrees = plan.bearing
    }

    private fun logScreenPositions() {
        val map = mapLibreMap ?: return
        val mission = currentMission ?: return
        val playerLatLng = resolveDisplayRouteStart(mission)
        val targetLatLng = LatLng(mission.target.latitude, mission.target.longitude)
        val playerScreen = map.projection.toScreenLocation(playerLatLng)
        val targetScreen = map.projection.toScreenLocation(targetLatLng)
        Log.d(
            CAMERA_LOG_TAG,
            "screen player=${playerScreen.x},${playerScreen.y} target=${targetScreen.x},${targetScreen.y} width=${mapContainer.width} height=${mapContainer.height}",
        )
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
        return cameraPlanner.bearingBetween(routeStart, missionTarget)
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

    private fun checkArrival() {
        val location = currentLocation ?: return
        val mission = currentMission ?: return
        val hasArrived = cameraPlanner.hasArrived(
            playerLat = location.latitude,
            playerLon = location.longitude,
            targetLat = mission.target.latitude,
            targetLon = mission.target.longitude,
        )
        if (hasArrived && !isArrivalShowing) {
            isArrivalShowing = true
            arrivalImageView.visibility = View.VISIBLE
            arrivalImageView.bringToFront()
        } else if (!hasArrived && isArrivalShowing) {
            isArrivalShowing = false
            arrivalImageView.visibility = View.GONE
        }
    }

    private fun loadBitmapFromAssets(context: Context, path: String): Bitmap? {
        return runCatching {
            context.assets.open(path).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
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
