package com.xdeev.bubblelevel.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xdeev.bubblelevel.ui.theme.BL
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val bubbleSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 280f)

/** The flat-surface bubble: concentric rings, crosshair, a target and a glass bubble that floats to the high side. */
@Composable
fun BubbleDial(
    tiltX: Float,
    tiltY: Float,
    level: Boolean,
    rangeDeg: Float,
    description: String,
    modifier: Modifier = Modifier,
) {
    val nx by animateFloatAsState(tiltX / rangeDeg, bubbleSpring, label = "nx")
    val ny by animateFloatAsState(tiltY / rangeDeg, bubbleSpring, label = "ny")
    val glow by animateFloatAsState(if (level) 1f else 0f, tween(350), label = "glow")

    Canvas(modifier.semantics { contentDescription = description }) {
        val s = min(size.width, size.height)
        val c = center
        val r = s / 2f * 0.97f
        val hair = 1.dp.toPx()

        // ambient glow
        drawCircle(
            Brush.radialGradient(listOf(BL.Green.copy(alpha = 0.04f + 0.16f * glow), Color.Transparent), c, r * 1.08f),
            radius = r * 1.08f, center = c,
        )
        // rings
        for (f in floatArrayOf(0.66f, 0.33f)) drawCircle(BL.Outline, radius = r * f, center = c, style = Stroke(hair))
        drawCircle(lerp(BL.OutlineBright, BL.Green, 0.25f + 0.75f * glow), radius = r, center = c, style = Stroke(2.dp.toPx()))
        // ticks just inside the outer ring, a long one every 90 degrees
        for (i in 0 until 36) {
            val a = Math.toRadians(i * 10.0)
            val major = i % 9 == 0
            val r1 = r * (if (major) 0.90f else 0.94f)
            val r2 = r * 0.975f
            drawLine(
                if (major) BL.TextDim else BL.OutlineBright,
                c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()),
                c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()),
                hair,
            )
        }
        // crosshair (leaves the middle open)
        val gap = r * 0.2f
        drawLine(BL.Outline, Offset(c.x - r * 0.88f, c.y), Offset(c.x - gap, c.y), hair)
        drawLine(BL.Outline, Offset(c.x + gap, c.y), Offset(c.x + r * 0.88f, c.y), hair)
        drawLine(BL.Outline, Offset(c.x, c.y - r * 0.88f), Offset(c.x, c.y - gap), hair)
        drawLine(BL.Outline, Offset(c.x, c.y + gap), Offset(c.x, c.y + r * 0.88f), hair)

        // target the bubble has to sit in
        val bubbleR = r * 0.115f
        val targetR = bubbleR * 1.45f
        drawCircle(
            lerp(BL.GreenDim, BL.Green, glow).copy(alpha = 0.55f + 0.45f * glow),
            radius = targetR, center = c, style = Stroke(2.dp.toPx()),
        )
        if (glow > 0.01f) drawCircle(BL.Green.copy(alpha = 0.10f * glow), radius = targetR, center = c)

        // bubble: moves toward the high side, clamped inside the ring
        var px = nx
        var py = -ny
        val len = hypot(px, py)
        if (len > 1f) { px /= len; py /= len }
        val travel = r - bubbleR * 1.5f
        val bc = Offset(c.x + px * travel, c.y + py * travel)
        drawCircle(
            Brush.radialGradient(listOf(BL.Green.copy(alpha = 0.34f), Color.Transparent), bc, bubbleR * 2.8f),
            radius = bubbleR * 2.8f, center = bc,
        )
        drawCircle(
            Brush.radialGradient(
                0f to BL.GreenSoft, 0.35f to BL.Green, 1f to BL.GreenDim,
                center = bc + Offset(-bubbleR * 0.3f, -bubbleR * 0.35f), radius = bubbleR * 1.5f,
            ),
            radius = bubbleR, center = bc,
        )
        drawCircle(BL.GreenSoft.copy(alpha = 0.55f), radius = bubbleR, center = bc, style = Stroke(1.2.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.55f), radius = bubbleR * 0.17f, center = bc + Offset(-bubbleR * 0.36f, -bubbleR * 0.40f))
    }
}

/**
 * A glass vial with a bubble. [valueDeg] positive moves the bubble right (horizontal) or up (vertical).
 * [active] = false dims it when the reading does not apply to the way the phone is held.
 */
@Composable
fun Vial(
    valueDeg: Float,
    rangeDeg: Float,
    level: Boolean,
    vertical: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val n by animateFloatAsState((valueDeg / rangeDeg).coerceIn(-1f, 1f), bubbleSpring, label = "vial")
    val glow by animateFloatAsState(if (level && active) 1f else 0f, tween(350), label = "vglow")
    val dim by animateFloatAsState(if (active) 1f else 0.35f, tween(250), label = "vdim")

    Canvas(modifier.semantics { contentDescription = description }) {
        val along = if (vertical) size.height else size.width
        val thick = if (vertical) size.width else size.height
        val hair = 1.dp.toPx()
        fun at(a: Float, b: Float) = if (vertical) Offset(b, along - a) else Offset(a, b)
        fun box(a: Float, b: Float) = if (vertical) Size(b, a) else Size(a, b)

        // glass tube
        val corner = CornerRadius(thick / 2f)
        drawRoundRect(
            Brush.linearGradient(
                listOf(BL.SurfaceHigh, BL.Black),
                start = Offset.Zero,
                end = if (vertical) Offset(thick, 0f) else Offset(0f, thick),
            ),
            size = size, cornerRadius = corner,
        )
        drawRoundRect(
            lerp(BL.OutlineBright, BL.Green, 0.3f + 0.7f * glow).copy(alpha = dim),
            size = size, cornerRadius = corner, style = Stroke(1.5.dp.toPx()),
        )
        // glass reflection
        drawLine(
            Color.White.copy(alpha = 0.07f * dim),
            at(thick * 0.55f, thick * 0.2f), at(along - thick * 0.55f, thick * 0.2f),
            hair * 1.5f, StrokeCap.Round,
        )
        // centre marks the bubble has to sit between
        val bubbleLen = thick * 1.15f
        val markGap = bubbleLen * 0.58f
        for (sgn in floatArrayOf(-1f, 1f)) {
            val a = along / 2f + sgn * markGap
            drawLine(
                lerp(BL.GreenDim, BL.Green, glow).copy(alpha = 0.55f * dim + 0.45f * glow),
                at(a, thick * 0.14f), at(a, thick * 0.86f), 2.dp.toPx(), StrokeCap.Round,
            )
        }
        // bubble (its glow is clipped to the glass so it never spills outside the tube)
        val travel = along / 2f - thick * 0.75f
        val bc = at(along / 2f + n * travel, thick / 2f)
        val bs = box(bubbleLen, thick * 0.62f)
        val topLeft = Offset(bc.x - bs.width / 2f, bc.y - bs.height / 2f)
        val tube = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, corner)) }
        clipPath(tube) {
            drawCircle(
                Brush.radialGradient(listOf(BL.Green.copy(alpha = 0.28f * dim), Color.Transparent), bc, thick * 0.95f),
                radius = thick * 0.95f, center = bc,
            )
        }
        drawOval(
            Brush.radialGradient(
                0f to BL.GreenSoft.copy(alpha = dim), 0.4f to BL.Green.copy(alpha = dim), 1f to BL.GreenDim.copy(alpha = dim),
                center = bc + Offset(-bs.width * 0.14f, -bs.height * 0.18f), radius = maxOf(bs.width, bs.height) * 0.75f,
            ),
            topLeft = topLeft, size = bs,
        )
        drawOval(BL.GreenSoft.copy(alpha = 0.5f * dim), topLeft = topLeft, size = bs, style = Stroke(1.dp.toPx()))
    }
}

/** Half-circle clinometer: 0° at the top, ±90° at the sides. */
@Composable
fun AngleGauge(
    angleDeg: Float,
    level: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val a by animateFloatAsState(angleDeg.coerceIn(-90f, 90f), spring(dampingRatio = 0.8f, stiffness = 320f), label = "angle")
    val glow by animateFloatAsState(if (level) 1f else 0f, tween(350), label = "aglow")
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }

    Canvas(modifier.semantics { contentDescription = description }) {
        val r = min(size.width / 2f, size.height * 0.92f) * 0.96f
        val c = Offset(size.width / 2f, size.height / 2f + r / 2f)
        val track = 5.dp.toPx()
        val hair = 1.dp.toPx()
        val arcTopLeft = c - Offset(r, r)
        val arcSize = Size(2 * r, 2 * r)

        drawCircle(
            Brush.radialGradient(listOf(BL.Green.copy(alpha = 0.03f + 0.12f * glow), Color.Transparent), c, r * 1.1f),
            radius = r * 1.1f, center = c,
        )
        // track and the green sweep from 0 to the current angle
        drawArc(BL.Outline, 180f, 180f, false, arcTopLeft, arcSize, style = Stroke(track, cap = StrokeCap.Round))
        drawArc(BL.Green, 270f, a, false, arcTopLeft, arcSize, style = Stroke(track, cap = StrokeCap.Round))
        // ticks and labels
        paint.textSize = 11.dp.toPx()
        for (d in -90..90 step 5) {
            val ang = Math.toRadians(270.0 + d)
            val major = d % 30 == 0
            val medium = d % 10 == 0
            val len = r * (if (major) 0.15f else if (medium) 0.095f else 0.05f)
            val r1 = r - track * 1.6f - len
            val r2 = r - track * 1.6f
            val col = when {
                d == 0 -> BL.Green
                major -> BL.TextDim
                else -> BL.OutlineBright
            }
            drawLine(
                col,
                c + Offset((cos(ang) * r1).toFloat(), (sin(ang) * r1).toFloat()),
                c + Offset((cos(ang) * r2).toFloat(), (sin(ang) * r2).toFloat()),
                if (major) 1.6.dp.toPx() else hair,
            )
            if (major) {
                val lr = r1 - 12.dp.toPx()
                paint.color = (if (d == 0) BL.Green else BL.TextDim).toArgb()
                drawIntoCanvas {
                    it.nativeCanvas.drawText(
                        kotlin.math.abs(d).toString(),
                        c.x + (cos(ang) * lr).toFloat(),
                        c.y + (sin(ang) * lr).toFloat() + paint.textSize * 0.35f,
                        paint,
                    )
                }
            }
        }
        // needle
        val na = Math.toRadians(270.0 + a)
        val tip = c + Offset((cos(na) * r * 0.80f).toFloat(), (sin(na) * r * 0.80f).toFloat())
        if (glow > 0.01f) drawLine(BL.Green.copy(alpha = 0.25f * glow), c, tip, 12.dp.toPx(), StrokeCap.Round)
        drawLine(BL.Green, c, tip, 3.dp.toPx(), StrokeCap.Round)
        drawCircle(BL.Green, radius = 9.dp.toPx(), center = c)
        drawCircle(BL.Black, radius = 3.5.dp.toPx(), center = c)
    }
}
