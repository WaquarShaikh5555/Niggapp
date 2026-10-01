package com.smilebeat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = TextPrimary,
    primaryContainer = NeonPurpleDark,
    onPrimaryContainer = NeonPurpleLight,
    secondary = NeonRed,
    onSecondary = TextPrimary,
    secondaryContainer = NeonRedDark,
    tertiary = NeonCyan,
    onTertiary = BlackVoid,
    background = BlackVoid,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorGlow
)

@Composable
fun SmileBeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
