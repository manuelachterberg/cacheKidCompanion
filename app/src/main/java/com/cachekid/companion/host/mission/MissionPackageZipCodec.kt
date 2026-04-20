package com.cachekid.companion.host.mission

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MissionPackageZipCodec {

    fun encode(missionPackage: MissionPackage): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipOutput ->
            missionPackage.files.sortedBy { it.path }.forEach { packageFile ->
                zipOutput.putNextEntry(ZipEntry(packageFile.path))
                zipOutput.write(packageFile.content.toByteArray(Charsets.UTF_8))
                zipOutput.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun decode(zipBytes: ByteArray): Map<String, String> {
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    files[entry.name] = zipInput.readBytes().toString(Charsets.UTF_8)
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
        return files.toSortedMap()
    }
}
