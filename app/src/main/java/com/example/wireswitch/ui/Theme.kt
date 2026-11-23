package com.example.wireswitch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 다크 테마 팔레트.
val Teal = Color(0xFF1D9E75)
val TealSoft = Color(0xFF6FD3AE)
val Amber = Color(0xFFE2B44F)
val Danger = Color(0xFFE5706B)
val Ink = Color(0xFF0E1412)
val Surface1 = Color(0xFF161E1B)
val Surface2 = Color(0xFF1F2A26)
val TextPrimary = Color(0xFFE6EDE9)
val TextMuted = Color(0xFF93A29B)

private val colors = darkColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF14785A),
    onPrimaryContainer = Color.White,
    secondary = TealSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF32433D),
    error = Danger,
    onError = Color.White,
)

@Composable
fun WireSwitchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
