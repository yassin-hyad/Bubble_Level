package com.xdeev.bubblelevel.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xdeev.bubblelevel.CalState
import com.xdeev.bubblelevel.ads.AdManager
import com.xdeev.bubblelevel.LevelViewModel
import com.xdeev.bubblelevel.R
import com.xdeev.bubblelevel.core.TiltMath
import com.xdeev.bubblelevel.ui.theme.BL
import com.xdeev.bubblelevel.ui.theme.BLType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: LevelViewModel, onDismiss: () -> Unit) {
    val s = vm.settings
    val haptics by s.haptics.collectAsStateWithLifecycle()
    val sound by s.sound.collectAsStateWithLifecycle()
    val keepOn by s.keepScreenOn.collectAsStateWithLifecycle()
    val fine by s.fine.collectAsStateWithLifecycle()
    val percent by s.percentUnits.collectAsStateWithLifecycle()
    val tol by s.toleranceTenths.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BL.SurfaceHigh,
        contentColor = BL.Text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BL.OutlineBright) },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = BLType.Number, color = BL.Text)
            SwitchRow(stringResource(R.string.setting_haptics), haptics, s::setHaptics)
            SwitchRow(stringResource(R.string.setting_sound), sound, s::setSound)
            SwitchRow(stringResource(R.string.setting_keep_on), keepOn, s::setKeepScreenOn)
            Divider()
            SegmentSetting(
                stringResource(R.string.setting_units),
                listOf(stringResource(R.string.units_degrees), stringResource(R.string.units_percent)),
                if (percent) 1 else 0,
            ) { s.setPercentUnits(it == 1) }
            SegmentSetting(
                stringResource(R.string.setting_precision),
                listOf(stringResource(R.string.precision_fine), stringResource(R.string.precision_coarse)),
                if (fine) 0 else 1,
            ) { s.setFine(it == 0) }
            SegmentSetting(
                stringResource(R.string.setting_tolerance),
                listOf("0.2°", "0.5°", "1°"),
                when (tol) { 2 -> 0; 10 -> 2; else -> 1 },
            ) { s.setToleranceTenths(intArrayOf(2, 5, 10)[it]) }
            Divider()
            if (AdManager.privacyOptionsRequired) {
                val activity = LocalContext.current.findActivity()
                PillButton(
                    stringResource(R.string.privacy_options),
                    { activity?.let(AdManager::showPrivacyOptions) },
                    Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(R.string.privacy_note), style = BLType.Body, color = BL.TextDim)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationSheet(vm: LevelViewModel, onDismiss: () -> Unit) {
    val state by vm.calState.collectAsStateWithLifecycle()
    val measuring by vm.calMeasuring.collectAsStateWithLifecycle()
    val notFlat by vm.calNotFlat.collectAsStateWithLifecycle()
    val calibration by vm.settings.calibration.collectAsStateWithLifecycle()
    val raw by vm.raw.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BL.SurfaceHigh,
        contentColor = BL.Text,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BL.OutlineBright) },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.cal_title), style = BLType.Number, color = BL.Text, modifier = Modifier.fillMaxWidth())

            // Live raw tilt so the user can see the phone is still
            val live = raw?.let { "X ${formatDeg(TiltMath.horizontalEdgeDeg(it), true)}   Y ${formatDeg(TiltMath.verticalEdgeDeg(it), true)}" }
            if (live != null) {
                Text("${stringResource(R.string.cal_live)}  $live", style = BLType.Label, color = BL.TextDim, modifier = Modifier.fillMaxWidth())
            }

            when (val st = state) {
                CalState.Idle -> {
                    Text(stringResource(R.string.cal_intro), style = BLType.Body, color = BL.TextDim, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(stringResource(R.string.cal_start), vm::startCalibration, Modifier.fillMaxWidth())
                    if (calibration.isSet) PillButton(stringResource(R.string.cal_reset), vm::resetCalibration, Modifier.fillMaxWidth())
                }
                CalState.Step1 -> StepContent(
                    stringResource(R.string.cal_step1_title), stringResource(R.string.cal_step1_body),
                    measuring, notFlat, vm::captureCalibration, null,
                )
                is CalState.Step2 -> StepContent(
                    stringResource(R.string.cal_step2_title), stringResource(R.string.cal_step2_body),
                    measuring, notFlat, vm::captureCalibration, vm::skipSecondStep,
                )
                CalState.Done -> {
                    Text(stringResource(R.string.cal_done), style = BLType.Number, color = BL.Green, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.cal_done_body), style = BLType.Body, color = BL.TextDim, modifier = Modifier.fillMaxWidth())
                    PrimaryButton(stringResource(R.string.cal_close), onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StepContent(
    title: String,
    body: String,
    measuring: Boolean,
    notFlat: Boolean,
    onCapture: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = BLType.Label, color = BL.Green)
        Text(body, style = BLType.Body, color = BL.Text)
        if (notFlat) Text(stringResource(R.string.cal_not_flat), style = BLType.Body, color = BL.GreenSoft)
    }
    PrimaryButton(
        text = stringResource(if (measuring) R.string.cal_measuring else R.string.cal_capture),
        onClick = onCapture, modifier = Modifier.fillMaxWidth(), enabled = !measuring,
    )
    if (onSkip != null) PillButton(stringResource(R.string.cal_skip_step2), onSkip, Modifier.fillMaxWidth())
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = BLType.Body, color = BL.Text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BL.Black, checkedTrackColor = BL.Green, checkedBorderColor = BL.Green,
                uncheckedThumbColor = BL.TextDim, uncheckedTrackColor = BL.Surface, uncheckedBorderColor = BL.OutlineBright,
            ),
        )
    }
}

@Composable
private fun SegmentSetting(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = BLType.Label, color = BL.TextDim)
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(BL.Surface).border(BorderStroke(1.dp, BL.Outline), shape).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEachIndexed { i, text ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).clip(shape)
                        .background(if (on) BL.Green.copy(alpha = 0.16f) else BL.Surface)
                        .clickable { onSelect(i) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = BLType.Button, color = if (on) BL.Green else BL.TextDim, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(2.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(BL.Outline))
}

internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
