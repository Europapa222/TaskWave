package com.taskwave.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// TaskWave brand colors — purple/blue gradient palette
private val TW_Primary = Color(0xFF6C63FF)
private val TW_PrimaryDark = Color(0xFF8B85FF)
private val TW_Secondary = Color(0xFF4FC3F7)
private val TW_Error = Color(0xFFFF5252)

private val LightColors = lightColorScheme(
    primary = TW_Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FF),
    onPrimaryContainer = Color(0xFF2D0092),
    secondary = TW_Secondary,
    onSecondary = Color.White,
    background = Color(0xFFF8F7FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0EEFF),
    onSurface = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFF5A5A7A),
    outline = Color(0xFFD0CFDF),
    error = TW_Error,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = TW_PrimaryDark,
    onPrimary = Color(0xFF1A0060),
    primaryContainer = Color(0xFF3D2B8F),
    onPrimaryContainer = Color(0xFFD8D3FF),
    secondary = TW_Secondary,
    onSecondary = Color(0xFF00374A),
    background = Color(0xFF0F0E1A),
    surface = Color(0xFF1A1829),
    surfaceVariant = Color(0xFF252338),
    onSurface = Color(0xFFECEBFF),
    onSurfaceVariant = Color(0xFFAAABCC),
    outline = Color(0xFF3A3850),
    error = Color(0xFFFF6E6E),
    onError = Color.White
)

@Composable
fun TaskWaveTheme(
    darkModeOverride: String = "system",
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (darkModeOverride) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}
