package com.cachekid.companion.shared

/**
 * Parses shared cache content from Geocaching apps.
 * Supports: GC codes, coordinates, URLs
 */
object CacheParser {
    
    private val gcCodePattern = Regex("GC[0-9A-Z]{1,5}", RegexOption.IGNORE_CASE)
    // Pattern for decimal minutes: N 52° 31.234 E 13° 24.567
    private val coordPattern = Regex("""N?\s*(\d+)[°\s]+(\d+)[.](\d+)\s*[']?\s*[N]?[,\s]+E?\s*(\d+)[°\s]+(\d+)[.](\d+)\s*[']?\s*[E]?""")
    
    fun parse(sharedText: String): ActiveMission? {
        val gcCode = gcCodePattern.find(sharedText)?.value ?: "GC${(1000..99999).random()}"
        
        // Try to extract coordinates from text
        val coords = extractCoordinates(sharedText)
        
        return ActiveMission(
            missionId = generateMissionId(),
            cacheCode = gcCode.uppercase(),
            sourceTitle = "Imported Cache",
            childTitle = gcCode.uppercase(),
            summary = sharedText.take(200),
            target = coords ?: MissionTarget(52.52, 13.405), // Berlin fallback
        )
    }
    
    private fun extractCoordinates(text: String): MissionTarget? {
        val match = coordPattern.find(text) ?: return null
        
        return try {
            val latDeg = match.groupValues[1].toDouble()
            val latMin = match.groupValues[2].toDouble()
            val latSec = match.groupValues[3].toDouble()
            val lonDeg = match.groupValues[4].toDouble()
            val lonMin = match.groupValues[5].toDouble()
            val lonSec = match.groupValues[6].toDouble()
            
            // Decimal minutes: deg + min.dec / 60
            val latMinDecimal = latMin + latSec / 1000.0
            val lonMinDecimal = lonMin + lonSec / 1000.0
            val lat = latDeg + latMinDecimal / 60.0
            val lon = lonDeg + lonMinDecimal / 60.0
            
            MissionTarget(lat, lon)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun generateMissionId(): String {
        return "mission-${getTimestamp()}"
    }
    
    private fun getTimestamp(): Long {
        // Platform-specific time source
        return 0L
    }
}
