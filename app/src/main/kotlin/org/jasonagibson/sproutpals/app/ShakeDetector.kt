package org.jasonagibson.sproutpals.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Turns a firm shake of the phone into "jolts" for opening the egg.
 * A jolt = acceleration over 2.5 g (normal handling stays under ~1.5 g),
 * at most one every 400 ms so one shake isn't counted three times.
 */
class ShakeDetector(context: Context, private val onJolt: () -> Unit) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastJolt = 0L

    fun start() { accel?.let { sensors?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { sensors?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()
        if (g > THRESHOLD_G && now - lastJolt > GAP_MS) {
            lastJolt = now
            onJolt()
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit

    private companion object {
        const val THRESHOLD_G = 2.5f
        const val GAP_MS = 400L
    }
}
