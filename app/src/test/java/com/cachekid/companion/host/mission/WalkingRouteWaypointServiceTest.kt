package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkingRouteWaypointServiceTest {

    @Test
    fun `service parses interior route points as mission waypoints`() {
        val service = WalkingRouteWaypointService(
            responseFetcher = {
                """
                {
                  "routes": [
                    {
                      "geometry": {
                        "coordinates": [
                          [13.4000, 52.5000],
                          [13.4010, 52.5010],
                          [13.4020, 52.5020],
                          [13.4030, 52.5030]
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent()
            },
        )

        val waypoints = service.buildWaypoints(
            origin = MissionTarget(52.5000, 13.4000),
            target = MissionTarget(52.5030, 13.4030),
        )

        assertEquals(2, waypoints.size)
        assertEquals(52.5010, waypoints[0].latitude, 0.000001)
        assertEquals(13.4020, waypoints[1].longitude, 0.000001)
    }

    @Test
    fun `service limits large routes to sampled waypoint set`() {
        val service = WalkingRouteWaypointService(
            responseFetcher = {
                buildString {
                    append("""{"routes":[{"geometry":{"coordinates":[""")
                    val coordinates = (0..40).joinToString(",") { index ->
                        val longitude = 13.0 + (index * 0.001)
                        val latitude = 52.0 + (index * 0.001)
                        "[$longitude,$latitude]"
                    }
                    append(coordinates)
                    append("]}}]}")
                }
            },
            maxWaypoints = 5,
        )

        val waypoints = service.buildWaypoints(
            origin = MissionTarget(52.0, 13.0),
            target = MissionTarget(52.040, 13.040),
        )

        assertEquals(5, waypoints.size)
        assertTrue(waypoints.first().latitude > 52.0)
        assertTrue(waypoints.last().latitude < 52.040)
    }

    @Test
    fun `service ignores later coordinates arrays outside route geometry`() {
        val service = WalkingRouteWaypointService(
            responseFetcher = {
                """
                {
                  "routes": [
                    {
                      "geometry": {
                        "coordinates": [
                          [13.4000, 52.5000],
                          [13.4010, 52.5010],
                          [13.4020, 52.5020],
                          [13.4030, 52.5030]
                        ]
                      },
                      "legs": [
                        {
                          "steps": [
                            {
                              "geometry": {
                                "coordinates": [
                                  [7.0000, 48.0000],
                                  [7.1000, 48.1000]
                                ]
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            },
        )

        val waypoints = service.buildWaypoints(
            origin = MissionTarget(52.5000, 13.4000),
            target = MissionTarget(52.5030, 13.4030),
        )

        assertEquals(2, waypoints.size)
        assertEquals(52.5010, waypoints[0].latitude, 0.000001)
        assertEquals(13.4020, waypoints[1].longitude, 0.000001)
    }
}
