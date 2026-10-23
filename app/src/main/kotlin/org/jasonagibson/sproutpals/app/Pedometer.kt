package org.jasonagibson.sproutpals.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The phone's built-in pedometer (TYPE_STEP_COUNTER). It reports total steps since the
 * phone last booted; [org.jasonagibson.sproutpals.engine.Steps] turns the difference into points.
 * The phone counts steps all day on its own low-power chip, so the app doesn't need to be open
 * while you walk - it collects the total next time it looks.
 */
class Pedometer(private val context: Context) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var listener: SensorEventListener? = null

    val available get() = sensor != null
    fun permitted() = context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Live updates while the app is on screen. */
    fun start(onReading: (Long) -> Unit) {
        if (sensor == null || !permitted() || listener != null) return
        listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = onReading(e.values[0].toLong())
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sensors?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
    }

    /** One reading, for the background job. Null if there's no sensor, no permission, or no answer in 5 s. */
    suspend fun readOnce(): Long? {
        if (sensor == null || !permitted()) return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sensors?.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sensors?.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sensors?.unregisterListener(l) }
            }
        }
    }
}
