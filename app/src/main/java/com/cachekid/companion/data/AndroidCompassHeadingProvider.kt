package com.cachekid.companion.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs

class AndroidCompassHeadingProvider(context: Context) : DeviceHeadingProvider {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    override val headingDegrees: Flow<Float> = callbackFlow {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val adjustedRotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            private var smoothedAzimuthDegrees: Double? = null
            private var lastEmittedDegrees: Float? = null

            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val (axisX, axisY) = when (windowManager.defaultDisplay.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    axisX,
                    axisY,
                    adjustedRotationMatrix,
                )
                SensorManager.getOrientation(adjustedRotationMatrix, orientation)
                val azimuthRadians = orientation[0]
                val rawAzimuthDegrees = normalizeDegrees(Math.toDegrees(azimuthRadians.toDouble()))
                val previousSmoothed = smoothedAzimuthDegrees
                val nextSmoothed = if (previousSmoothed == null) {
                    rawAzimuthDegrees
                } else {
                    normalizeDegrees(previousSmoothed + smallestAngleDifference(previousSmoothed, rawAzimuthDegrees) * 0.18)
                }
                smoothedAzimuthDegrees = nextSmoothed
                val nextHeading = nextSmoothed.toFloat()
                val shouldEmit =
                    lastEmittedDegrees == null ||
                        abs(smallestAngleDifference(lastEmittedDegrees!!.toDouble(), nextSmoothed)) >= 1.2
                if (shouldEmit) {
                    lastEmittedDegrees = nextHeading
                    trySend(nextHeading)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private fun normalizeDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    private fun smallestAngleDifference(from: Double, to: Double): Double {
        var delta = (to - from) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        return delta
    }
}
