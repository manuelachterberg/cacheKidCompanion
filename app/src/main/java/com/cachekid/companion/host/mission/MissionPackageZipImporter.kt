package com.cachekid.companion.host.mission

import java.io.File

class MissionPackageZipImporter(
    private val zipCodec: MissionPackageZipCodec = MissionPackageZipCodec(),
    private val reader: MissionPackageReader = MissionPackageReader(),
    private val validator: MissionPackageValidator = MissionPackageValidator(),
    private val fileStore: MissionPackageFileStore = MissionPackageFileStore(),
) {

    fun import(baseDirectory: File, zipBytes: ByteArray): MissionPackageImportResult {
        val files = runCatching { zipCodec.decode(zipBytes) }.getOrElse {
            return MissionPackageImportResult(
                missionDirectory = null,
                missionId = null,
                errors = listOf("Mission package ZIP could not be decoded."),
            )
        }

        val missionPackage = reader.read(files) ?: return MissionPackageImportResult(
            missionDirectory = null,
            missionId = null,
            errors = listOf("Mission package manifest is missing or invalid."),
        )

        val validation = validator.validate(missionPackage)
        if (!validation.isValid) {
            return MissionPackageImportResult(
                missionDirectory = null,
                missionId = missionPackage.missionId,
                errors = validation.errors,
            )
        }

        val storeResult = fileStore.store(baseDirectory, missionPackage)
        return MissionPackageImportResult(
            missionDirectory = storeResult.missionDirectory,
            missionId = missionPackage.missionId,
            errors = storeResult.errors,
        )
    }
}
