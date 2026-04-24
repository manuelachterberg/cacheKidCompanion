package com.cachekid.companion.host.mission

import android.content.res.AssetManager

class AssetManagerBaseMapAssetProvider(
    private val assetManager: AssetManager,
) : BundledOfflineBaseMapInstaller.BaseMapAssetProvider {

    override fun list(path: String): List<String> {
        return assetManager.list(path)?.toList().orEmpty()
    }

    override fun readBytes(path: String): ByteArray {
        return assetManager.open(path).use { it.readBytes() }
    }
}
