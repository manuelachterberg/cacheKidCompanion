package com.cachekid.companion.host.mission

import java.net.URLEncoder

class OverpassQueryBuilder {

    fun build(bounds: MissionMapBounds): String {
        val query = """
            [out:json][timeout:25];
            (
              way["highway"](${bbox(bounds)});
              way["waterway"](${bbox(bounds)});
              way["natural"="water"](${bbox(bounds)});
              way["landuse"="forest"](${bbox(bounds)});
            );
            out geom;
        """.trimIndent()
        return URLEncoder.encode(query, Charsets.UTF_8.name())
    }

    private fun bbox(bounds: MissionMapBounds): String {
        return "${bounds.minLatitude},${bounds.minLongitude},${bounds.maxLatitude},${bounds.maxLongitude}"
    }
}
