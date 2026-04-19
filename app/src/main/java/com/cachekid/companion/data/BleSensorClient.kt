package com.cachekid.companion.data

import android.bluetooth.BluetoothManager
import android.content.Context

class BleSensorClient(context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    fun isBluetoothSupported(): Boolean = bluetoothManager?.adapter != null
}
