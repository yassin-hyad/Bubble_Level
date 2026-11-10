package com.xdeev.bubblelevel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xdeev.bubblelevel.core.Calibration
import com.xdeev.bubblelevel.core.TiltMath
import com.xdeev.bubblelevel.core.Vec3
import com.xdeev.bubblelevel.data.SettingsStore
import com.xdeev.bubblelevel.sensor.TiltSensor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface CalState {
    data object Idle : CalState
    data object Step1 : CalState
    data class Step2(val first: Vec3) : CalState
    data object Done : CalState
}

class LevelViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    val hasSensor: Boolean = TiltSensor.hasSensor(app)

    /** Smoothed gravity in the phone's own axes, no calibration. Only active while someone collects it. */
    val raw: StateFlow<Vec3?> = TiltSensor.up(app)
        .map<Vec3, Vec3?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    private val rotation = MutableStateFlow(0)
    private val held = MutableStateFlow<Vec3?>(null)

    val isHeld: StateFlow<Boolean> = held
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Calibrated gravity in screen axes (right / up), or the frozen value while "Hold" is on. */
    val screenUp: StateFlow<Vec3?> = combine(raw, rotation, settings.calibration, held) { r, rot, cal, h ->
        h ?: r?.let { TiltMath.toScreen(TiltMath.calibrate(it, cal), rot) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun setRotation(r: Int) { rotation.value = r }

    fun toggleHold() { held.value = if (held.value == null) screenUp.value else null }

    // --- Angle mode "zero" (relative measurements) ---------------------------------------------
    val angleZero = MutableStateFlow(0f)
    fun zeroAngle(currentAbsoluteDeg: Float) { angleZero.value = currentAbsoluteDeg }
    fun resetZero() { angleZero.value = 0f }

    // --- Calibration wizard ---------------------------------------------------------------------
    val calState = MutableStateFlow<CalState>(CalState.Idle)
    val calMeasuring = MutableStateFlow(false)
    val calNotFlat = MutableStateFlow(false)

    fun openCalibration() { calNotFlat.value = false; calState.value = CalState.Idle }

    fun startCalibration() { calNotFlat.value = false; calState.value = CalState.Step1 }

    fun captureCalibration() {
        val state = calState.value
        if (calMeasuring.value || (state !is CalState.Step1 && state !is CalState.Step2)) return
        viewModelScope.launch {
            calMeasuring.value = true
            val avg = TiltMath.average(raw.filterNotNull().take(24).toList())
            calMeasuring.value = false
            if (avg == null) return@launch
            if (abs(avg.z) < FLAT_Z) { calNotFlat.value = true; return@launch }
            calNotFlat.value = false
            when (state) {
                is CalState.Step1 -> calState.value = CalState.Step2(avg)
                is CalState.Step2 -> finish(TiltMath.solveBias(state.first, avg))
                else -> Unit
            }
        }
    }

    /** Step 2 skipped: trust the surface to be perfectly level. */
    fun skipSecondStep() {
        val s = calState.value as? CalState.Step2 ?: return
        finish(TiltMath.solveBias(s.first, null))
    }

    fun resetCalibration() {
        settings.setCalibration(Calibration())
        calState.value = CalState.Idle
    }

    private fun finish(c: Calibration) {
        settings.setCalibration(c)
        calState.value = CalState.Done
    }

    private companion object {
        /** The phone must be within about 14 degrees of flat to calibrate. */
        const val FLAT_Z = 0.97f
    }
}
