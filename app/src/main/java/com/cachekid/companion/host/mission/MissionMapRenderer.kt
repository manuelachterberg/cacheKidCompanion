package com.cachekid.companion.host.mission

interface MissionMapRenderer {
    fun render(mapData: String, bounds: MissionMapBounds): String
}
