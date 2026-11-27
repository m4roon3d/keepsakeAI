package com.keepsakeai.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.keepsakeai.app.ui.theme.Palette

/** How one placed widget looks: how see-through its background is, what it's tinted with, and light or dark text. */
data class WidgetStyle(val opacity: Int = 55, val tint: String = TINT_THEME, val lightText: Boolean = true) {
    companion object {
        const val TINT_THEME = "theme"
        const val TINT_BLACK = "black"
        const val TINT_WHITE = "white"
    }
}

/** Per-widget styles, keyed by the launcher's widget id. */
object WidgetStyleStore {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    fun get(c: Context, id: Int): WidgetStyle = prefs(c).let { p ->
        WidgetStyle(p.getInt("$id.opacity", 55), p.getString("$id.tint", WidgetStyle.TINT_THEME) ?: WidgetStyle.TINT_THEME, p.getBoolean("$id.light", true))
    }

    fun save(c: Context, id: Int, s: WidgetStyle) {
        prefs(c).edit().putInt("$id.opacity", s.opacity.coerceIn(0, 100)).putString("$id.tint", s.tint).putBoolean("$id.light", s.lightText).apply()
    }

    fun remove(c: Context, ids: IntArray) {
        val e = prefs(c).edit()
        ids.forEach { id -> e.remove("$id.opacity").remove("$id.tint").remove("$id.light") }
        e.apply()
    }
}

/** The colours a widget draws with. */
data class WidgetColors(
    val bg: Color, val text: Color, val soft: Color, val chip: Color,
    val accent: Color, val good: Color, val warn: Color, val alert: Color,
)

fun WidgetStyle.colors(p: Palette): WidgetColors {
    val base = when (tint) {
        WidgetStyle.TINT_BLACK -> Color(0xFF0B0F1A)
        WidgetStyle.TINT_WHITE -> Color.White
        else -> if (lightText) p.darkBase else p.lightBase
    }
    val bg = base.copy(alpha = opacity.coerceIn(0, 100) / 100f)
    return if (lightText) WidgetColors(
        bg, Color.White, Color(0xD9FFFFFF), Color.White.copy(alpha = 0.16f),
        p.secondaryBright, Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFB7185),
    ) else WidgetColors(
        bg, Color(0xFF0F172A), Color(0xFF475569), Color(0xFF0F172A).copy(alpha = 0.08f),
        p.primaryDeep, Color(0xFF059669), Color(0xFFB45309), Color(0xFFE11D48),
    )
}
