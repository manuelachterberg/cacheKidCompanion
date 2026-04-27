package com.cachekid.companion.host.mission

import java.io.File

data class OfflineBaseMapPackage(
    val id: String,
    val displayName: String,
    val version: String,
    val format: OfflineBaseMapPackageFormat,
    val bounds: MissionMapBounds,
    val tileAssetPath: String,
    val styleAssetPath: String,
    val packageDirectory: File,
    val minZoom: Int,
    val maxZoom: Int,
    val attribution: String?,
) {
    fun covers(target: MissionTarget): Boolean = bounds.contains(target)
}

enum class OfflineBaseMapPackageFormat(val metadataValue: String) {
    PMTILES_VECTOR("pmtiles-vector");

    companion object {
        fun fromMetadataValue(value: String?): OfflineBaseMapPackageFormat? {
            return entries.firstOrNull { it.metadataValue == value }
        }
    }
}
