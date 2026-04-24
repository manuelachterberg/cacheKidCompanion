package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionDraftValidatorTest {

    private val validator = MissionDraftValidator()

    @Test
    fun `valid mission draft passes validation`() {
        val result = validator.validate(
            MissionDraft(
                cacheCode = "GC12345",
                sourceTitle = "Old Oak Cache",
                childTitle = "Der Schatz im Wald",
                summary = "Folge der Karte bis zum grossen X.",
                target = MissionTarget(
                    latitude = 52.520008,
                    longitude = 13.404954,
                ),
                sourceApp = "geocaching",
            ),
        )

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `blank fields are all reported`() {
        val result = validator.validate(
            MissionDraft(
                cacheCode = "",
                sourceTitle = "",
                childTitle = "",
                summary = "",
                target = MissionTarget(
                    latitude = 52.520008,
                    longitude = 13.404954,
                ),
            ),
        )

        assertFalse(result.isValid)
        assertEquals(4, result.errors.size)
    }

    @Test
    fun `invalid coordinates fail validation`() {
        val result = validator.validate(
            MissionDraft(
                cacheCode = "GC12345",
                sourceTitle = "Cache",
                childTitle = "Schatz",
                summary = "Summary",
                target = MissionTarget(
                    latitude = 120.0,
                    longitude = 200.0,
                ),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Target coordinates") })
    }

    @Test
    fun `invalid waypoint coordinates fail validation`() {
        val result = validator.validate(
            MissionDraft(
                cacheCode = "GC12345",
                sourceTitle = "Cache",
                childTitle = "Schatz",
                summary = "Summary",
                target = MissionTarget(
                    latitude = 52.0,
                    longitude = 13.0,
                ),
                waypoints = listOf(
                    MissionWaypoint(
                        latitude = 120.0,
                        longitude = 13.0,
                        label = "Kaputt",
                    ),
                ),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Waypoint coordinates") })
    }
}
