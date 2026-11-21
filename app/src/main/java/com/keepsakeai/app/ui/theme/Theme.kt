package com.keepsakeai.app.ui.theme

import android.content.Context
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palettes ─────────────────────────────────────────────────────────

@Immutable
data class Palette(
    val id: String,
    val name: String,
    val tagline: String,
    val primary: Color,
    val primaryDeep: Color,
    val secondary: Color,
    val secondaryBright: Color,
    val tertiary: Color,
    val tertiaryLight: Color,
    val accent: Color,
    val titleEnd: Color,
    val lightBase: Color,
    val lightBaseBottom: Color,
    val darkBase: Color,
    val darkBaseBottom: Color,
    val glows: List<Color>, // three glow hues for the backdrop
)

object Palettes {
    val Aurora = Palette(
        "aurora", "Aurora", "Indigo, sky & orchid",
        primary = Color(0xFF6366F1), primaryDeep = Color(0xFF4F46E5),
        secondary = Color(0xFF38BDF8), secondaryBright = Color(0xFF22D3EE),
        tertiary = Color(0xFFC084FC), tertiaryLight = Color(0xFFA78BFA),
        accent = Color(0xFFF472B6), titleEnd = Color(0xFFD946EF),
        lightBase = Color(0xFFF6F8FF), lightBaseBottom = Color(0xFFEEF2FB),
        darkBase = Color(0xFF0B1020), darkBaseBottom = Color(0xFF090D1A),
        glows = listOf(Color(0xFFA78BFA), Color(0xFF38BDF8), Color(0xFFF472B6)),
    )
    val Sunset = Palette(
        "sunset", "Sunset", "Coral, saffron & magenta",
        primary = Color(0xFFF43F5E), primaryDeep = Color(0xFFE11D48),
        secondary = Color(0xFFFB923C), secondaryBright = Color(0xFFFBBF24),
        tertiary = Color(0xFFD946EF), tertiaryLight = Color(0xFFF0ABFC),
        accent = Color(0xFFF59E0B), titleEnd = Color(0xFFFB7185),
        lightBase = Color(0xFFFFF7F3), lightBaseBottom = Color(0xFFFDEEE8),
        darkBase = Color(0xFF1A0B12), darkBaseBottom = Color(0xFF12070D),
        glows = listOf(Color(0xFFFB923C), Color(0xFFF43F5E), Color(0xFFD946EF)),
    )
    val Lagoon = Palette(
        "lagoon", "Lagoon", "Teal, aqua & ocean blue",
        primary = Color(0xFF0D9488), primaryDeep = Color(0xFF0F766E),
        secondary = Color(0xFF06B6D4), secondaryBright = Color(0xFF67E8F9),
        tertiary = Color(0xFF3B82F6), tertiaryLight = Color(0xFF93C5FD),
        accent = Color(0xFF10B981), titleEnd = Color(0xFF22D3EE),
        lightBase = Color(0xFFF2FBFA), lightBaseBottom = Color(0xFFE6F5F4),
        darkBase = Color(0xFF061417), darkBaseBottom = Color(0xFF040E10),
        glows = listOf(Color(0xFF2DD4BF), Color(0xFF38BDF8), Color(0xFF3B82F6)),
    )
    val Orchid = Palette(
        "orchid", "Orchid", "Violet, fuchsia & rose",
        primary = Color(0xFF8B5CF6), primaryDeep = Color(0xFF7C3AED),
        secondary = Color(0xFFEC4899), secondaryBright = Color(0xFFF9A8D4),
        tertiary = Color(0xFFD946EF), tertiaryLight = Color(0xFFC4B5FD),
        accent = Color(0xFFF472B6), titleEnd = Color(0xFF6366F1),
        lightBase = Color(0xFFFAF5FF), lightBaseBottom = Color(0xFFF3EAFD),
        darkBase = Color(0xFF120A1F), darkBaseBottom = Color(0xFF0C0716),
        glows = listOf(Color(0xFFC084FC), Color(0xFFF472B6), Color(0xFF8B5CF6)),
    )
    val Noir = Palette(
        "noir", "Noir", "Graphite & gold",
        primary = Color(0xFFCA8A04), primaryDeep = Color(0xFFA16207),
        secondary = Color(0xFFF59E0B), secondaryBright = Color(0xFFFCD34D),
        tertiary = Color(0xFF78716C), tertiaryLight = Color(0xFFD6D3D1),
        accent = Color(0xFFF97316), titleEnd = Color(0xFFFDE68A),
        lightBase = Color(0xFFF8F7F4), lightBaseBottom = Color(0xFFEFEDE7),
        darkBase = Color(0xFF0E0E10), darkBaseBottom = Color(0xFF08080A),
        glows = listOf(Color(0xFFFBBF24), Color(0xFF78716C), Color(0xFFF59E0B)),
    )
    val all = listOf(Aurora, Sunset, Lagoon, Orchid, Noir)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Aurora
}

enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }

/** App-wide, persisted theme choice. Reading it inside composition recomposes on change. */
object ThemeStore {
    private const val PREFS = "theme_prefs"
    var palette by mutableStateOf(Palettes.Aurora)
        private set
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        palette = Palettes.byId(p.getString("palette", null))
        mode = runCatching { ThemeMode.valueOf(p.getString("mode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setPalette(context: Context, value: Palette) {
        palette = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("palette", value.id).apply()
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("mode", value.name).apply()
    }
}

// ── Living Flow colours (accents follow the active palette) ──────────
object Flow {
    private val p get() = ThemeStore.palette

    val Indigo get() = p.primary
    val IndigoDeep get() = p.primaryDeep
    val Sky get() = p.secondary
    val Cyan get() = p.secondaryBright
    val Violet get() = p.tertiaryLight
    val Purple get() = p.tertiary
    val Pink get() = p.accent

    // Semantic colours stay fixed across themes (money, warnings, errors).
    val Emerald = Color(0xFF10B981)
    val Teal = Color(0xFF0D9488)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFF43F5E)
    val Ink = Color(0xFF0F172A)

    /** Iridescent orb used for the Pilot button and primary AI actions. */
    val auroraSweep get() = Brush.sweepGradient(listOf(p.primary, p.secondary, p.tertiary, p.accent, p.primary))
    val primaryGradient get() = Brush.horizontalGradient(listOf(p.primaryDeep, p.secondary))
    val suggestGradient get() = Brush.linearGradient(listOf(p.primary, p.secondary, p.tertiary))
    val spendGradient = Brush.linearGradient(listOf(Emerald, Teal, Color(0xFF0E7490)))
    val lensGradient get() = Brush.verticalGradient(listOf(Color(0xFF0F172A), p.primaryDeep.copy(alpha = 0.55f).compositeOver(Color(0xFF0F172A)), Color(0xFF020617)))
    val titleGradient get() = Brush.horizontalGradient(listOf(p.primary, p.secondary, p.titleEnd))
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
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

private fun flowColors(p: Palette, dark: Boolean) = if (dark) FlowColors(
    dark = true,
    base = p.darkBase,
    baseBottom = p.darkBaseBottom,
    glow1 = p.glows[0].copy(alpha = 0.28f),
    glow2 = p.glows[1].copy(alpha = 0.18f),
    glow3 = p.glows[2].copy(alpha = 0.16f),
    glass = Color(0xA8171F34),
    glassBorder = Color(0x1AFFFFFF),
    text = Color(0xFFF8FAFC),
    muted = Color(0xFF94A3B8),
    chipSelected = Color(0xFFF8FAFC),
    onChipSelected = Color(0xFF0F172A),
    divider = Color(0x1AFFFFFF),
) else FlowColors(
    dark = false,
    base = p.lightBase,
    baseBottom = p.lightBaseBottom,
    glow1 = p.glows[0].copy(alpha = 0.28f),
    glow2 = p.glows[1].copy(alpha = 0.24f),
    glow3 = p.glows[2].copy(alpha = 0.16f),
    glass = Color(0xB8FFFFFF),
    glassBorder = Color(0xD9FFFFFF),
    text = Color(0xFF0F172A),
    muted = Color(0xFF64748B),
    chipSelected = Color(0xFF0F172A),
    onChipSelected = Color.White,
    divider = Color(0x33CBD5E1),
)

val LocalFlow = staticCompositionLocalOf { flowColors(Palettes.Aurora, false) }

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

/** True when the active theme mode resolves to dark. */
@Composable
fun isFlowDark(): Boolean = when (ThemeStore.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MindSpaceTheme(content: @Composable () -> Unit) {
    val dark = isFlowDark()
    val p = ThemeStore.palette
    val scheme = if (dark) darkColorScheme(
        primary = p.primary, onPrimary = Color.White, secondary = p.secondary, tertiary = p.tertiary,
        background = p.darkBase, surface = Color(0xFF111827), onSurface = Color(0xFFF8FAFC), onSurfaceVariant = Color(0xFF94A3B8),
    ) else lightColorScheme(
        primary = p.primaryDeep, onPrimary = Color.White, secondary = p.secondary, tertiary = p.tertiary,
        background = p.lightBase, surface = Color.White, onSurface = Color(0xFF0F172A), onSurfaceVariant = Color(0xFF64748B),
    )
    CompositionLocalProvider(LocalFlow provides flowColors(p, dark)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FlowTypography,
            shapes = LivingFlowShapes,
            content = content
        )
    }
}
