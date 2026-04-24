package com.cachekid.companion.host.mission

import java.io.File

class OfflineBaseMapPackageSideloadImporter(
    private val installer: OfflineBaseMapPackageZipInstaller = OfflineBaseMapPackageZipInstaller(),
) {

    fun importLatest(
        offlineMapBaseDirectory: File,
        candidateDirectories: List<File>,
    ): OfflineBaseMapPackageSideloadResult {
        val normalizedDirectories = candidateDirectories.distinctBy { it.absolutePath }
        val latestPackage = normalizedDirectories
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { directory ->
                directory.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { file -> file.isFile && file.extension.equals("zip", ignoreCase = true) }
            }
            .maxByOrNull { it.lastModified() }

        if (latestPackage == null) {
            return OfflineBaseMapPackageSideloadResult(
                status = OfflineBaseMapPackageSideloadStatus.NO_PACKAGE_FOUND,
                message = "Kein Offline-Kartenpaket gefunden.",
                searchedDirectories = normalizedDirectories,
            )
        }

        val zipBytes = runCatching { latestPackage.readBytes() }.getOrElse { error ->
            val archivedFile = archive(latestPackage, FAILED_DIRECTORY_NAME)
            val errorReport = writeErrorReport(archivedFile ?: latestPackage, error.message ?: "Unbekannter Lesefehler.")
            return OfflineBaseMapPackageSideloadResult(
                status = OfflineBaseMapPackageSideloadStatus.IMPORT_FAILED,
                sourceFile = latestPackage,
                archivedFile = archivedFile,
                errorReportFile = errorReport,
                message = "Offline-Kartenpaket konnte nicht gelesen werden: ${latestPackage.name}",
                errors = listOf(error.message ?: "Unbekannter Lesefehler."),
                searchedDirectories = normalizedDirectories,
            )
        }

        val installResult = installer.install(offlineMapBaseDirectory, zipBytes)
        return if (installResult.isSuccess) {
            OfflineBaseMapPackageSideloadResult(
                status = OfflineBaseMapPackageSideloadStatus.IMPORTED,
                sourceFile = latestPackage,
                archivedFile = archive(latestPackage, IMPORTED_DIRECTORY_NAME),
                installResult = installResult,
                message = "Offline-Karte importiert: ${installResult.offlinePackage?.displayName}",
                searchedDirectories = normalizedDirectories,
            )
        } else {
            val archivedFile = archive(latestPackage, FAILED_DIRECTORY_NAME)
            val errorReport = writeErrorReport(archivedFile ?: latestPackage, installResult.errors.joinToString("\n"))
            OfflineBaseMapPackageSideloadResult(
                status = OfflineBaseMapPackageSideloadStatus.IMPORT_FAILED,
                sourceFile = latestPackage,
                archivedFile = archivedFile,
                errorReportFile = errorReport,
                installResult = installResult,
                message = "Offline-Kartenimport fehlgeschlagen: ${latestPackage.name}",
                errors = installResult.errors,
                searchedDirectories = normalizedDirectories,
            )
        }
    }

    private fun archive(sourceFile: File, archiveDirectoryName: String): File? {
        val archiveDirectory = File(sourceFile.parentFile, archiveDirectoryName)
        archiveDirectory.mkdirs()
        val archivedFile = uniqueArchiveFile(archiveDirectory, sourceFile)
        if (sourceFile.renameTo(archivedFile)) {
            return archivedFile
        }

        return runCatching {
            sourceFile.copyTo(target = archivedFile, overwrite = false)
            if (!sourceFile.delete()) {
                archivedFile.delete()
                null
            } else {
                archivedFile
            }
        }.getOrNull()
    }

    private fun uniqueArchiveFile(archiveDirectory: File, sourceFile: File): File {
        val baseName = sourceFile.nameWithoutExtension
        val extension = sourceFile.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var attempt = 0
        while (true) {
            val suffix = if (attempt == 0) "" else "-$attempt"
            val candidate = File(archiveDirectory, "$baseName$suffix$extension")
            if (!candidate.exists()) {
                return candidate
            }
            attempt += 1
        }
    }

    private fun writeErrorReport(referenceFile: File, errorText: String): File? {
        val reportFile = File(referenceFile.parentFile, "${referenceFile.name}.error.txt")
        return runCatching {
            reportFile.writeText(errorText)
            reportFile
        }.getOrNull()
    }

    companion object {
        const val IMPORTED_DIRECTORY_NAME = "imported"
        const val FAILED_DIRECTORY_NAME = "failed"
    }
}

data class OfflineBaseMapPackageSideloadResult(
    val status: OfflineBaseMapPackageSideloadStatus,
    val sourceFile: File? = null,
    val archivedFile: File? = null,
    val errorReportFile: File? = null,
    val installResult: OfflineBaseMapPackageInstallResult? = null,
    val message: String,
    val errors: List<String> = emptyList(),
    val searchedDirectories: List<File> = emptyList(),
)

enum class OfflineBaseMapPackageSideloadStatus {
    NO_PACKAGE_FOUND,
    IMPORTED,
    IMPORT_FAILED,
}
