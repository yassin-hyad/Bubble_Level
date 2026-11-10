package com.xdeev.bubblelevel.data

import android.content.Context
import com.xdeev.bubblelevel.core.Calibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tiny SharedPreferences-backed store exposing each setting as a StateFlow. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _haptics = MutableStateFlow(prefs.getBoolean(K_HAPTICS, false))
    private val _sound = MutableStateFlow(prefs.getBoolean(K_SOUND, false))
    private val _keepOn = MutableStateFlow(prefs.getBoolean(K_KEEP_ON, true))
    private val _fine = MutableStateFlow(prefs.getBoolean(K_FINE, true))
    private val _percent = MutableStateFlow(prefs.getBoolean(K_PERCENT, false))
    private val _tolerance = MutableStateFlow(prefs.getInt(K_TOL, 5))
    private val _calibration = MutableStateFlow(
        Calibration(prefs.getFloat(K_BIAS_X, 0f), prefs.getFloat(K_BIAS_Y, 0f))
    )

    val haptics: StateFlow<Boolean> = _haptics
    val sound: StateFlow<Boolean> = _sound
    val keepScreenOn: StateFlow<Boolean> = _keepOn
    /** true = one decimal (0.1°), false = whole degrees. */
    val fine: StateFlow<Boolean> = _fine
    val percentUnits: StateFlow<Boolean> = _percent
    /** Level tolerance in tenths of a degree (2 = 0.2°, 5 = 0.5°, 10 = 1°). */
    val toleranceTenths: StateFlow<Int> = _tolerance
    val calibration: StateFlow<Calibration> = _calibration

    fun setHaptics(v: Boolean) { _haptics.value = v; prefs.edit().putBoolean(K_HAPTICS, v).apply() }
    fun setSound(v: Boolean) { _sound.value = v; prefs.edit().putBoolean(K_SOUND, v).apply() }
    fun setKeepScreenOn(v: Boolean) { _keepOn.value = v; prefs.edit().putBoolean(K_KEEP_ON, v).apply() }
    fun setFine(v: Boolean) { _fine.value = v; prefs.edit().putBoolean(K_FINE, v).apply() }
    fun setPercentUnits(v: Boolean) { _percent.value = v; prefs.edit().putBoolean(K_PERCENT, v).apply() }
    fun setToleranceTenths(v: Int) { _tolerance.value = v; prefs.edit().putInt(K_TOL, v).apply() }
    fun setCalibration(c: Calibration) {
        _calibration.value = c
        prefs.edit().putFloat(K_BIAS_X, c.biasX).putFloat(K_BIAS_Y, c.biasY).apply()
    }

    /** Counts real app starts (1 = the very first). */
    fun registerLaunch(): Int {
        val n = prefs.getInt(K_LAUNCHES, 0) + 1
        prefs.edit().putInt(K_LAUNCHES, n).apply()
        return n
    }

    private companion object {
        const val K_LAUNCHES = "launches"
        const val K_HAPTICS = "haptics"
        const val K_SOUND = "sound"
        const val K_KEEP_ON = "keep_on"
        const val K_FINE = "fine"
        const val K_PERCENT = "percent"
        const val K_TOL = "tolerance_tenths"
        const val K_BIAS_X = "bias_x"
        const val K_BIAS_Y = "bias_y"
    }
}
