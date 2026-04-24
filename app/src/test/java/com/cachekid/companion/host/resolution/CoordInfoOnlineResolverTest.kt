package com.cachekid.companion.host.resolution

import com.cachekid.companion.host.importing.SharedCacheImport
import com.cachekid.companion.host.mission.MissionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordInfoOnlineResolverTest {

    @Test
    fun `resolver uses exact coordinates when page exposes them`() {
        val resolver = CoordInfoOnlineResolver(
            pageFetcher = object : CoordInfoPageFetcher {
                override fun fetch(url: String): CoordInfoPage {
                    return CoordInfoPage(
                        html = """
                        <html>
                          <head>
                            <meta property="og:title" content="Old Oak Cache" />
                          </head>
                          <body>
                            <span id="ctl00_ContentBody_CacheName">Old Oak Cache</span>
                            <div>N 52° 31.201 E 013° 24.297</div>
                          </body>
                        </html>
                    """.trimIndent(),
                        source = CoordInfoPageSource.AUTHENTICATED,
                    )
                }
            },
            geocoder = object : PublicLocationGeocoder {
                override fun geocode(query: String): MissionTarget? = null
            },
        )

        val result = resolver.resolve(
            SharedCacheImport(
                rawText = "https://coord.info/GC12345",
                cacheCode = "GC12345",
                sourceTitle = null,
                target = null,
                sourceApp = "geocaching",
            ),
        )

        assertNotNull(result)
        assertEquals(CacheResolutionStatus.RESOLVED, result?.status)
        assertEquals("Old Oak Cache", result?.value?.title)
        assertEquals(52.52001666666667, result?.value?.target?.latitude ?: 0.0, 0.000001)
        assertEquals(13.40495, result?.value?.target?.longitude ?: 0.0, 0.000001)
        assertTrue(result?.messages?.firstOrNull()?.contains("eingeloggten") == true)
    }

    @Test
    fun `resolver falls back to public geocoding when coordinates are not public`() {
        var capturedQuery: String? = null
        val resolver = CoordInfoOnlineResolver(
            pageFetcher = object : CoordInfoPageFetcher {
                override fun fetch(url: String): CoordInfoPage {
                    return CoordInfoPage(
                        html = """
                        <html>
                          <head>
                            <meta property="og:title" content="Allerbogen" />
                            <meta name="description" content="Allerbogen (GC7NXFT) was created by jeng4aaaaa. Der Allerbogen in Celle liegt im Postleitzahlengebiet 29223 und hat eine Länge von rund 140 Metern." />
                          </head>
                          <body>
                            <span id="ctl00_ContentBody_CacheName">Allerbogen</span>
                          </body>
                        </html>
                    """.trimIndent(),
                        source = CoordInfoPageSource.ANONYMOUS,
                    )
                }
            },
            geocoder = object : PublicLocationGeocoder {
                override fun geocode(query: String): MissionTarget? {
                    capturedQuery = query
                    return MissionTarget(52.6302913, 10.0685275)
                }
            },
        )

        val result = resolver.resolve(
            SharedCacheImport(
                rawText = "https://coord.info/GC7NXFT",
                cacheCode = "GC7NXFT",
                sourceTitle = null,
                target = null,
                sourceApp = "geocaching",
            ),
        )

        assertNotNull(result)
        assertEquals(CacheResolutionStatus.RESOLVED, result?.status)
        assertEquals("Allerbogen", result?.value?.title)
        assertEquals(52.6302913, result?.value?.target?.latitude ?: 0.0, 0.000001)
        assertEquals(10.0685275, result?.value?.target?.longitude ?: 0.0, 0.000001)
        assertTrue(capturedQuery?.contains("29223 Celle") == true)
        assertTrue(result?.messages?.any { it.contains("naeherungsweise") } == true)
    }

    @Test
    fun `resolver ignores bogus decimal coordinates from svg path data`() {
        val queries = mutableListOf<String>()
        val resolver = CoordInfoOnlineResolver(
            pageFetcher = object : CoordInfoPageFetcher {
                override fun fetch(url: String): CoordInfoPage {
                    return CoordInfoPage(
                        html = """
                        <html>
                          <head>
                            <meta property="og:title" content="Allerbogen" />
                            <meta name="description" content="Allerbogen (GC7NXFT) was created by jeng4aaaaa. Der Allerbogen in Celle liegt im Postleitzahlengebiet 29223 und hat eine Länge von rund 140 Metern." />
                          </head>
                          <body>
                            <path d="M1.98946751,1.37469233 L15.0220577,1.37469233"></path>
                            <span id="ctl00_ContentBody_CacheName">Allerbogen</span>
                          </body>
                        </html>
                    """.trimIndent(),
                        source = CoordInfoPageSource.ANONYMOUS,
                    )
                }
            },
            geocoder = object : PublicLocationGeocoder {
                override fun geocode(query: String): MissionTarget? {
                    queries += query
                    return if (query.contains("Allerbogen, 29223 Celle")) {
                        MissionTarget(52.6302913, 10.0685275)
                    } else {
                        null
                    }
                }
            },
        )

        val result = resolver.resolve(
            SharedCacheImport(
                rawText = "https://coord.info/GC7NXFT",
                cacheCode = "GC7NXFT",
                sourceTitle = null,
                target = null,
                sourceApp = "geocaching",
            ),
        )

        assertNotNull(result)
        assertEquals(CacheResolutionStatus.RESOLVED, result?.status)
        assertEquals(52.6302913, result?.value?.target?.latitude ?: 0.0, 0.000001)
        assertEquals(10.0685275, result?.value?.target?.longitude ?: 0.0, 0.000001)
        assertTrue(queries.any { it.contains("Allerbogen, 29223 Celle") })
    }

    @Test
    fun `authenticated fetcher falls back to anonymous when no session is available`() {
        val fetcher = AuthenticatedCoordInfoPageFetcher(
            sessionStore = object : GeocachingSessionStore {
                override fun getCookieHeader(): String? = null
            },
            anonymousFallback = object : CoordInfoPageFetcher {
                override fun fetch(url: String): CoordInfoPage {
                    return CoordInfoPage(
                        html = "<html><body>anonymous</body></html>",
                        source = CoordInfoPageSource.ANONYMOUS,
                    )
                }
            },
        )

        val result = fetcher.fetch("https://coord.info/GC12345")

        assertNotNull(result)
        assertEquals(CoordInfoPageSource.ANONYMOUS, result?.source)
    }
}
