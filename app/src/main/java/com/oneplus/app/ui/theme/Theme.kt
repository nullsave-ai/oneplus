package com.oneplus.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF2563EB), background = Color(0xFFF6F8FC), surface = Color.White,
    surfaceVariant = Color(0xFFEAEFF8), onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B), outlineVariant = Color(0xFFDCE3EF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5B9BFF), background = Color(0xFF0B0F17), surface = Color(0xFF121826),
    surfaceVariant = Color(0xFF1B2333), onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8), outlineVariant = Color(0xFF263045),
)
private val Type = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun OnePlusTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, content = content)
}
