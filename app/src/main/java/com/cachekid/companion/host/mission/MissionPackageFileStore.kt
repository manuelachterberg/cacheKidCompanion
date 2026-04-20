package com.cachekid.companion.host.mission

import java.io.File

class MissionPackageFileStore {

    fun store(baseDirectory: File, missionPackage: MissionPackage): MissionPackageStoreResult {
        val expectedFiles = MissionPackageSchema.requiredCoreFiles.toSet()
        val actualFiles = missionPackage.files.map { it.path }.toSet()
        val missingFiles = expectedFiles - actualFiles
        if (missingFiles.isNotEmpty()) {
            return MissionPackageStoreResult(
                missionDirectory = null,
                errors = listOf("Mission package is missing required files: ${missingFiles.sorted().joinToString(", ")}"),
            )
        }

        if (baseDirectory.exists() && !baseDirectory.isDirectory) {
            return MissionPackageStoreResult(
                missionDirectory = null,
                errors = listOf("Base mission storage path is not a directory."),
            )
        }

        if (!baseDirectory.exists() && !baseDirectory.mkdirs()) {
            return MissionPackageStoreResult(
                missionDirectory = null,
                errors = listOf("Base mission storage directory could not be created."),
            )
        }

        val missionDirectory = File(baseDirectory, missionPackage.missionId)
        if (!missionDirectory.exists() && !missionDirectory.mkdirs()) {
            return MissionPackageStoreResult(
                missionDirectory = null,
                errors = listOf("Mission directory could not be created."),
            )
        }

        missionPackage.files.forEach { packageFile ->
            val outputFile = File(missionDirectory, packageFile.path)
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(packageFile.content)
        }

        return MissionPackageStoreResult(
            missionDirectory = missionDirectory,
            errors = emptyList(),
        )
    }
}
