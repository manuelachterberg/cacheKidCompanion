package com.cachekid.companion.config

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequirementsTest {

    @Test
    fun `pre-android-12 excludes bluetooth runtime permissions`() {
        val permissions = PermissionRequirements.requiredPermissions(Build.VERSION_CODES.R)

        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertFalse(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertFalse(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    fun `android-12-and-newer includes bluetooth runtime permissions`() {
        val permissions = PermissionRequirements.requiredPermissions(Build.VERSION_CODES.S)

        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
    }

    @Test
    fun `location permission helper stays minimal and stable`() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            PermissionRequirements.requiredLocationPermissions(),
        )
    }
}
