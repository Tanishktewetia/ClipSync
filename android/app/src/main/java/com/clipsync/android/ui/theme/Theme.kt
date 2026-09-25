package com.clipsync.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Indigo500,
    onPrimary = White,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo600,
    secondary = Teal500,
    onSecondary = Gray900,
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal900,
    tertiary = Violet500,
    background = White,
    onBackground = Gray900,
    surface = Gray50,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray500,
    outline = Gray300,
    outlineVariant = Gray200,
    error = androidx.compose.ui.graphics.Color(0xFFB91C1C),
    onError = White,
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo400,
    onPrimary = Indigo950,
    primaryContainer = Indigo950,
    onPrimaryContainer = Indigo400,
    secondary = Teal300,
    onSecondary = Teal900,
    secondaryContainer = Teal900,
    onSecondaryContainer = Teal300,
    tertiary = Violet400,
    background = DarkBackground,
    onBackground = Gray50,
    surface = DarkSurface,
    onSurface = Gray50,
    surfaceVariant = DarkBorder,
    onSurfaceVariant = Gray400,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Red400,
    onError = DarkBackground,
)

@Composable
fun ClipSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ClipSyncTypography,
        content = content
    )
}
