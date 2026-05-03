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

    @Test
    fun `auto uses fresh injected signal when available`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.AUTO,
            signalState = NavigationInputSignalState(
                onboardAvailable = true,
                injectedAvailable = true,
                injectedFresh = true,
            ),
        )

        assertEquals(NavigationInputSource.INJECTED, source)
    }

    @Test
    fun `auto falls back to onboard when injected signal is stale`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.AUTO,
            signalState = NavigationInputSignalState(
                onboardAvailable = true,
                injectedAvailable = true,
                injectedFresh = false,
            ),
        )

        assertEquals(NavigationInputSource.ONBOARD, source)
    }

    @Test
    fun `both unavailable returns none`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.AUTO,
            signalState = NavigationInputSignalState(
                onboardAvailable = false,
                injectedAvailable = false,
                injectedFresh = false,
            ),
        )

        assertEquals(NavigationInputSource.NONE, source)
    }

    @Test
    fun `injected unavailable but onboard available returns onboard`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.PREFER_INJECTED,
            signalState = NavigationInputSignalState(
                onboardAvailable = true,
                injectedAvailable = false,
                injectedFresh = false,
            ),
        )

        assertEquals(NavigationInputSource.ONBOARD, source)
    }

    @Test
    fun `onboard only with onboard available returns onboard`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.ONBOARD_ONLY,
            signalState = NavigationInputSignalState(
                onboardAvailable = true,
                injectedAvailable = true,
                injectedFresh = true,
            ),
        )

        assertEquals(NavigationInputSource.ONBOARD, source)
    }

    @Test
    fun `prefer injected with stale injected and no onboard returns none`() {
        val source = selector.selectSource(
            mode = NavigationInputMode.PREFER_INJECTED,
            signalState = NavigationInputSignalState(
                onboardAvailable = false,
                injectedAvailable = true,
                injectedFresh = false,
            ),
        )

        assertEquals(NavigationInputSource.NONE, source)
    }
}
