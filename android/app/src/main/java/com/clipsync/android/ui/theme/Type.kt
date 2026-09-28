package com.clipsync.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)
val ClipSyncTypography = Typography(
    displaySmall = type(36, 44, FontWeight.SemiBold),
    headlineLarge = type(28, 36, FontWeight.SemiBold),
    headlineMedium = type(24, 32, FontWeight.SemiBold),
    headlineSmall = type(22, 28, FontWeight.SemiBold),
    titleLarge = type(22, 28, FontWeight.SemiBold),
    titleMedium = type(18, 24, FontWeight.SemiBold),
    titleSmall = type(16, 22, FontWeight.SemiBold),
    bodyLarge = type(16, 24), bodyMedium = type(15, 22), bodySmall = type(13, 19),
    labelLarge = type(15, 22, FontWeight.SemiBold),
    labelMedium = type(13, 19, FontWeight.Medium), labelSmall = type(12, 16, FontWeight.Medium),
)
