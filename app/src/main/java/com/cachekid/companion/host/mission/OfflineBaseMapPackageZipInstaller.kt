package com.cachekid.companion.host.mission

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class OfflineBaseMapPackageZipInstaller(
    private val manifestReader: OfflineBaseMapPackageManifestReader = OfflineBaseMapPackageManifestReader(),
) {

    fun install(baseDirectory: File, zipBytes: ByteArray): OfflineBaseMapPackageInstallResult {
        val zipFiles = decodeZip(zipBytes).getOrElse {
            return OfflineBaseMapPackageInstallResult(
                offlinePackage = null,
                packageDirectory = null,
                errors = listOf("Offline map ZIP could not be decoded."),
            )
        }
        val metadata = zipFiles[MissionPackageSchema.OFFLINE_MAP_METADATA_FILE]?.toString(Charsets.UTF_8)
            ?: return OfflineBaseMapPackageInstallResult(
                offlinePackage = null,
                packageDirectory = null,
                errors = listOf("Offline map package is missing ${MissionPackageSchema.OFFLINE_MAP_METADATA_FILE}."),
            )
        val packageId = manifestReader.readPackageId(metadata, fallbackId = "")
            ?: return OfflineBaseMapPackageInstallResult(
                offlinePackage = null,
                packageDirectory = null,
                errors = listOf("Offline map package id is missing or invalid."),
            )
        val packageDirectory = File(baseDirectory, packageId)
        val offlinePackage = manifestReader.read(metadata, fallbackId = packageId, packageDirectory = packageDirectory)
            ?: return OfflineBaseMapPackageInstallResult(
                offlinePackage = null,
                packageDirectory = null,
                errors = listOf("Offline map manifest is invalid."),
            )

        val missingFiles = listOf(offlinePackage.tileAssetPath, offlinePackage.styleAssetPath)
            .filterNot { path -> zipFiles.containsKey(path) }
        if (missingFiles.isNotEmpty()) {
            return OfflineBaseMapPackageInstallResult(
                offlinePackage = offlinePackage,
                packageDirectory = null,
                errors = listOf("Offline map package is missing files: ${missingFiles.sorted().joinToString(", ")}"),
            )
        }

        if (baseDirectory.exists() && !baseDirectory.isDirectory) {
            return OfflineBaseMapPackageInstallResult(
                offlinePackage = offlinePackage,
                packageDirectory = null,
                errors = listOf("Offline map storage path is not a directory."),
            )
        }
        if (!baseDirectory.exists() && !baseDirectory.mkdirs()) {
            return OfflineBaseMapPackageInstallResult(
                offlinePackage = offlinePackage,
                packageDirectory = null,
                errors = listOf("Offline map storage directory could not be created."),
            )
        }

        val temporaryDirectory = File(baseDirectory, "$packageId.tmp").apply {
            deleteRecursively()
        }
        if (!temporaryDirectory.mkdirs()) {
            return OfflineBaseMapPackageInstallResult(
                offlinePackage = offlinePackage,
                packageDirectory = null,
                errors = listOf("Offline map temporary directory could not be created."),
            )
        }

        val requiredPaths = setOf(
            MissionPackageSchema.OFFLINE_MAP_METADATA_FILE,
            offlinePackage.tileAssetPath,
            offlinePackage.styleAssetPath,
        )
        requiredPaths.forEach { path ->
            val outputFile = File(temporaryDirectory, path)
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(requireNotNull(zipFiles[path]))
        }

        packageDirectory.deleteRecursively()
        if (!temporaryDirectory.renameTo(packageDirectory)) {
            temporaryDirectory.deleteRecursively()
            return OfflineBaseMapPackageInstallResult(
                offlinePackage = offlinePackage,
                packageDirectory = null,
                errors = listOf("Offline map package could not be installed."),
            )
        }

        return OfflineBaseMapPackageInstallResult(
            offlinePackage = offlinePackage,
            packageDirectory = packageDirectory,
            errors = emptyList(),
        )
    }

    private fun decodeZip(zipBytes: ByteArray): Result<Map<String, ByteArray>> {
        return runCatching {
            val files = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInput ->
                var entry = zipInput.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val normalizedName = normalizeEntryName(entry.name)
                        files[normalizedName] = zipInput.readBytes()
                    }
                    zipInput.closeEntry()
                    entry = zipInput.nextEntry
                }
            }
            files.toSortedMap()
        }
    }

    private fun normalizeEntryName(entryName: String): String {
        val normalized = entryName.replace('\\', '/').trim('/')
        require(normalized.isNotBlank()) { "Blank ZIP entry name." }
        require(normalized.split('/').none { segment -> segment.isBlank() || segment == ".." }) {
            "Unsafe ZIP entry path."
        }
        return normalized
    }
}

data class OfflineBaseMapPackageInstallResult(
    val offlinePackage: OfflineBaseMapPackage?,
    val packageDirectory: File?,
    val errors: List<String>,
) {
    val isSuccess: Boolean = errors.isEmpty() && offlinePackage != null && packageDirectory != null
}
