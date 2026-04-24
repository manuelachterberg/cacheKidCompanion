package com.cachekid.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationInputSelectorTest {

    private val selector = NavigationInputSelector()

    @Test
    fun `prefer injected uses fresh injected signal when available`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.PREFER_INJECTED,
            signalState = NavigationInputSignalState(
                onboardAvailable = true,
                injectedAvailable = true,
                injectedFresh = true,
            ),
        )

        assertEquals(NavigationInputSource.INJECTED, source)
    }

    @Test
    fun `prefer injected falls back to onboard when injected signal is stale`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.PREFER_INJECTED,
            signalState = NavigationInputSignalState(
                onboardAvailable = true,
                injectedAvailable = true,
                injectedFresh = false,
            ),
        )

        assertEquals(NavigationInputSource.ONBOARD, source)
    }

    @Test
    fun `onboard only ignores injected signal`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.ONBOARD_ONLY,
            signalState = NavigationInputSignalState(
                onboardAvailable = false,
                injectedAvailable = true,
                injectedFresh = true,
            ),
        )

        assertEquals(NavigationInputSource.NONE, source)
    }
}
