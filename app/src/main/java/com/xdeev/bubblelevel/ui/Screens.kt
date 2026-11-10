package com.xdeev.bubblelevel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xdeev.bubblelevel.R
import com.xdeev.bubblelevel.core.TiltMath
import com.xdeev.bubblelevel.core.Vec3
import com.xdeev.bubblelevel.ui.theme.BL
import com.xdeev.bubblelevel.ui.theme.BLType
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
// LEVEL: phone flat on a surface, two-axis bubble
// ---------------------------------------------------------------------------------------------

@Composable
fun LevelScreen(
    up: Vec3?,
    prefs: DisplayPrefs,
    held: Boolean,
    onHold: () -> Unit,
    onReached: () -> Unit,
) {
    val tx = up?.let { TiltMath.horizontalEdgeDeg(it) }
    val ty = up?.let { TiltMath.verticalEdgeDeg(it) }
    val total = up?.let { TiltMath.surfaceTiltDeg(it) }
    val level = rememberIsLevel(total, prefs.toleranceDeg, onReached)

    AdaptiveLayout(
        visual = {
            BubbleDial(
                tiltX = tx ?: 0f, tiltY = ty ?: 0f, level = level, rangeDeg = 8f,
                description = total?.let { "Level indicator, tilted ${formatDeg(it, true, false)}" } ?: "Level indicator",
                modifier = Modifier.fillMaxSize(),
            )
        },
        info = {
            StatusChip(
                text = when {
                    held -> stringResource(R.string.status_held)
                    level -> stringResource(R.string.status_level)
                    total == null -> stringResource(R.string.hint_level)
                    else -> stringResource(R.string.status_tilted)
                },
                active = level || held,
            )
            val dash = stringResource(R.string.dash)
            ReadoutRow {
                if (prefs.percent) {
                    Readout(stringResource(R.string.label_x), tx?.let { formatPercent(TiltMath.slopePercent(it), prefs.fine) } ?: dash, Modifier.weight(1f))
                    Readout(stringResource(R.string.label_y), ty?.let { formatPercent(TiltMath.slopePercent(it), prefs.fine) } ?: dash, Modifier.weight(1f))
                } else {
                    Readout(stringResource(R.string.label_x), tx?.let { formatDeg(it, prefs.fine) } ?: dash, Modifier.weight(1f))
                    Readout(stringResource(R.string.label_y), ty?.let { formatDeg(it, prefs.fine) } ?: dash, Modifier.weight(1f))
                }
                Readout(stringResource(R.string.label_tilt), total?.let { formatDeg(it, prefs.fine, false) } ?: dash, Modifier.weight(1f), highlight = level)
            }
            PillButton(
                text = stringResource(if (held) R.string.release else R.string.hold),
                onClick = onHold, active = held,
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// SPIRIT: phone on its edge or against a wall, a horizontal vial plus a plumb vial
// ---------------------------------------------------------------------------------------------

@Composable
fun SpiritScreen(
    up: Vec3?,
    prefs: DisplayPrefs,
    held: Boolean,
    onHold: () -> Unit,
    onReached: () -> Unit,
) {
    val horizontal = up?.let { TiltMath.horizontalEdgeDeg(it) }
    val plumb = up?.let { TiltMath.plumbDeg(it) }
    // The plumb vial only means something when the phone stands up against a wall.
    val plumbValid = up != null && abs(up.z) < 0.5f
    val level = rememberIsLevel(horizontal?.let { abs(it) }, prefs.toleranceDeg, onReached)
    val plumbLevel = plumbValid && plumb != null && abs(plumb) < prefs.toleranceDeg

    AdaptiveLayout(
        visual = {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Vial(
                        valueDeg = horizontal ?: 0f, rangeDeg = 6f, level = level, vertical = false,
                        description = horizontal?.let { "Horizontal vial, ${formatDeg(it, true)}" } ?: "Horizontal vial",
                        modifier = Modifier.fillMaxWidth().height(76.dp),
                    )
                }
                Box(Modifier.width(20.dp))
                Box(Modifier.width(76.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Vial(
                        valueDeg = if (plumbValid) plumb ?: 0f else 0f, rangeDeg = 6f, level = plumbLevel, vertical = true,
                        active = plumbValid,
                        description = plumb?.let { "Plumb vial, ${formatDeg(it, true)}" } ?: "Plumb vial",
                        modifier = Modifier.width(76.dp).fillMaxHeight(0.8f),
                    )
                }
            }
        },
        info = {
            StatusChip(
                text = when {
                    held -> stringResource(R.string.status_held)
                    level -> stringResource(R.string.status_level)
                    horizontal == null -> stringResource(R.string.hint_spirit)
                    else -> stringResource(R.string.status_tilted)
                },
                active = level || held,
            )
            val dash = stringResource(R.string.dash)
            ReadoutRow {
                Readout(
                    stringResource(R.string.label_horizontal),
                    horizontal?.let { if (prefs.percent) formatPercent(TiltMath.slopePercent(it), prefs.fine) else formatDeg(it, prefs.fine) } ?: dash,
                    Modifier.weight(1f), highlight = level,
                )
                Readout(
                    stringResource(R.string.label_plumb),
                    if (plumbValid && plumb != null) {
                        if (prefs.percent) formatPercent(TiltMath.slopePercent(plumb), prefs.fine) else formatDeg(plumb, prefs.fine)
                    } else dash,
                    Modifier.weight(1f), highlight = plumbLevel,
                )
            }
            PillButton(
                text = stringResource(if (held) R.string.release else R.string.hold),
                onClick = onHold, active = held,
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// ANGLE: clinometer with slope %, 1:n ratio and a relative "zero"
// ---------------------------------------------------------------------------------------------

@Composable
fun AngleScreen(
    up: Vec3?,
    zero: Float,
    prefs: DisplayPrefs,
    held: Boolean,
    onHold: () -> Unit,
    onZero: (Float) -> Unit,
    onResetZero: () -> Unit,
    onReached: () -> Unit,
) {
    val absolute = up?.let { TiltMath.horizontalEdgeDeg(it) }
    val shown = absolute?.let { (it - zero).coerceIn(-90f, 90f) }
    val zeroed = zero != 0f
    // Highlight at flat, and at plumb when measuring in absolute terms.
    val measure = shown?.let { if (zeroed) abs(it) else min(abs(it), abs(90f - abs(it))) }
    val level = rememberIsLevel(measure, prefs.toleranceDeg, onReached)

    AdaptiveLayout(
        visual = {
            AngleGauge(
                angleDeg = shown ?: 0f, level = level,
                description = shown?.let { "Inclinometer, ${formatDeg(it, true)}" } ?: "Inclinometer",
                modifier = Modifier.fillMaxSize(),
            )
        },
        info = {
            val dash = stringResource(R.string.dash)
            Text(
                text = shown?.let { if (prefs.percent) formatPercent(TiltMath.slopePercent(it), prefs.fine) else formatDeg(it, prefs.fine) } ?: dash,
                style = BLType.Hero,
                color = if (level || held) BL.Green else BL.Text,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            ReadoutRow {
                Readout(
                    stringResource(R.string.label_slope),
                    shown?.let { if (prefs.percent) formatDeg(it, prefs.fine) else formatPercent(TiltMath.slopePercent(it), prefs.fine) } ?: dash,
                    Modifier.weight(1f),
                )
                Readout(stringResource(R.string.label_ratio), shown?.let { formatRatio(TiltMath.slopeRatio(it)) } ?: dash, Modifier.weight(1f))
                Readout(
                    stringResource(R.string.label_from_vertical),
                    shown?.let { formatDeg(90f - abs(it), prefs.fine, false) } ?: dash,
                    Modifier.weight(1.3f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = stringResource(if (held) R.string.release else R.string.hold),
                    onClick = onHold, active = held,
                )
                PillButton(
                    text = stringResource(if (zeroed) R.string.reset_zero else R.string.zero),
                    onClick = { if (zeroed) onResetZero() else absolute?.let(onZero) },
                    active = zeroed,
                )
            }
        },
    )
}
