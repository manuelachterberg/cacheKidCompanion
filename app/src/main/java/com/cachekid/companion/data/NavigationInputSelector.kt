package com.cachekid.companion.data

data class NavigationInputSignalState(
    val onboardAvailable: Boolean,
    val injectedAvailable: Boolean,
    val injectedFresh: Boolean,
)

class NavigationInputSelector {

    fun selectSource(
        mode: NavigationInputMode,
        signalState: NavigationInputSignalState,
    ): NavigationInputSource {
        return when (mode) {
            NavigationInputMode.ONBOARD_ONLY -> {
                if (signalState.onboardAvailable) {
                    NavigationInputSource.ONBOARD
                } else {
                    NavigationInputSource.NONE
                }
            }

            NavigationInputMode.PREFER_INJECTED,
            NavigationInputMode.AUTO,
            -> when {
                signalState.injectedAvailable && signalState.injectedFresh -> NavigationInputSource.INJECTED
                signalState.onboardAvailable -> NavigationInputSource.ONBOARD
                else -> NavigationInputSource.NONE
            }
        }
    }
}
