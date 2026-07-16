package com.kevinboutwell.lightmeter.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Incident-style metering via the ambient light sensor. Phone light sensors
 * exist for screen-brightness control and are often coarsely quantized, so
 * readings are smoothed with a ~1 s rolling median.
 */
class AmbientLightMeter(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    val isAvailable: Boolean get() = sensor != null

    /** Median-smoothed illuminance in lux. Completes immediately if no sensor. */
    val lux: Flow<Double> = callbackFlow {
        val lightSensor = sensor ?: run {
            close()
            return@callbackFlow
        }
        val window = ArrayDeque<Pair<Long, Float>>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                window.addLast(now to event.values[0])
                while (window.isNotEmpty() && now - window.first().first > WINDOW_MS) {
                    window.removeFirst()
                }
                val sorted = window.map { it.second }.sorted()
                trySend(sorted[sorted.size / 2].toDouble())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private companion object {
        const val WINDOW_MS = 1000L
    }
}
