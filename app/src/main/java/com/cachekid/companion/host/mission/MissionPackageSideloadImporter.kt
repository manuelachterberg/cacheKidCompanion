package com.cachekid.companion.host.mission

import java.io.File

class MissionPackageSideloadImporter(
    private val importer: MissionPackageZipImporter = MissionPackageZipImporter(),
) {

    fun importLatest(
        missionBaseDirectory: File,
        candidateDirectories: List<File>,
    ): MissionPackageSideloadResult {
        val normalizedDirectories = candidateDirectories
            .distinctBy { it.absolutePath }

        val latestPackage = normalizedDirectories
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { directory ->
                directory.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { file ->
                        file.isFile &&
                            file.extension.equals("zip", ignoreCase = true)
                    }
            }
            .maxByOrNull { it.lastModified() }

        if (latestPackage == null) {
            return MissionPackageSideloadResult(
                status = MissionPackageSideloadStatus.NO_PACKAGE_FOUND,
                message = "Kein adb-Sideload-Paket gefunden.",
                searchedDirectories = normalizedDirectories,
            )
        }

        val zipBytes = runCatching { latestPackage.readBytes() }
            .getOrElse { error ->
                val archivedFile = archive(latestPackage, FAILED_DIRECTORY_NAME)
                val errorReport = writeErrorReport(archivedFile ?: latestPackage, error.message ?: "Unbekannter Lesefehler.")
                return MissionPackageSideloadResult(
                    status = MissionPackageSideloadStatus.IMPORT_FAILED,
                    sourceFile = latestPackage,
                    archivedFile = archivedFile,
                    errorReportFile = errorReport,
                    message = "Sideload-Paket konnte nicht gelesen werden: ${latestPackage.name}",
                    errors = listOf(error.message ?: "Unbekannter Lesefehler."),
                    searchedDirectories = normalizedDirectories,
                )
            }

        val importResult = importer.import(missionBaseDirectory, zipBytes)
        return if (importResult.isSuccess) {
            MissionPackageSideloadResult(
                status = MissionPackageSideloadStatus.IMPORTED,
                sourceFile = latestPackage,
                archivedFile = archive(latestPackage, IMPORTED_DIRECTORY_NAME),
                importResult = importResult,
                message = "ADB-Sideload importiert: ${importResult.missionId}",
                searchedDirectories = normalizedDirectories,
            )
        } else {
            val archivedFile = archive(latestPackage, FAILED_DIRECTORY_NAME)
            val errorReport = writeErrorReport(archivedFile ?: latestPackage, importResult.errors.joinToString("\n"))
            MissionPackageSideloadResult(
                status = MissionPackageSideloadStatus.IMPORT_FAILED,
                sourceFile = latestPackage,
                archivedFile = archivedFile,
                errorReportFile = errorReport,
                importResult = importResult,
                message = "ADB-Sideload fehlgeschlagen: ${latestPackage.name}",
                errors = importResult.errors,
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

data class MissionPackageSideloadResult(
    val status: MissionPackageSideloadStatus,
    val sourceFile: File? = null,
    val archivedFile: File? = null,
    val errorReportFile: File? = null,
    val importResult: MissionPackageImportResult? = null,
    val message: String,
    val errors: List<String> = emptyList(),
    val searchedDirectories: List<File> = emptyList(),
)

enum class MissionPackageSideloadStatus {
    NO_PACKAGE_FOUND,
    IMPORTED,
    IMPORT_FAILED,
}
