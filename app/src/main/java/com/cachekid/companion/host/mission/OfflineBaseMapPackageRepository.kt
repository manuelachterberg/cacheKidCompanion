package com.cachekid.companion.host.mission

import java.io.File

class OfflineBaseMapPackageRepository(
    private val baseDirectory: File,
    private val manifestReader: OfflineBaseMapPackageManifestReader = OfflineBaseMapPackageManifestReader(),
) {

    fun listInstalled(): List<OfflineBaseMapPackage> {
        if (!baseDirectory.exists() || !baseDirectory.isDirectory) {
            return emptyList()
        }

        return baseDirectory.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { packageDirectory -> loadPackage(packageDirectory) }
            ?.sortedBy { it.displayName }
            .orEmpty()
    }

    fun findCovering(target: MissionTarget): OfflineBaseMapPackage? {
        return listInstalled().firstOrNull { offlinePackage -> offlinePackage.covers(target) }
    }

    private fun loadPackage(packageDirectory: File): OfflineBaseMapPackage? {
        val metadata = File(packageDirectory, MissionPackageSchema.OFFLINE_MAP_METADATA_FILE)
            .takeIf { it.exists() && it.isFile }
            ?.readText()
            ?: return null

        val packageId = manifestReader.readPackageId(metadata, fallbackId = packageDirectory.name)
            ?: return null
        val offlinePackage = manifestReader.read(metadata, packageId) ?: return null

        if (
            !File(packageDirectory, offlinePackage.tileAssetPath).isFile ||
            !File(packageDirectory, offlinePackage.styleAssetPath).isFile
        ) {
            return null
        }

        return offlinePackage
    }
}
