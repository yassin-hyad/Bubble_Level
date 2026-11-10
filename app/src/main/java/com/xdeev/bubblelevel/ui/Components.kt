package com.xdeev.bubblelevel.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xdeev.bubblelevel.ui.theme.BL
import com.xdeev.bubblelevel.ui.theme.BLType
import java.util.Locale
import kotlin.math.abs

/** Display preferences shared by the three measuring screens. */
data class DisplayPrefs(val fine: Boolean, val percent: Boolean, val toleranceDeg: Float)

private const val MINUS = "−"

/** "+2.3°" / "−0.4°" / "0.0°". Never prints "-0.0". */
fun formatDeg(v: Float, fine: Boolean, signed: Boolean = true): String {
    val r = if (fine) Math.round(v * 10f) / 10f else Math.round(v).toFloat()
    val body = String.format(Locale.getDefault(), if (fine) "%.1f" else "%.0f", abs(r))
    val sign = when {
        r < 0f -> MINUS
        r > 0f && signed -> "+"
        else -> ""
    }
    return "$sign$body°"
}

fun formatPercent(v: Float, fine: Boolean): String {
    val r = if (fine) Math.round(v * 10f) / 10f else Math.round(v).toFloat()
    val body = String.format(Locale.getDefault(), if (fine) "%.1f" else "%.0f", abs(r))
    return (if (r < 0f) MINUS else "") + body + "%"
}

/** "1 : 4.6" for steep slopes, "1 : 191" once it is nearly flat; a dash when there is no slope to speak of. */
fun formatRatio(n: Float?): String = when {
    n == null || n > 999f -> "—"
    n >= 100f -> "1 : " + String.format(Locale.getDefault(), "%.0f", n)
    else -> "1 : " + String.format(Locale.getDefault(), "%.1f", n)
}

// --- Rotation ---------------------------------------------------------------------------------

@Composable
fun rememberDisplayRotation(): Int {
    val context = LocalContext.current
    val view = LocalView.current
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: 0) }
    // Three triggers, because no single one is reliable on every device and rotation:
    // the display listener, any layout change (a 90/270 turn resizes the window) and a new configuration.
    val configuration = LocalConfiguration.current
    DisposableEffect(context, view, configuration) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val refresh = { view.display?.rotation?.let { if (it != rotation) rotation = it } }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) { refresh() }
        }
        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }
        dm.registerDisplayListener(displayListener, null)
        view.addOnLayoutChangeListener(layoutListener)
        refresh()
        onDispose {
            dm.unregisterDisplayListener(displayListener)
            view.removeOnLayoutChangeListener(layoutListener)
        }
    }
    return rotation
}

// --- Level detection with hysteresis + feedback ------------------------------------------------

/**
 * True while [measure] (degrees away from the target) is inside the tolerance.
 * Uses hysteresis so the state does not flicker at the edge, and fires [onReached] once on entering.
 */
@Composable
fun rememberIsLevel(measure: Float?, tolDeg: Float, onReached: () -> Unit): Boolean {
    var level by remember { mutableStateOf(false) }
    val reached by rememberUpdatedState(onReached)
    val inside = measure != null && measure < tolDeg
    val outside = measure == null || measure > tolDeg * 1.6f
    LaunchedEffect(inside, outside) {
        if (inside && !level) {
            level = true
            reached()
        } else if (outside && level) {
            level = false
        }
    }
    return level
}

/** Vibration and beep when the phone becomes level. Both are off until the user turns them on. */
class Feedback(private val view: View) {
    private var tone: ToneGenerator? = null

    @Suppress("DEPRECATION")
    fun reached(haptics: Boolean, sound: Boolean) {
        if (haptics) {
            val type = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK
            view.performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
        if (sound) {
            if (tone == null) tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}

// --- Layout -----------------------------------------------------------------------------------

/** Portrait: visual on top, info below. Landscape: visual left, info right. */
@Composable
fun AdaptiveLayout(
    visual: @Composable BoxScope.() -> Unit,
    info: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Box(Modifier.weight(1.1f).fillMaxHeight().padding(vertical = 8.dp), Alignment.Center, content = visual)
                Column(
                    Modifier.weight(0.9f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    content = info,
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), Alignment.Center, content = visual)
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = info,
                )
            }
        }
    }
}

// --- Small building blocks --------------------------------------------------------------------

@Composable
fun Readout(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = BLType.Label, color = BL.TextDim)
        Text(value, style = BLType.Number, color = if (highlight) BL.Green else BL.Text, maxLines = 1)
    }
}

@Composable
fun ReadoutRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The LEVEL / TILTED / HELD badge above the readouts. */
@Composable
fun StatusChip(text: String, active: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (active) BL.Green.copy(alpha = 0.14f) else BL.Surface)
            .border(BorderStroke(1.dp, if (active) BL.Green else BL.Outline), shape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BLType.Label, color = if (active) BL.Green else BL.TextDim)
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (active) BL.Green.copy(alpha = 0.14f) else BL.Surface)
            .border(BorderStroke(1.dp, if (active) BL.Green else BL.OutlineBright), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = BLType.Button, color = if (active) BL.Green else BL.Text, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (enabled) BL.Green else BL.GreenDeep)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = BLType.Button, color = if (enabled) BL.Black else BL.GreenDim, textAlign = TextAlign.Center)
    }
}

/** Soft green halo behind something important. */
fun Modifier.halo(color: Color, radiusDp: Float = 120f, alpha: Float = 0.12f): Modifier = drawBehind {
    val r = radiusDp.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = r), radius = r)
}

@Composable
fun NoSensor(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = BLType.Number, color = BL.Green)
        Box(Modifier.size(12.dp))
        Text(body, style = BLType.Body, color = BL.TextDim, textAlign = TextAlign.Center)
    }
}

/** Little line icon used on the bottom bar. [kind]: 0 level, 1 spirit, 2 angle. */
@Composable
fun ModeIcon(kind: Int, tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val w = 1.8.dp.toPx()
        when (kind) {
            0 -> {
                drawCircle(tint, radius = s * 0.42f, style = Stroke(w))
                drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.5f, s * 0.12f), Offset(s * 0.5f, s * 0.88f), w * 0.6f)
                drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.12f, s * 0.5f), Offset(s * 0.88f, s * 0.5f), w * 0.6f)
                drawCircle(tint, radius = s * 0.11f, center = Offset(s * 0.58f, s * 0.44f))
            }
            1 -> {
                drawRoundRect(tint, Offset(s * 0.04f, s * 0.32f), Size(s * 0.92f, s * 0.36f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.18f), style = Stroke(w))
                drawOval(tint, Offset(s * 0.4f, s * 0.42f), Size(s * 0.26f, s * 0.16f))
                drawLine(tint.copy(alpha = 0.5f), Offset(s * 0.3f, s * 0.36f), Offset(s * 0.3f, s * 0.64f), w * 0.6f)
            }
            else -> {
                drawLine(tint, Offset(s * 0.1f, s * 0.82f), Offset(s * 0.92f, s * 0.82f), w, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.1f, s * 0.82f), Offset(s * 0.78f, s * 0.2f), w, StrokeCap.Round)
                drawArc(tint.copy(alpha = 0.6f), -48f, 48f, false, Offset(s * 0.1f - s * 0.38f, s * 0.82f - s * 0.38f),
                    Size(s * 0.76f, s * 0.76f), style = Stroke(w * 0.7f))
            }
        }
    }
}
