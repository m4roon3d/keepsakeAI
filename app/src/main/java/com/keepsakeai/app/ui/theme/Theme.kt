package com.keepsakeai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Living Flow palette ──────────────────────────────────────────────
object Flow {
    val Indigo = Color(0xFF6366F1)
    val IndigoDeep = Color(0xFF4F46E5)
    val Sky = Color(0xFF38BDF8)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFFA78BFA)
    val Purple = Color(0xFFC084FC)
    val Pink = Color(0xFFF472B6)
    val Emerald = Color(0xFF10B981)
    val Teal = Color(0xFF0D9488)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFF43F5E)
    val Ink = Color(0xFF0F172A)

    /** Iridescent orb used for the Pilot button and primary AI actions. */
    val auroraSweep = Brush.sweepGradient(listOf(Indigo, Sky, Purple, Pink, Indigo))
    val primaryGradient = Brush.horizontalGradient(listOf(IndigoDeep, Sky))
    val suggestGradient = Brush.linearGradient(listOf(Indigo, Color(0xFF0EA5E9), Color(0xFF7C3AED)))
    val spendGradient = Brush.linearGradient(listOf(Emerald, Teal, Color(0xFF0E7490)))
    val lensGradient = Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF020617)))
    val titleGradient = Brush.horizontalGradient(listOf(Indigo, Sky, Color(0xFFD946EF)))
}

@Immutable
data class FlowColors(
    val dark: Boolean,
    val base: Color,
    val baseBottom: Color,
    val glow1: Color,
    val glow2: Color,
    val glow3: Color,
    val glass: Color,
    val glassBorder: Color,
    val text: Color,
    val muted: Color,
    val chipSelected: Color,
    val onChipSelected: Color,
    val divider: Color,
)

private val LightFlow = FlowColors(
    dark = false,
    base = Color(0xFFF6F8FF),
    baseBottom = Color(0xFFEEF2FB),
    glow1 = Color(0x47A78BFA),
    glow2 = Color(0x3D38BDF8),
    glow3 = Color(0x29F472B6),
    glass = Color(0xB8FFFFFF),
    glassBorder = Color(0xD9FFFFFF),
    text = Color(0xFF0F172A),
    muted = Color(0xFF64748B),
    chipSelected = Color(0xFF0F172A),
    onChipSelected = Color.White,
    divider = Color(0x33CBD5E1),
)

private val DarkFlow = FlowColors(
    dark = true,
    base = Color(0xFF0B1020),
    baseBottom = Color(0xFF090D1A),
    glow1 = Color(0x47818CF8),
    glow2 = Color(0x2E22D3EE),
    glow3 = Color(0x29C084FC),
    glass = Color(0xA8171F34),
    glassBorder = Color(0x1AFFFFFF),
    text = Color(0xFFF8FAFC),
    muted = Color(0xFF94A3B8),
    chipSelected = Color(0xFFF8FAFC),
    onChipSelected = Color(0xFF0F172A),
    divider = Color(0x1AFFFFFF),
)

val LocalFlow = staticCompositionLocalOf { LightFlow }

private val LightColors = lightColorScheme(
    primary = Flow.IndigoDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF0EA5E9),
    tertiary = Flow.Purple,
    background = Color(0xFFF6F8FF),
    surface = Color(0xFFFFFFFF),
    onSurface = Flow.Ink,
    onSurfaceVariant = Color(0xFF64748B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Flow.Ink,
    primaryContainer = Color(0xFF1E1B4B),
    secondary = Flow.Sky,
    tertiary = Color(0xFFD8B4FE),
    background = Color(0xFF0B1020),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8),
)

private val FlowTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp),
)

private val LivingFlowShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp)
)

@Composable
fun MindSpaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFlow provides if (darkTheme) DarkFlow else LightFlow) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = FlowTypography,
            shapes = LivingFlowShapes,
            content = content
        )
    }
}
