package com.kevinboutwell.lightmeter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark-only, photography-focused palette: near-black surfaces with a single
// amber accent (kind to dark-adapted eyes). No dynamic color on purpose.
private val Amber = Color(0xFFFFB74D)
private val AmberDim = Color(0xFFCC8F33)
private val NearBlack = Color(0xFF0E0E10)
private val Surface1 = Color(0xFF17171A)
private val Surface2 = Color(0xFF202024)
private val TextPrimary = Color(0xFFEDEAE4)
private val TextSecondary = Color(0xFF9C988F)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF201400),
    primaryContainer = Color(0xFF3A2B10),
    onPrimaryContainer = Amber,
    secondary = AmberDim,
    onSecondary = Color(0xFF201400),
    secondaryContainer = Surface2,
    onSecondaryContainer = TextPrimary,
    tertiary = TextSecondary,
    background = NearBlack,
    onBackground = TextPrimary,
    surface = NearBlack,
    onSurface = TextPrimary,
    surfaceVariant = Surface1,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    outline = Color(0xFF3A3A40),
    outlineVariant = Color(0xFF2A2A2E),
    error = Color(0xFFFF8A65),
    onError = Color(0xFF2D0C00),
)

@Composable
fun LightMeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = LightMeterTypography,
        content = content,
    )
}
