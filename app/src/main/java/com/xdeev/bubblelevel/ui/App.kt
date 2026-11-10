package com.xdeev.bubblelevel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xdeev.bubblelevel.LevelViewModel
import com.xdeev.bubblelevel.ads.AdManager
import com.xdeev.bubblelevel.ads.BannerAd
import com.xdeev.bubblelevel.R
import com.xdeev.bubblelevel.ui.theme.BL
import com.xdeev.bubblelevel.ui.theme.BLType

private const val MODE_LEVEL = 0
private const val MODE_SPIRIT = 1
private const val MODE_ANGLE = 2

@Composable
fun BubbleLevelApp(vm: LevelViewModel) {
    val up by vm.screenUp.collectAsStateWithLifecycle()
    val held by vm.isHeld.collectAsStateWithLifecycle()
    val zero by vm.angleZero.collectAsStateWithLifecycle()
    val haptics by vm.settings.haptics.collectAsStateWithLifecycle()
    val sound by vm.settings.sound.collectAsStateWithLifecycle()
    val keepOn by vm.settings.keepScreenOn.collectAsStateWithLifecycle()
    val fine by vm.settings.fine.collectAsStateWithLifecycle()
    val percent by vm.settings.percentUnits.collectAsStateWithLifecycle()
    val tolTenths by vm.settings.toleranceTenths.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableIntStateOf(MODE_LEVEL) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCalibration by rememberSaveable { mutableStateOf(false) }

    // Tell the view model how the screen is rotated so "right" and "up" follow what the user sees.
    val rotation = rememberDisplayRotation()
    LaunchedEffect(rotation) { vm.setRotation(rotation) }

    val view = LocalView.current
    val feedback = remember(view) { Feedback(view) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val prefs = DisplayPrefs(fine = fine, percent = percent, toleranceDeg = tolTenths / 10f)
    val activity = LocalContext.current.findActivity()
    val onReached = {
        feedback.reached(haptics, sound)
        AdManager.noteLevelReached() // keep the ads quiet while the user reads the result
    }

    Column(
        Modifier.fillMaxSize().background(BL.Black).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        TopBar(onCalibrate = { vm.openCalibration(); showCalibration = true }, onSettings = { showSettings = true })

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!vm.hasSensor) {
                NoSensor(stringResource(R.string.no_sensor_title), stringResource(R.string.no_sensor_body))
            } else when (mode) {
                MODE_LEVEL -> LevelScreen(up, prefs, held, vm::toggleHold, onReached)
                MODE_SPIRIT -> SpiritScreen(up, prefs, held, vm::toggleHold, onReached)
                else -> AngleScreen(up, zero, prefs, held, vm::toggleHold, vm::zeroAngle, vm::resetZero, onReached)
            }
        }

        BottomBar(selected = mode, onSelect = {
            if (it != mode) {
                mode = it
                // A natural break: the user finished with one view. The pacer decides if an ad may show.
                activity?.let(AdManager::onNaturalBreak)
            }
        })
        // Clear space between the controls and the ad: Google penalises ads that sit too close to buttons.
        BannerAd(Modifier.padding(top = 14.dp))
    }

    if (showSettings) SettingsSheet(vm) {
        showSettings = false
        activity?.let(AdManager::onNaturalBreak)
    }
    if (showCalibration) CalibrationSheet(vm) { showCalibration = false }
}

@Composable
private fun TopBar(onCalibrate: () -> Unit, onSettings: () -> Unit) {
    val calibrate = stringResource(R.string.cd_calibrate)
    val settings = stringResource(R.string.cd_settings)
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(BL.Green))
        Box(Modifier.size(10.dp))
        Text(stringResource(R.string.app_name).uppercase(), style = BLType.Label, color = BL.TextDim, modifier = Modifier.weight(1f))
        IconAction(calibrate, onCalibrate) {
            Canvas(Modifier.size(22.dp)) {
                val c = center
                val r = size.minDimension * 0.30f
                val w = 1.8.dp.toPx()
                drawCircle(BL.Text, radius = r, center = c, style = Stroke(w))
                drawCircle(BL.Green, radius = r * 0.3f, center = c)
                for (d in listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))) {
                    drawLine(BL.Text, c + d * (r * 1.15f), c + d * (r * 1.65f), w)
                }
            }
        }
        IconAction(settings, onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = BL.Text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun IconAction(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.mode_level, R.string.mode_spirit, R.string.mode_angle)
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(BL.SurfaceHigh)
                .border(BorderStroke(1.dp, BL.Outline), shape)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { i, res ->
                val on = i == selected
                Row(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (on) BL.Green.copy(alpha = 0.14f) else BL.SurfaceHigh)
                        .clickable(role = Role.Tab) { onSelect(i) }
                        .padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                ) {
                    ModeIcon(i, if (on) BL.Green else BL.TextDim)
                    Text(
                        stringResource(res).uppercase(), style = BLType.Button,
                        color = if (on) BL.Green else BL.TextDim, maxLines = 1, softWrap = false,
                    )
                }
            }
        }
    }
}
