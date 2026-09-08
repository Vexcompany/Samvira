package com.vexcompany.samvira.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo700,
    onPrimary = Color.White,
    secondary = Teal400,
    tertiary = Amber400,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    secondary = Teal400,
    tertiary = Amber400,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
)

/**
 * SAMVIRA theme.
 *
 * Dynamic (Material You) color is intentionally not used: SAMVIRA keeps a
 * consistent brand identity across devices.
 */
@Composable
fun SamviraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SamviraTypography,
        content = content,
    )
}
