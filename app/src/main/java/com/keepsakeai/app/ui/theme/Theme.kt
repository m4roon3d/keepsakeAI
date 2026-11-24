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
    /** Lagoon's calmer sibling: muted teal and slate with a touch of brass. */
    val Harbour = Palette(
        "harbour", "Harbour", "Deep teal, slate & brass",
        primary = Color(0xFF2F6F73), primaryDeep = Color(0xFF245A5E),
        secondary = Color(0xFF5B9AA0), secondaryBright = Color(0xFF9CC5C8),
        tertiary = Color(0xFF5F7A99), tertiaryLight = Color(0xFFA9B9CC),
        accent = Color(0xFFB8A27A), titleEnd = Color(0xFF7FB0B3),
        lightBase = Color(0xFFF4F7F7), lightBaseBottom = Color(0xFFE9EFEF),
        darkBase = Color(0xFF0A1315), darkBaseBottom = Color(0xFF070E10),
        glows = listOf(Color(0xFF3F7F83), Color(0xFF5F7A99), Color(0xFFB8A27A)),
    )
    val all = listOf(Lagoon, Harbour)
    fun byId(id: String?) = all.firstOrNull { it.id == id } ?: Lagoon
}

enum class ThemeMode(val label: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }

/** App-wide, persisted theme choice. Reading it inside composition recomposes on change. */
object ThemeStore {
    private const val PREFS = "theme_prefs"
    var palette by mutableStateOf(Palettes.Lagoon)
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

val LocalFlow = staticCompositionLocalOf { flowColors(Palettes.Lagoon, false) }

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
