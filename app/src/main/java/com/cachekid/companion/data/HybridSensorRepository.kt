package com.cachekid.companion.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive

class HybridSensorRepository(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val inputSelector: NavigationInputSelector = NavigationInputSelector(),
    private val injectedFreshnessWindowMillis: Long = DEFAULT_INJECTED_FRESHNESS_WINDOW_MILLIS,
) {

    private val appContext = context.applicationContext
    private val bleSensorClient = BleSensorClient(appContext)
    private val compassProvider = AndroidCompassHeadingProvider(appContext)
    private val locationProvider = AndroidLocationProvider(appContext)
    private val inputMode = MutableStateFlow(NavigationInputMode.AUTO)
    private val injectedInput = MutableStateFlow<InjectedNavigationInput?>(null)
    private val selectedLocationSourceState = MutableStateFlow(NavigationInputSource.NONE)
    private val selectedHeadingSourceState = MutableStateFlow(NavigationInputSource.NONE)
    private val navigationInputDebugSummaryState =
        MutableStateFlow("Standort none | Heading none | kein adb-Input")

    val selectedLocationSource: StateFlow<NavigationInputSource> =
        selectedLocationSourceState.asStateFlow()

    val selectedHeadingSource: StateFlow<NavigationInputSource> =
        selectedHeadingSourceState.asStateFlow()

    val navigationInputDebugSummary: StateFlow<String> =
        navigationInputDebugSummaryState.asStateFlow()

    val locationReadings: Flow<NavigationLocationReading?> =
        combine(
            inputMode,
            locationProvider.locationUpdates
                .map { location -> location?.toOnboardLocationReading() }
                .onStart { emit(null) },
            injectedInput,
            freshnessTicker(),
        ) { mode, onboard, injected, now ->
            val source = inputSelector.selectSource(
                mode = mode,
                signalState = NavigationInputSignalState(
                    onboardAvailable = onboard != null,
                    injectedAvailable = injected?.hasLocation == true,
                    injectedFresh = injected?.hasLocation == true && isInjectedFresh(injected, now),
                ),
            )
            val reading = when (source) {
                NavigationInputSource.ONBOARD -> onboard
                NavigationInputSource.INJECTED -> injected?.toInjectedLocationReading()
                NavigationInputSource.NONE -> null
            }
            SelectionResult<NavigationLocationReading>(
                source = source,
                reading = reading,
                injected = injected,
                now = now,
            )
        }
            .onEach { result ->
                selectedLocationSourceState.value = result.source
                updateNavigationInputDebugSummary(result.injected, result.now)
            }
            .map { result -> result.reading }
            .distinctUntilChanged { old, new ->
                old?.source == new?.source &&
                    old?.location?.latitude == new?.location?.latitude &&
                    old?.location?.longitude == new?.location?.longitude &&
                    old?.location?.accuracy == new?.location?.accuracy
            }

    val headingReadings: Flow<NavigationHeadingReading?> =
        combine(
            inputMode,
            compassProvider.headingDegrees
                .map { headingDegrees -> headingDegrees.toOnboardHeadingReading() as NavigationHeadingReading? }
                .onStart { emit(null) },
            injectedInput,
            freshnessTicker(),
        ) { mode, onboard, injected, now ->
            val source = inputSelector.selectSource(
                mode = mode,
                signalState = NavigationInputSignalState(
                    onboardAvailable = onboard != null,
                    injectedAvailable = injected?.hasHeading == true,
                    injectedFresh = injected?.hasHeading == true && isInjectedFresh(injected, now),
                ),
            )
            val reading = when (source) {
                NavigationInputSource.ONBOARD -> onboard
                NavigationInputSource.INJECTED -> injected?.toInjectedHeadingReading()
                NavigationInputSource.NONE -> null
            }
            SelectionResult<NavigationHeadingReading>(
                source = source,
                reading = reading,
                injected = injected,
                now = now,
            )
        }
            .onEach { result ->
                selectedHeadingSourceState.value = result.source
                updateNavigationInputDebugSummary(result.injected, result.now)
            }
            .map { result -> result.reading }
            .distinctUntilChanged()

    val headingDegrees: Flow<Float> = headingReadings
        .map { it?.headingDegrees }
        .filterNotNull()

    val locationUpdates: Flow<Location?> = locationReadings.map { it?.location }

    fun isBluetoothSupported(): Boolean = bleSensorClient.isBluetoothSupported()

    fun setNavigationInputMode(mode: NavigationInputMode) {
        inputMode.value = mode
    }

    fun updateInjectedNavigationInput(value: InjectedNavigationInput?) {
        injectedInput.value = value
        updateNavigationInputDebugSummary(value, clock())
    }

    fun getNavigationInputDebugSummary(): String {
        return navigationInputDebugSummary.value
    }

    private fun isInjectedFresh(value: InjectedNavigationInput, nowEpochMillis: Long): Boolean {
        return (nowEpochMillis - value.capturedAtEpochMillis).coerceAtLeast(0L) <= injectedFreshnessWindowMillis
    }

    private fun freshnessTicker(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(clock())
            delay(FRESHNESS_TICK_INTERVAL_MILLIS)
        }
    }

    private fun Location.toOnboardLocationReading(): NavigationLocationReading {
        return NavigationLocationReading(
            location = this,
            source = NavigationInputSource.ONBOARD,
            capturedAtEpochMillis = clock(),
        )
    }

    private fun Float.toOnboardHeadingReading(): NavigationHeadingReading {
        return NavigationHeadingReading(
            headingDegrees = this,
            source = NavigationInputSource.ONBOARD,
            capturedAtEpochMillis = clock(),
        )
    }

    private fun InjectedNavigationInput.toInjectedLocationReading(): NavigationLocationReading? {
        if (!hasLocation) {
            return null
        }

        val location = Location(INJECTED_LOCATION_PROVIDER).apply {
            latitude = this@toInjectedLocationReading.latitude ?: 0.0
            longitude = this@toInjectedLocationReading.longitude ?: 0.0
            accuracy = accuracyMeters ?: 0f
            time = capturedAtEpochMillis
        }
        return NavigationLocationReading(
            location = location,
            source = NavigationInputSource.INJECTED,
            capturedAtEpochMillis = capturedAtEpochMillis,
        )
    }

    private fun InjectedNavigationInput.toInjectedHeadingReading(): NavigationHeadingReading? {
        val heading = headingDegrees ?: return null
        return NavigationHeadingReading(
            headingDegrees = heading,
            source = NavigationInputSource.INJECTED,
            capturedAtEpochMillis = capturedAtEpochMillis,
        )
    }

    private fun updateNavigationInputDebugSummary(
        injected: InjectedNavigationInput?,
        nowEpochMillis: Long,
    ) {
        val freshnessText = when {
            injected == null -> "kein adb-Input"
            isInjectedFresh(injected, nowEpochMillis) -> "adb frisch"
            else -> "adb stale"
        }
        navigationInputDebugSummaryState.value =
            "Standort ${selectedLocationSource.value.name.lowercase()} | Heading ${selectedHeadingSource.value.name.lowercase()} | $freshnessText"
    }

    companion object {
        private const val INJECTED_LOCATION_PROVIDER = "adb-injected"
        private const val FRESHNESS_TICK_INTERVAL_MILLIS = 1_000L
        private const val DEFAULT_INJECTED_FRESHNESS_WINDOW_MILLIS = 5 * 60 * 1_000L
    }
}

private data class SelectionResult<T>(
    val source: NavigationInputSource,
    val reading: T?,
    val injected: InjectedNavigationInput?,
    val now: Long,
)

data class NavigationLocationReading(
    val location: Location,
    val source: NavigationInputSource,
    val capturedAtEpochMillis: Long,
)

data class NavigationHeadingReading(
    val headingDegrees: Float,
    val source: NavigationInputSource,
    val capturedAtEpochMillis: Long,
)
