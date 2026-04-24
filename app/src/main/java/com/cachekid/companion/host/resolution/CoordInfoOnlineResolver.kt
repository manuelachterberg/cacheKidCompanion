package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.importing.SharedCacheImport
import com.cachekid.companion.host.mission.MissionTarget
import java.net.HttpURLConnection
import java.net.URL

class CoordInfoOnlineResolver(
    private val pageFetcher: CoordInfoPageFetcher = AuthenticatedCoordInfoPageFetcher(),
    private val geocoder: PublicLocationGeocoder = NominatimLocationGeocoder(),
) {

    private val coordInfoRegex = Regex(
        """https?://(?:www\.)?coord\.info/(GC[A-Z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val ogTitleRegex = Regex(
        """<meta[^>]+property="og:title"[^>]+content="([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val cacheNameRegex = Regex(
        """<span[^>]+id="ctl00_ContentBody_CacheName"[^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val titleTagRegex = Regex(
        """<title>\s*(.*?)\s*</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val metaDescriptionRegex = Regex(
        """<meta[^>]+name="description"[^>]+content="([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )
    private val longDescriptionRegex = Regex(
        """<span[^>]+id="ctl00_ContentBody_LongDescription"[^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val decimalCoordinateRegex = Regex(
        """(-?\d{1,2}\.\d{4,})\s*,\s*(-?\d{1,3}\.\d{4,})""",
    )
    private val directionalCoordinateRegex = Regex(
        """([NS])\s*(\d{1,2})[°\s]+(\d{1,2}\.\d+)\s*[, ]+\s*([EW])\s*(\d{1,3})[°\s]+(\d{1,2}\.\d+)""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val streetAndPostalRegex = Regex(
        """\b([A-ZÄÖÜ][A-Za-zÄÖÜäöüß0-9.\- ]{2,})\s+in\s+([A-ZÄÖÜ][A-Za-zÄÖÜäöüß.\- ]+?)\s+liegt\s+im\s+Postleitzahlengebiet\s+(\d{5})""",
        RegexOption.IGNORE_CASE,
    )
    private val postalCodeRegex = Regex("""\b(\d{5})\b""")
    private val cityRegex = Regex(
        """\bin\s+([A-ZÄÖÜ][A-Za-zÄÖÜäöüß.\- ]{1,40})\b""",
        RegexOption.IGNORE_CASE,
    )

    fun resolve(import: SharedCacheImport, prefetchedPage: CoordInfoPage? = null): CacheResolutionResult? {
        val resolvedUrl = buildCoordInfoUrl(import) ?: return null
        val fetchedPage = prefetchedPage ?: pageFetcher.fetch(resolvedUrl) ?: return null
        val html = fetchedPage.html
        val title = extractTitle(html) ?: import.sourceTitle ?: import.cacheCode ?: return null

        extractExactTarget(html)?.let { target ->
            return CacheResolutionResult(
                status = CacheResolutionStatus.RESOLVED,
                value = ResolvedCacheDetails(
                    cacheCode = import.cacheCode ?: extractCacheCode(resolvedUrl) ?: return null,
                    title = title,
                    target = target,
                    sourceApp = import.sourceApp,
                ),
                cacheCodeHint = import.cacheCode,
                messages = listOf(
                    if (fetchedPage.source == CoordInfoPageSource.AUTHENTICATED) {
                        "Cache online aufgeloest. Zielkoordinate wurde aus der eingeloggten Cache-Seite uebernommen."
                    } else {
                        "Cache online aufgeloest. Zielkoordinate wurde aus der Cache-Seite uebernommen."
                    },
                ),
                debugInfo = buildDebugInfo(
                    source = fetchedPage.source,
                    title = title,
                    html = html,
                    locationQueries = emptyList(),
                    resolvedTarget = target,
                ),
            )
        }

        val publicText = extractPublicDescription(html)
        val locationQueries = buildLocationQueries(title, publicText)
        val approximatedTarget = locationQueries.asSequence()
            .mapNotNull { query -> geocoder.geocode(query) }
            .filter { target -> looksLikeGermany(target) }
            .firstOrNull()

        if (approximatedTarget != null) {
            return CacheResolutionResult(
                status = CacheResolutionStatus.RESOLVED,
                value = ResolvedCacheDetails(
                    cacheCode = import.cacheCode ?: extractCacheCode(resolvedUrl) ?: return null,
                    title = title,
                    target = approximatedTarget,
                    sourceApp = import.sourceApp,
                ),
                cacheCodeHint = import.cacheCode,
                messages = listOf("Cache online aufgeloest. Zielkoordinate wurde naeherungsweise aus oeffentlichen Ortsdaten bestimmt."),
                debugInfo = buildDebugInfo(
                    source = fetchedPage.source,
                    title = title,
                    html = html,
                    locationQueries = locationQueries,
                    resolvedTarget = approximatedTarget,
                ),
            )
        }

        return CacheResolutionResult(
            status = CacheResolutionStatus.NEEDS_ONLINE_RESOLUTION,
            value = null,
            cacheCodeHint = import.cacheCode,
            messages = listOf(
                if (fetchedPage.source == CoordInfoPageSource.AUTHENTICATED) {
                    buildAuthenticatedFailureMessage(html)
                } else {
                    "Cache-Seite wurde geladen, aber die exakten Koordinaten sind oeffentlich nicht sichtbar."
                },
                "Fuer eine genaue Zielkoordinate ist weiterhin ein authentifizierter Host oder eine manuelle Eingabe noetig.",
            ),
            debugInfo = buildDebugInfo(
                source = fetchedPage.source,
                title = title,
                html = html,
                locationQueries = locationQueries,
                resolvedTarget = null,
            ),
        )
    }

    fun buildCoordInfoUrl(import: SharedCacheImport): String? {
        return coordInfoRegex.find(import.rawText)?.value
            ?: import.cacheCode?.let { "https://coord.info/${it.uppercase()}" }
    }

    private fun extractCacheCode(coordInfoUrl: String): String? {
        return coordInfoRegex.find(coordInfoUrl)?.groupValues?.getOrNull(1)?.uppercase()
    }

    private fun extractTitle(html: String): String? {
        val encodedTitle = ogTitleRegex.find(html)?.groupValues?.getOrNull(1)
            ?: cacheNameRegex.find(html)?.groupValues?.getOrNull(1)
            ?: titleTagRegex.find(html)?.groupValues?.getOrNull(1)
        return encodedTitle
            ?.let(::decodeHtml)
            ?.let(::stripTags)
            ?.trim()
            ?.substringBefore(" (Traditional Cache)")
            ?.substringBefore(" (Mystery Cache)")
            ?.substringBefore(" (Multi-cache)")
            ?.substringBefore(" (Letterbox Hybrid)")
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractExactTarget(html: String): MissionTarget? {
        val decoded = decodeHtml(html)
        directionalCoordinateRegex.findAll(decoded).forEach { match ->
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
                val target = MissionTarget(latitude, longitude)
                if (target.isValid() && looksLikeGermany(target)) {
                    return target
                }
            }
        }

        decimalCoordinateRegex.findAll(decoded).forEach { match ->
            val latitude = match.groupValues[1].toDoubleOrNull()
            val longitude = match.groupValues[2].toDoubleOrNull()
            if (latitude != null && longitude != null) {
                val target = MissionTarget(latitude, longitude)
                if (target.isValid() && looksLikeGermany(target)) {
                    return target
                }
            }
        }

        return null
    }

    private fun extractPublicDescription(html: String): String {
        val description = metaDescriptionRegex.find(html)?.groupValues?.getOrNull(1)
            ?: longDescriptionRegex.find(html)?.groupValues?.getOrNull(1)
            ?: ""
        return stripTags(decodeHtml(description))
    }

    private fun buildLocationQueries(title: String, publicDescription: String): List<String> {
        val normalizedTitle = title.trim()
        val simplifiedTitle = simplifyPlaceName(normalizedTitle)
        val trimmedDescription = publicDescription.replace(Regex("""\s+"""), " ").trim()
        val directMatch = streetAndPostalRegex.find(trimmedDescription)
        if (directMatch != null) {
            val street = directMatch.groupValues[1].trim()
            val simplifiedStreet = simplifyPlaceName(street)
            val city = directMatch.groupValues[2].trim()
            val postalCode = directMatch.groupValues[3].trim()
            return listOf(
                "$street, $postalCode $city",
                "$simplifiedStreet, $postalCode $city",
                "$street, $postalCode $city, Deutschland",
                "$simplifiedStreet, $postalCode $city, Deutschland",
                "$normalizedTitle, $postalCode $city",
                "$simplifiedTitle, $postalCode $city",
                "$normalizedTitle, $postalCode $city, Deutschland",
                "$simplifiedTitle, $postalCode $city, Deutschland",
                "$street, $city",
                "$simplifiedStreet, $city",
                "$street, $city, Deutschland",
                "$simplifiedStreet, $city, Deutschland",
            ).distinct()
        }

        val postalCode = postalCodeRegex.find(trimmedDescription)?.groupValues?.getOrNull(1)
        val city = cityRegex.find(trimmedDescription)?.groupValues?.getOrNull(1)?.trim()

        return buildList {
            if (postalCode != null && city != null) {
                add("$normalizedTitle, $postalCode $city")
                add("$simplifiedTitle, $postalCode $city")
                add("$normalizedTitle, $postalCode $city, Deutschland")
                add("$simplifiedTitle, $postalCode $city, Deutschland")
            }
            if (city != null) {
                add("$normalizedTitle, $city")
                add("$simplifiedTitle, $city")
                add("$normalizedTitle, $city, Deutschland")
                add("$simplifiedTitle, $city, Deutschland")
            }
            if (postalCode != null) {
                add("$normalizedTitle, $postalCode")
                add("$simplifiedTitle, $postalCode")
                add("$normalizedTitle, $postalCode, Deutschland")
                add("$simplifiedTitle, $postalCode, Deutschland")
            }
        }.distinct()
    }

    private fun simplifyPlaceName(value: String): String {
        return value
            .replace(
                Regex("""^(der|die|das|the)\s+""", RegexOption.IGNORE_CASE),
                "",
            )
            .trim()
    }

    private fun buildAuthenticatedFailureMessage(html: String): String {
        val normalized = html.replace(Regex("""\s+"""), " ")
        return when {
            normalized.contains("Join now to view geocache location details", ignoreCase = true) ->
                "Eingeloggte Seite zeigt weiter den gesperrten Koordinatenblock."
            normalized.contains("Sign up", ignoreCase = true) &&
                normalized.contains("geocache location details", ignoreCase = true) ->
                "Eingeloggte Seite wirkt weiterhin wie eine nicht freigeschaltete Detailansicht."
            normalized.contains("UTM:", ignoreCase = true) ||
                normalized.contains("coord-info", ignoreCase = true) ||
                normalized.contains("coordinate", ignoreCase = true) ->
                "Eingeloggte Seite enthaelt einen Orts-/Koordinatenbereich, aber unser Parser trifft ihn noch nicht."
            else ->
                "Cache-Seite wurde geladen, aber die exakten Koordinaten konnten auch eingeloggt nicht gelesen werden."
        }
    }

    private fun buildDebugInfo(
        source: CoordInfoPageSource,
        title: String,
        html: String,
        locationQueries: List<String>,
        resolvedTarget: MissionTarget?,
    ): String {
        val normalized = html.replace(Regex("""\s+"""), " ")
        val joinNowBlocked = normalized.contains("Join now to view geocache location details", ignoreCase = true)
        val hasUtm = normalized.contains("UTM:", ignoreCase = true)
        val hasDirectionalCoords = directionalCoordinateRegex.containsMatchIn(normalized)
        val hasDecimalCoords = decimalCoordinateRegex.containsMatchIn(normalized)
        val renderedTextSnippet = normalized.take(400)
        val targetText = resolvedTarget?.let { "${it.latitude},${it.longitude}" } ?: "--"
        val queriesText = if (locationQueries.isEmpty()) "--" else locationQueries.joinToString(" || ")
        return buildString {
            append("source=")
            append(source.name)
            append(" | title=")
            append(title)
            append(" | target=")
            append(targetText)
            append(" | blocked=")
            append(joinNowBlocked)
            append(" | hasUtm=")
            append(hasUtm)
            append(" | hasDirectionalCoords=")
            append(hasDirectionalCoords)
            append(" | hasDecimalCoords=")
            append(hasDecimalCoords)
            append(" | queries=")
            append(queriesText)
            append(" | snippet=")
            append(renderedTextSnippet)
        }
    }

    private fun stripTags(value: String): String {
        return value
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun decodeHtml(value: String): String {
        return value
            .replace("&#13;", "\n")
            .replace("&#10;", "\n")
            .replace("&#32;", " ")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
    }

    private fun directionalToDecimal(
        direction: String,
        degrees: Double?,
        minutes: Double?,
    ): Double? {
        if (degrees == null || minutes == null) {
            return null
        }

        val absolute = kotlin.math.abs(degrees) + (minutes / 60.0)
        return when (direction.uppercase()) {
            "N", "E" -> absolute
            "S", "W" -> -absolute
            else -> null
        }
    }

    private fun looksLikeGermany(target: MissionTarget): Boolean {
        return target.latitude in 47.0..56.5 && target.longitude in 5.0..16.5
    }
}

data class CoordInfoPage(
    val html: String,
    val source: CoordInfoPageSource,
)

enum class CoordInfoPageSource {
    AUTHENTICATED,
    ANONYMOUS,
}

interface CoordInfoPageFetcher {
    fun fetch(url: String): CoordInfoPage?
}

interface GeocachingSessionStore {
    fun getCookieHeader(): String?
}

class EmptyGeocachingSessionStore : GeocachingSessionStore {
    override fun getCookieHeader(): String? = null
}

class AnonymousCoordInfoPageFetcher : CoordInfoPageFetcher {
    override fun fetch(url: String): CoordInfoPage? {
        val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "CacheKidCompanion/1.0 (+https://github.com/manuelachterberg/cacheKidCompanion)",
            )
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            CoordInfoPage(
                html = html,
                source = CoordInfoPageSource.ANONYMOUS,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

class AuthenticatedCoordInfoPageFetcher(
    private val sessionStore: GeocachingSessionStore = EmptyGeocachingSessionStore(),
    private val anonymousFallback: CoordInfoPageFetcher = AnonymousCoordInfoPageFetcher(),
) : CoordInfoPageFetcher {

    override fun fetch(url: String): CoordInfoPage? {
        fetchAuthenticated(url)?.let { return it }
        return anonymousFallback.fetch(url)
    }

    private fun fetchAuthenticated(url: String): CoordInfoPage? {
        val cookieHeader = sessionStore.getCookieHeader()?.takeIf { it.isNotBlank() } ?: return null
        var currentUrl = url
        repeat(MAX_REDIRECTS) {
            val connection = URL(currentUrl).openConnection() as? HttpURLConnection ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.setRequestProperty(
                    "User-Agent",
                    "CacheKidCompanion/1.0 (+https://github.com/manuelachterberg/cacheKidCompanion)",
                )
                connection.setRequestProperty("Cookie", cookieHeader)

                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    currentUrl = URL(URL(currentUrl), location).toString()
                    return@repeat
                }

                if (responseCode !in 200..299) {
                    return null
                }

                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return CoordInfoPage(
                    html = html,
                    source = CoordInfoPageSource.AUTHENTICATED,
                )
            } catch (_: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private companion object {
        const val MAX_REDIRECTS = 5
    }
}

interface PublicLocationGeocoder {
    fun geocode(query: String): MissionTarget?
}

class NominatimLocationGeocoder : PublicLocationGeocoder {
    private val latRegex = Regex(""""lat"\s*:\s*"([^"]+)"""")
    private val lonRegex = Regex(""""lon"\s*:\s*"([^"]+)"""")

    override fun geocode(query: String): MissionTarget? {
        val encodedQuery = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=jsonv2&limit=1&countrycodes=de"
        val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "CacheKidCompanion/1.0 (+https://github.com/manuelachterberg/cacheKidCompanion)",
            )
            connection.setRequestProperty("Accept-Language", "de")
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lat = latRegex.find(payload)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val lon = lonRegex.find(payload)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lon != null) {
                MissionTarget(lat, lon).takeIf { it.isValid() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
