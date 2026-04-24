package com.cachekid.companion.data

import java.io.File

class InjectedNavigationInputFileImporter(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun importLatest(candidateDirectories: List<File>): InjectedNavigationInputImportResult {
        val normalizedDirectories = candidateDirectories.distinctBy { it.absolutePath }
        val latestFile = normalizedDirectories
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { directory ->
                directory.listFiles()
                    .orEmpty()
                    .asSequence()
                    .filter { file ->
                        file.isFile &&
                            file.extension.equals("json", ignoreCase = true)
                    }
            }
            .maxByOrNull { it.lastModified() }

        if (latestFile == null) {
            return InjectedNavigationInputImportResult(
                status = InjectedNavigationInputImportStatus.NO_FILE_FOUND,
                message = "Keine adb-Navigationsdatei gefunden.",
                searchedDirectories = normalizedDirectories,
            )
        }

        val jsonText = runCatching { latestFile.readText() }
            .getOrElse { error ->
                val archivedFile = archive(latestFile, FAILED_DIRECTORY_NAME)
                val errorReportFile = writeErrorReport(archivedFile ?: latestFile, error.message ?: "Unbekannter Lesefehler.")
                return InjectedNavigationInputImportResult(
                    status = InjectedNavigationInputImportStatus.IMPORT_FAILED,
                    sourceFile = latestFile,
                    archivedFile = archivedFile,
                    errorReportFile = errorReportFile,
                    message = "Navigationsdatei konnte nicht gelesen werden: ${latestFile.name}",
                    errors = listOf(error.message ?: "Unbekannter Lesefehler."),
                    searchedDirectories = normalizedDirectories,
                )
            }

        val injectedInput = runCatching { parse(jsonText) }
            .getOrElse { error ->
                val archivedFile = archive(latestFile, FAILED_DIRECTORY_NAME)
                val errorReportFile = writeErrorReport(archivedFile ?: latestFile, error.message ?: "Ungueltige Navigationsdaten.")
                return InjectedNavigationInputImportResult(
                    status = InjectedNavigationInputImportStatus.IMPORT_FAILED,
                    sourceFile = latestFile,
                    archivedFile = archivedFile,
                    errorReportFile = errorReportFile,
                    message = "Navigationsdatei ist ungueltig: ${latestFile.name}",
                    errors = listOf(error.message ?: "Ungueltige Navigationsdaten."),
                    searchedDirectories = normalizedDirectories,
                )
            }

        return InjectedNavigationInputImportResult(
            status = InjectedNavigationInputImportStatus.IMPORTED,
            sourceFile = latestFile,
            archivedFile = archive(latestFile, IMPORTED_DIRECTORY_NAME),
            injectedInput = injectedInput,
            message = "ADB-Navigation importiert: ${latestFile.name}",
            searchedDirectories = normalizedDirectories,
        )
    }

    private fun parse(jsonText: String): InjectedNavigationInput {
        val latitude = extractDouble(jsonText, "latitude")
        val longitude = extractDouble(jsonText, "longitude")
        val accuracyMeters = extractDouble(jsonText, "accuracyMeters")?.toFloat()
        val headingDegrees = extractDouble(jsonText, "headingDegrees")?.toFloat()
        val capturedAtEpochMillis = extractLong(jsonText, "capturedAtEpochMillis") ?: clock()

        if (latitude != null && longitude == null || latitude == null && longitude != null) {
            error("latitude und longitude muessen gemeinsam geliefert werden.")
        }
        if (latitude == null && longitude == null && headingDegrees == null) {
            error("Navigationsdatei braucht mindestens Standort oder Heading.")
        }

        return InjectedNavigationInput(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            headingDegrees = headingDegrees,
            capturedAtEpochMillis = capturedAtEpochMillis,
        )
    }

    private fun extractDouble(jsonText: String, fieldName: String): Double? {
        return NUMBER_FIELD_REGEX_TEMPLATE
            .format(Regex.escape(fieldName))
            .toRegex()
            .find(jsonText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
    }

    private fun extractLong(jsonText: String, fieldName: String): Long? {
        return NUMBER_FIELD_REGEX_TEMPLATE
            .format(Regex.escape(fieldName))
            .toRegex()
            .find(jsonText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
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
        private const val NUMBER_FIELD_REGEX_TEMPLATE =
            """"%s"\s*:\s*(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)"""
    }
}

data class InjectedNavigationInputImportResult(
    val status: InjectedNavigationInputImportStatus,
    val sourceFile: File? = null,
    val archivedFile: File? = null,
    val errorReportFile: File? = null,
    val injectedInput: InjectedNavigationInput? = null,
    val message: String,
    val errors: List<String> = emptyList(),
    val searchedDirectories: List<File> = emptyList(),
)

enum class InjectedNavigationInputImportStatus {
    NO_FILE_FOUND,
    IMPORTED,
    IMPORT_FAILED,
}
