package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnCyanDark,
    primaryContainer = Color(0xFF0E3A4D),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = EmeraldReady,
    onSecondary = Color(0xFF03261B),
    tertiary = AmberWarn,
    onTertiary = Color(0xFF2B1900),
    error = RoseAlert,
    onError = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianOutline,
  )

@Composable
fun CyberBrowserTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = CyberDarkColorScheme, typography = Typography, content = content)
}
