package com.xdeev.bubblelevel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TiltMathTest {
    private val eps = 0.01f
    private fun rad(d: Double) = Math.toRadians(d)

    @Test fun flatPhoneHasZeroTilt() {
        val up = TiltMath.unitUp(0f, 0f, 9.81f)!!
        assertEquals(0f, TiltMath.surfaceTiltDeg(up), eps)
        assertEquals(0f, TiltMath.horizontalEdgeDeg(up), eps)
        assertEquals(0f, TiltMath.verticalEdgeDeg(up), eps)
    }

    @Test fun freeFallIsRejected() {
        assertNull(TiltMath.unitUp(0.1f, 0.1f, 0.2f))
    }

    @Test fun rightEdgeRaisedTenDegrees() {
        val a = rad(10.0)
        val up = TiltMath.unitUp(sin(a).toFloat() * 9.81f, 0f, cos(a).toFloat() * 9.81f)!!
        assertEquals(10f, TiltMath.horizontalEdgeDeg(up), eps)
        assertEquals(10f, TiltMath.surfaceTiltDeg(up), eps)
    }

    @Test fun faceDownPhoneStillMeasuresTiltFromHorizontal() {
        val a = rad(7.0)
        val up = TiltMath.unitUp(0f, sin(a).toFloat() * 9.81f, -cos(a).toFloat() * 9.81f)!!
        assertEquals(7f, TiltMath.surfaceTiltDeg(up), eps)
    }

    @Test fun uprightPhoneAgainstPlumbWallReadsPlumb() {
        val up = TiltMath.unitUp(0f, 9.81f, 0f)!!
        assertEquals(0f, TiltMath.plumbDeg(up), eps)
        assertEquals(0f, TiltMath.horizontalEdgeDeg(up), eps)
    }

    @Test fun wallLeaningBackReadsPositivePlumb() {
        val a = rad(3.0)
        val up = TiltMath.unitUp(0f, cos(a).toFloat() * 9.81f, sin(a).toFloat() * 9.81f)!!
        assertEquals(3f, TiltMath.plumbDeg(up), eps)
    }

    @Test fun calibrationCancelsBias() {
        // sensor reads 0.7 degrees off on x and -0.4 degrees off on y when truly flat
        val bx = sin(rad(0.7)).toFloat()
        val by = sin(rad(-0.4)).toFloat()
        val raw = Vec3(bx, by, 1f)
        val cal = TiltMath.solveBias(raw, null)
        val fixed = TiltMath.calibrate(raw, cal)
        assertEquals(0f, TiltMath.horizontalEdgeDeg(fixed), 0.001f)
        assertEquals(0f, TiltMath.verticalEdgeDeg(fixed), 0.001f)
        assertEquals(1f, fixed.z, 0.001f)
    }

    @Test fun twoStepCalibrationCancelsTheSurfaceSlope() {
        // surface is really tilted 1.0 degree on x; sensor bias is 0.5 degree
        val t = sin(rad(1.0)).toFloat()
        val b = sin(rad(0.5)).toFloat()
        val first = Vec3(t + b, 0f, 1f)
        val second = Vec3(-t + b, 0f, 1f)
        val cal = TiltMath.solveBias(first, second)
        assertEquals(b, cal.biasX, 1e-4f)
        // a genuinely flat reading now shows exactly zero
        val flat = TiltMath.calibrate(Vec3(b, 0f, 1f), cal)
        assertEquals(0f, TiltMath.horizontalEdgeDeg(flat), 0.001f)
    }

    @Test fun displayRotationMapsAxes() {
        // Phone lying flat with its right edge raised: x = 0.2
        val up = Vec3(0.2f, 0f, 0.98f)
        // turned counter-clockwise (rotation 90): the phone's right edge is the screen's top
        val r90 = TiltMath.toScreen(up, 1)
        assertEquals(0f, r90.x, 1e-6f)
        assertEquals(0.2f, r90.y, 1e-6f)
        // turned clockwise (rotation 270): the phone's right edge is the screen's bottom
        val r270 = TiltMath.toScreen(up, 3)
        assertEquals(-0.2f, r270.y, 1e-6f)
        // upside down (rotation 180): screen right is the phone's left
        assertEquals(-0.2f, TiltMath.toScreen(up, 2).x, 1e-6f)
        assertEquals(up, TiltMath.toScreen(up, 0))
    }

    @Test fun slopeConversions() {
        assertEquals(100f, TiltMath.slopePercent(45f), 0.01f)
        assertEquals(1f, TiltMath.slopeRatio(45f)!!, 0.01f)
        assertNull(TiltMath.slopeRatio(0f))
        assertEquals(17.63f, TiltMath.slopePercent(10f), 0.01f)
    }

    @Test fun averageOfSamples() {
        val avg = TiltMath.average(listOf(Vec3(0f, 0f, 1f), Vec3(0.2f, 0.2f, 1f)))
        assertNotNull(avg)
        assertEquals(0.1f, avg!!.x, 1e-6f)
    }
}
