package com.keepsakeai.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Living Flow Luminous Palette
private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF0EA5E9),
    secondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFFC084FC),
    background = Color(0xFFF4F6FF),
    surface = Color(0xD9FFFFFF),
    surfaceVariant = Color(0xFFEBF0FA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF38BDF8),
    secondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD8B4FE),
    background = Color(0xFF090D1A),
    surface = Color(0xB8171F34),
    surfaceVariant = Color(0xFF1E293B)
)

private val LivingFlowShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp)
)

@Composable
fun MindSpaceTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = LivingFlowShapes,
        content = content
    )
}
