package com.cachekid.companion.host.importing

import com.cachekid.companion.host.mission.MissionTarget
import kotlin.math.abs

class SharedCacheParser {

    private val cacheCodeRegex = Regex("""\bGC[A-Z0-9]+\b""", RegexOption.IGNORE_CASE)
    private val coordInfoRegex = Regex(
        """https?://(?:www\.)?coord\.info/(GC[A-Z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val decimalCoordinateRegex = Regex(
        """(-?\d{1,2}\.\d{4,})\s*,\s*(-?\d{1,3}\.\d{4,})""",
    )
    private val directionalCoordinateRegex = Regex(
        """([NS])\s*(\d{1,2})[°\s]+(\d{1,2}\.\d+)\s*[, ]+\s*([EW])\s*(\d{1,3})[°\s]+(\d{1,2}\.\d+)""",
        setOf(RegexOption.IGNORE_CASE),
    )

    fun parse(sharedText: String, sourceApp: String? = null): SharedCacheImportResult {
        val normalizedText = sharedText.trim()
        if (normalizedText.isBlank()) {
            return SharedCacheImportResult(
                status = SharedCacheImportStatus.INVALID,
                value = null,
                messages = listOf("Shared cache payload is empty."),
            )
        }

        val cacheCode = extractCacheCode(normalizedText)
        val title = extractTitle(normalizedText, cacheCode)
        val target = extractTarget(normalizedText)

        val import = SharedCacheImport(
            rawText = normalizedText,
            cacheCode = cacheCode,
            sourceTitle = title,
            target = target,
            sourceApp = sourceApp,
        )

        val messages = buildList {
            if (cacheCode == null) {
                add("Cache code could not be detected.")
            }
            if (title == null) {
                add("Cache title could not be detected.")
            }
            if (target == null) {
                add("Target coordinates could not be detected.")
            }
        }

        val status = when {
            messages.isEmpty() -> SharedCacheImportStatus.SUCCESS
            cacheCode != null || title != null || target != null -> SharedCacheImportStatus.PARTIAL
            else -> SharedCacheImportStatus.INVALID
        }

        return SharedCacheImportResult(
            status = status,
            value = import.takeIf { status != SharedCacheImportStatus.INVALID },
            messages = messages,
        )
    }

    private fun extractCacheCode(text: String): String? {
        coordInfoRegex.find(text)?.let { match ->
            return match.groupValues[1].uppercase()
        }

        return cacheCodeRegex.find(text)?.value?.uppercase()
    }

    private fun extractTitle(text: String, cacheCode: String?): String? {
        val lines = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("http://") || it.startsWith("https://") }
            .filterNot { line -> cacheCode != null && line.equals(cacheCode, ignoreCase = true) }
            .toList()

        return lines.firstOrNull()
    }

    private fun extractTarget(text: String): MissionTarget? {
        decimalCoordinateRegex.find(text)?.let { match ->
            val latitude = match.groupValues[1].toDoubleOrNull()
            val longitude = match.groupValues[2].toDoubleOrNull()
            if (latitude != null && longitude != null) {
                return MissionTarget(latitude = latitude, longitude = longitude)
                    .takeIf { it.isValid() }
            }
        }

        directionalCoordinateRegex.find(text)?.let { match ->
            val latitude = directionalToDecimal(
                direction = match.groupValues[1],
                degrees = match.groupValues[2].toDoubleOrNull(),
                minutes = match.groupValues[3].toDoubleOrNull(),
            )
            val longitude = directionalToDecimal(
                direction = match.groupValues[4],
                degrees = match.groupValues[5].toDoubleOrNull(),
                minutes = match.groupValues[6].toDoubleOrNull(),
            )
            if (latitude != null && longitude != null) {
                return MissionTarget(latitude = latitude, longitude = longitude)
                    .takeIf { it.isValid() }
            }
        }

        return null
    }

    private fun directionalToDecimal(
        direction: String,
        degrees: Double?,
        minutes: Double?,
    ): Double? {
        if (degrees == null || minutes == null) {
            return null
        }

        val absolute = abs(degrees) + (minutes / 60.0)
        return when (direction.uppercase()) {
            "N", "E" -> absolute
            "S", "W" -> -absolute
            else -> null
        }
    }
}
