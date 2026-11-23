package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PhosphorEmerald,
    onPrimary = OnPhosphorEmerald,
    primaryContainer = Color(0xFF0A3826),
    onPrimaryContainer = PhosphorEmerald,
    secondary = ElectricCyan,
    onSecondary = OnElectricCyan,
    secondaryContainer = Color(0xFF0E2F44),
    onSecondaryContainer = ElectricCyan,
    tertiary = PrivacyViolet,
    onTertiary = OnPrivacyViolet,
    tertiaryContainer = Color(0xFF34194D),
    onTertiaryContainer = PrivacyViolet,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    error = AlertCoral,
    onError = Color(0xFF3B0808)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = VioletDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDCE5F0),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun TermuxBrowserTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
