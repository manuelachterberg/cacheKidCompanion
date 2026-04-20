package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.importing.SharedCacheImport

interface CacheResolver {
    fun resolve(import: SharedCacheImport): CacheResolutionResult
}
