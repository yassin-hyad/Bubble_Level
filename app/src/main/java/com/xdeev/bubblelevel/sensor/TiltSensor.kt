package com.xdeev.bubblelevel.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.xdeev.bubblelevel.core.TiltMath
import com.xdeev.bubblelevel.core.Vec3
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.hypot

/** Streams the smoothed "up" direction in the phone's axes. Registers only while collected. */
object TiltSensor {

    fun hasSensor(context: Context): Boolean {
        val sm = context.getSystemService(SensorManager::class.java) ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_GRAVITY) != null ||
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    fun up(context: Context): Flow<Vec3> = callbackFlow {
        val sm = context.getSystemService(SensorManager::class.java)
        // The raw accelerometer, not TYPE_GRAVITY: the system's fused gravity sensor can take several
        // seconds to settle after the phone is moved, which is useless for a level.
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (sm == null || sensor == null) {
            close()
            return@callbackFlow
        }
        var primed = false
        var fx = 0f
        var fy = 0f
        var fz = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = TiltMath.unitUp(e.values[0], e.values[1], e.values[2]) ?: return
                if (!primed) {
                    fx = v.x; fy = v.y; fz = v.z
                    primed = true
                } else {
                    // Adaptive low-pass: heavy smoothing while the phone is still (steady digits),
                    // light smoothing as soon as it really moves (instant response).
                    val delta = hypot(hypot(v.x - fx, v.y - fy), v.z - fz)
                    val alpha = (0.06f + 8f * delta).coerceAtMost(0.6f)
                    fx += alpha * (v.x - fx)
                    fy += alpha * (v.y - fy)
                    fz += alpha * (v.z - fz)
                }
                trySend(Vec3(fx, fy, fz))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }
}
