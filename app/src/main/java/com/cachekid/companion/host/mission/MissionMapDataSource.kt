package com.cachekid.companion.host.mission

interface MissionMapDataSource {
    fun fetch(bounds: MissionMapBounds): String?
}
