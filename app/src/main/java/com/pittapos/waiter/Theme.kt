package com.pittapos.waiter

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Ίδια ταυτότητα με το ταμείο (PosTheme.xaml) — κόκκινο #EC3013, μελάνι #201E1D, φόντο #F3F2F2.
val BrandRed = Color(0xFFEC3013)
val BrandRedDark = Color(0xFFAE1800)
val BrandRedContainer = Color(0xFFFFF2EF)
val BrandInk = Color(0xFF201E1D)
val BrandBg = Color(0xFFF3F2F2)
val BrandGreen = Color(0xFF2E7D32)
val BrandGreenContainer = Color(0xFFE3F2E4)

private val LightColors = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = BrandRedContainer,
    onPrimaryContainer = BrandRedDark,
    secondary = BrandInk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEBEA),
    onSecondaryContainer = BrandInk,
    tertiary = BrandGreen,
    tertiaryContainer = BrandGreenContainer,
    onTertiaryContainer = Color(0xFF1B5E20),
    background = BrandBg,
    onBackground = BrandInk,
    surface = Color.White,
    onSurface = BrandInk,
    surfaceVariant = Color(0xFFEDEBEA),
    onSurfaceVariant = Color(0xFF605D5D),
    outline = Color(0xFFB3B0B0),
    outlineVariant = Color(0xFFDBD8D8),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFEDEA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8266),
    onPrimary = Color(0xFF4A0E00),
    primaryContainer = Color(0xFF6B1A00),
    onPrimaryContainer = Color(0xFFFFDBD2),
    secondary = Color(0xFFD9D6D6),
    onSecondary = Color(0xFF201E1D),
    secondaryContainer = Color(0xFF3A3736),
    onSecondaryContainer = Color(0xFFE9E7E6),
    tertiary = Color(0xFF8BC98F),
    tertiaryContainer = Color(0xFF1B4020),
    onTertiaryContainer = Color(0xFFC8EACB),
    background = Color(0xFF1C1B1B),
    onBackground = Color(0xFFE9E7E6),
    surface = Color(0xFF262424),
    onSurface = Color(0xFFE9E7E6),
    surfaceVariant = Color(0xFF3A3736),
    onSurfaceVariant = Color(0xFFC9C6C5),
    outline = Color(0xFF6F6B6A),
    outlineVariant = Color(0xFF4A4746),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PittaWaiterTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
