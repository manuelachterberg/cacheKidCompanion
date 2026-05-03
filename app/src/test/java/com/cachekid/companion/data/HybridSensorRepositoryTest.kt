package com.cachekid.companion.data

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HybridSensorRepositoryTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `updateInjectedNavigationInput updates debug summary`() = runBlocking {
        val repository = HybridSensorRepository(context)

        repository.updateInjectedNavigationInput(
            InjectedNavigationInput(
                latitude = 52.0,
                longitude = 10.0,
                headingDegrees = 90f,
                capturedAtEpochMillis = System.currentTimeMillis(),
            ),
        )

        val summary = repository.navigationInputDebugSummary.first()
        assertEquals(true, summary.contains("adb frisch"))
    }

    @Test
    fun `setNavigationInputMode changes location source preference`() = runBlocking {
        val repository = HybridSensorRepository(
            context = context,
            inputSelector = NavigationInputSelector(),
        )

        repository.setNavigationInputMode(NavigationInputMode.ONBOARD_ONLY)

        // With ONBOARD_ONLY and no onboard signal, source should be NONE
        val locationSource = repository.selectedLocationSource.first()
        assertEquals(NavigationInputSource.NONE, locationSource)
    }

    @Test
    fun `stale injected input is reflected in debug summary`() = runBlocking {
        val repository = HybridSensorRepository(
            context = context,
            clock = { 1_000_000L },
            injectedFreshnessWindowMillis = 5_000L,
        )

        repository.updateInjectedNavigationInput(
            InjectedNavigationInput(
                latitude = 52.0,
                longitude = 10.0,
                capturedAtEpochMillis = 0L, // stale
            ),
        )

        val summary = repository.getNavigationInputDebugSummary()
        assertEquals(true, summary.contains("adb stale"))
    }

    @Test
    fun `null injected input shows no adb input in summary`() = runBlocking {
        val repository = HybridSensorRepository(context)

        repository.updateInjectedNavigationInput(null)

        val summary = repository.getNavigationInputDebugSummary()
        assertEquals(true, summary.contains("kein adb-Input"))
    }
}
