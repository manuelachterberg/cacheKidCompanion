package com.cachekid.companion.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.Flow

class HybridSensorRepository(context: Context) {

    private val appContext = context.applicationContext
    private val bleSensorClient = BleSensorClient(appContext)
    private val compassProvider = AndroidCompassHeadingProvider(appContext)
    private val locationProvider = AndroidLocationProvider(appContext)

    val headingDegrees: Flow<Float> = compassProvider.headingDegrees
    val locationUpdates: Flow<Location?> = locationProvider.locationUpdates

    fun isBluetoothSupported(): Boolean = bleSensorClient.isBluetoothSupported()
}
