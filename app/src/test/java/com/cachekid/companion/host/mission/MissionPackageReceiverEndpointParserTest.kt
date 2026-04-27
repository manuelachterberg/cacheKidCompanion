package com.cachekid.companion.host.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MissionPackageReceiverEndpointParserTest {

    private val parser = MissionPackageReceiverEndpointParser()

    @Test
    fun `parser accepts plain host`() {
        val result = parser.parse("192.168.0.23", 8765)

        assertEquals(MissionPackageSendStatus.SENT, result.status)
        assertEquals("192.168.0.23", result.endpoint?.host)
        assertEquals(8765, result.endpoint?.port)
        assertEquals("http://192.168.0.23:8765/missions", result.endpoint?.toUrl().toString())
    }

    @Test
    fun `parser accepts pasted http url and keeps configured port`() {
        val result = parser.parse("http://192.168.0.23:9999/missions", 8765)

        assertEquals(MissionPackageSendStatus.SENT, result.status)
        assertEquals("192.168.0.23", result.endpoint?.host)
        assertEquals(8765, result.endpoint?.port)
    }

    @Test
    fun `parser accepts host with trailing path`() {
        val result = parser.parse("192.168.0.23/status", 8765)

        assertEquals(MissionPackageSendStatus.SENT, result.status)
        assertEquals("192.168.0.23", result.endpoint?.host)
    }

    @Test
    fun `parser accepts host with pasted port and keeps configured port`() {
        val result = parser.parse("kid-reader.local:9999/status", 8765)

        assertEquals(MissionPackageSendStatus.SENT, result.status)
        assertEquals("kid-reader.local", result.endpoint?.host)
        assertEquals(8765, result.endpoint?.port)
    }

    @Test
    fun `parser rejects blank address`() {
        val result = parser.parse("  ", 8765)

        assertEquals(MissionPackageSendStatus.MISSING_ADDRESS, result.status)
        assertNull(result.endpoint)
    }

    @Test
    fun `parser rejects invalid port`() {
        val result = parser.parse("192.168.0.23", -1)

        assertEquals(MissionPackageSendStatus.INVALID_PORT, result.status)
        assertNull(result.endpoint)
    }

    @Test
    fun `parser rejects malformed address`() {
        val result = parser.parse("not a host", 8765)

        assertEquals(MissionPackageSendStatus.INVALID_ADDRESS, result.status)
        assertNull(result.endpoint)
    }

    @Test
    fun `parser rejects malformed url with scheme`() {
        val result = parser.parse("http://:8765/missions", 8765)

        assertEquals(MissionPackageSendStatus.INVALID_ADDRESS, result.status)
        assertNull(result.endpoint)
    }

    @Test
    fun `parser builds endpoint for domain names`() {
        val result = parser.parse("kid-reader.local", 8765)

        assertEquals(MissionPackageSendStatus.SENT, result.status)
        assertNotNull(result.endpoint)
        assertEquals("kid-reader.local", result.endpoint?.host)
    }
}
