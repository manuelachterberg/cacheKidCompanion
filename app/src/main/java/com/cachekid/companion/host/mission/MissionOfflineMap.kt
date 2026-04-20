package com.cachekid.companion.host.mission

data class MissionOfflineMap(
    val svgContent: String,
    val bounds: MissionMapBounds,
    val assetPath: String = MissionPackageSchema.MAP_SVG_FILE,
)
