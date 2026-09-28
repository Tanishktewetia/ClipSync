package com.clipsync.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Complete surface roles: no default purple tint on cards, dialogs or app bars.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF245C96), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EFF8), onPrimaryContainer = Color(0xFF173C63),
    secondary = Color(0xFF245C96), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEFF3), onSecondaryContainer = Color(0xFF20242B),
    tertiary = Color(0xFF326751), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7F1EB), onTertiaryContainer = Color(0xFF1E4936),
    background = Color(0xFFF7F8FA), onBackground = Color(0xFF20242B),
    surface = Color.White, onSurface = Color(0xFF20242B),
    surfaceVariant = Color(0xFFECEFF3), onSurfaceVariant = Color(0xFF505965),
    surfaceDim = Color(0xFFE2E5E9), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFECEFF3), surfaceContainerHighest = Color(0xFFE2E5E9),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF74808D), outlineVariant = Color(0xFFD7DCE3),
    error = Color(0xFFAE2929), onError = Color.White,
    errorContainer = Color(0xFFFCECEC), onErrorContainer = Color(0xFF7C2020),
)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9CC7FF), onPrimary = Color(0xFF102A45),
    primaryContainer = Color(0xFF263B52), onPrimaryContainer = Color(0xFFD8E8FF),
    secondary = Color(0xFF9CC7FF), onSecondary = Color(0xFF102A45),
    secondaryContainer = Color(0xFF2B3037), onSecondaryContainer = Color(0xFFF5F6F8),
    tertiary = Color(0xFFA0D3B5), onTertiary = Color(0xFF173D29),
    tertiaryContainer = Color(0xFF253C30), onTertiaryContainer = Color(0xFFCBEDD8),
    background = Color(0xFF181A1D), onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF23262B), onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF2B3037), onSurfaceVariant = Color(0xFFC1C7D0),
    surfaceDim = Color(0xFF181A1D), surfaceBright = Color(0xFF353B44),
    surfaceContainerLowest = Color(0xFF141619), surfaceContainerLow = Color(0xFF1E2024),
    surfaceContainer = Color(0xFF23262B), surfaceContainerHigh = Color(0xFF2B3037), surfaceContainerHighest = Color(0xFF353B44),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF87919E), outlineVariant = Color(0xFF444B55),
    error = Color(0xFFFFB4B4), onError = Color(0xFF570F16),
    errorContainer = Color(0xFF392629), onErrorContainer = Color(0xFFFFD9D9),
)
@Composable
fun ClipSyncTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ClipSyncTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) { content() }
        })
}
