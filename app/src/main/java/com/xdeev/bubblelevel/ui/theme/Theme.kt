package com.xdeev.bubblelevel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pure black canvas, one neon green accent. Everything else is a quiet shade of the same green-grey. */
object BL {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF070B09)
    val SurfaceHigh = Color(0xFF0E1411)
    val Outline = Color(0xFF1B2620)
    val OutlineBright = Color(0xFF2A3A31)
    val Green = Color(0xFF3DFF8B)
    val GreenSoft = Color(0xFF8DFFBB)
    val GreenDim = Color(0xFF1E9A57)
    val GreenDeep = Color(0xFF0A3B20)
    val Text = Color(0xFFE8F4EC)
    val TextDim = Color(0xFF74847B)
}

object BLType {
    /** Big tabular numerals so digits never jitter while the value changes. */
    val Hero = TextStyle(fontSize = 76.sp, fontWeight = FontWeight.Thin, letterSpacing = (-2).sp, fontFeatureSettings = "tnum")
    val Number = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")
    val Label = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.8.sp)
    val Body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp)
    val Button = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
}

private val scheme = darkColorScheme(
    primary = BL.Green,
    onPrimary = BL.Black,
    secondary = BL.GreenDim,
    background = BL.Black,
    onBackground = BL.Text,
    surface = BL.SurfaceHigh,
    onSurface = BL.Text,
    surfaceVariant = BL.Surface,
    onSurfaceVariant = BL.TextDim,
    outline = BL.Outline,
)

@Composable
fun BubbleLevelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
