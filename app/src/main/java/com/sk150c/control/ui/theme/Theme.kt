package com.sk150c.control.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF4FD1C5)
val TealDark = Color(0xFF0B8A7B)
val Amber = Color(0xFFF6AD55)
val Danger = Color(0xFFE53E3E)
val BgDark = Color(0xFF101820)
val SurfaceDark = Color(0xFF1B2530)

private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = Amber,
    background = BgDark,
    surface = SurfaceDark,
    error = Danger
)

private val LightColors = lightColorScheme(
    primary = TealDark,
    secondary = Amber,
    error = Danger
)

@Composable
fun SK150CTheme(useDark: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (useDark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
