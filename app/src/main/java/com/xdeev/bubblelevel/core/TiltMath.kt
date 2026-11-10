package com.xdeev.bubblelevel.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Unit vector pointing "up" (away from the Earth), expressed in the phone's axes:
 * x = right edge, y = top edge, z = out of the screen.
 * Phone flat on a table, screen up  -> (0, 0, 1).
 * Phone upright in portrait         -> (0, 1, 0).
 * A positive x means the right edge is higher than the left edge.
 */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** Sensor bias, measured in the phone's axes, in "sine of tilt" units (so 0.01 is about 0.57 degrees). */
data class Calibration(val biasX: Float = 0f, val biasY: Float = 0f) {
    val isSet: Boolean get() = biasX != 0f || biasY != 0f
}

object TiltMath {

    /** Below this the phone is in free fall (or the reading is garbage): ignore it. */
    private const val MIN_GRAVITY = 4f

    fun unitUp(gx: Float, gy: Float, gz: Float): Vec3? {
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        return if (n < MIN_GRAVITY) null else Vec3(gx / n, gy / n, gz / n)
    }

    /** Removes the calibration bias (phone axes) and keeps the vector at unit length. */
    fun calibrate(up: Vec3, c: Calibration): Vec3 {
        val x = (up.x - c.biasX).coerceIn(-1f, 1f)
        val y = (up.y - c.biasY).coerceIn(-1f, 1f)
        val z = sqrt(max(0f, 1f - x * x - y * y))
        return Vec3(x, y, if (up.z >= 0f) z else -z)
    }

    /**
     * Re-expresses the vector in the axes of what the user sees (screen right / screen up),
     * for the current display rotation (Surface.ROTATION_0 / 90 / 180 / 270 = 0..3).
     * ROTATION_90 means the phone was turned counter-clockwise: the top edge points left,
     * so the screen's "up" is the phone's right edge.
     */
    fun toScreen(up: Vec3, rotation: Int): Vec3 = when (rotation) {
        1 -> Vec3(-up.y, up.x, up.z)
        2 -> Vec3(-up.x, -up.y, up.z)
        3 -> Vec3(up.y, -up.x, up.z)
        else -> up
    }

    /** Tilt of the screen's horizontal axis against the horizon, -90..90 (positive = right side higher). */
    fun horizontalEdgeDeg(screen: Vec3): Float = asinDeg(screen.x)

    /** Tilt of the screen's vertical axis against the horizon, -90..90 (positive = top higher). */
    fun verticalEdgeDeg(screen: Vec3): Float = asinDeg(screen.y)

    /** How far the screen plane is from horizontal, 0..90, whichever face is up. */
    fun surfaceTiltDeg(screen: Vec3): Float =
        Math.toDegrees(acos(abs(screen.z).coerceIn(0f, 1f).toDouble())).toFloat()

    /** Lean of a wall the phone's back rests on: 0 when plumb, positive when the top leans away. */
    fun plumbDeg(screen: Vec3): Float = asinDeg(screen.z)

    fun slopePercent(deg: Float): Float =
        (tan(Math.toRadians(deg.toDouble())) * 100.0).coerceIn(-9999.0, 9999.0).toFloat()

    /** The "n" of a 1:n slope (run per unit of rise). Null when flat. */
    fun slopeRatio(deg: Float): Float? {
        val t = abs(tan(Math.toRadians(deg.toDouble())))
        return if (t < 1e-4) null else (1.0 / t).toFloat()
    }

    /**
     * Sensor bias from one or two measurements taken on a flat surface.
     * One measurement assumes the surface is perfectly level.
     * Two measurements, the phone turned 180 degrees in between, cancel the surface error:
     * r1 = tilt + bias and r2 = -tilt + bias, so bias = (r1 + r2) / 2.
     */
    fun solveBias(first: Vec3, second: Vec3?): Calibration =
        if (second == null) Calibration(first.x, first.y)
        else Calibration((first.x + second.x) / 2f, (first.y + second.y) / 2f)

    fun average(samples: List<Vec3>): Vec3? {
        if (samples.isEmpty()) return null
        val n = samples.size.toFloat()
        return Vec3(samples.sumOf { it.x.toDouble() }.toFloat() / n,
            samples.sumOf { it.y.toDouble() }.toFloat() / n,
            samples.sumOf { it.z.toDouble() }.toFloat() / n)
    }

    fun asinDeg(v: Float): Float = Math.toDegrees(asin(v.coerceIn(-1f, 1f).toDouble())).toFloat()
}
