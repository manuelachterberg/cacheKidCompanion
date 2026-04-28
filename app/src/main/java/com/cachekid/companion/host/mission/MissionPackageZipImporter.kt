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
                status = MissionPackageImportStatus.ZIP_DECODE_FAILED,
            )
        }
        if (files.isEmpty()) {
            return MissionPackageImportResult(
                missionDirectory = null,
                missionId = null,
                errors = listOf("Mission package ZIP could not be decoded."),
                status = MissionPackageImportStatus.ZIP_DECODE_FAILED,
            )
        }

        val missionPackage = reader.read(files) ?: return MissionPackageImportResult(
            missionDirectory = null,
            missionId = null,
            errors = listOf("Mission package manifest is missing or invalid."),
            status = MissionPackageImportStatus.MANIFEST_INVALID,
        )

        val validation = validator.validate(missionPackage)
        if (!validation.isValid) {
            return MissionPackageImportResult(
                missionDirectory = null,
                missionId = missionPackage.missionId,
                errors = validation.errors,
                status = MissionPackageImportStatus.VALIDATION_FAILED,
            )
        }

        val storeResult = fileStore.store(baseDirectory, missionPackage)
        return MissionPackageImportResult(
            missionDirectory = storeResult.missionDirectory,
            missionId = missionPackage.missionId,
            errors = storeResult.errors,
            status = if (storeResult.isSuccess) {
                MissionPackageImportStatus.IMPORTED
            } else {
                MissionPackageImportStatus.STORE_FAILED
            },
        )
    }
}
