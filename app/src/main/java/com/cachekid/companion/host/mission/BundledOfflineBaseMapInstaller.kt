package com.cachekid.companion.host.mission

import java.io.File

class BundledOfflineBaseMapInstaller(
    private val assetProvider: BaseMapAssetProvider,
) {

    fun installOrReplace(targetDirectory: File, assetRoot: String = DEFAULT_ASSET_ROOT) {
        if (targetDirectory.exists()) {
            targetDirectory.listFiles()?.forEach(::deleteRecursively)
        }
        targetDirectory.mkdirs()
        installDirectory(assetRoot, targetDirectory)
    }

    private fun installDirectory(assetPath: String, targetDirectory: File) {
        assetProvider.list(assetPath).forEach { childName ->
            val childAssetPath = "$assetPath/$childName"
            val childTarget = File(targetDirectory, childName)
            val childEntries = assetProvider.list(childAssetPath)
            if (childEntries.isEmpty()) {
                childTarget.writeBytes(assetProvider.readBytes(childAssetPath))
            } else {
                childTarget.mkdirs()
                installDirectory(childAssetPath, childTarget)
            }
        }
    }

    interface BaseMapAssetProvider {
        fun list(path: String): List<String>
        fun readBytes(path: String): ByteArray
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        file.delete()
    }

    private companion object {
        const val DEFAULT_ASSET_ROOT = "offline-basemaps"
    }
}
