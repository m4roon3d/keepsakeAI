package com.keepsakeai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.ui.theme.ThemeStore
import java.text.NumberFormat
import java.util.Locale

val OpenTabKey = ActionParameters.Key<String>(MainActivity.EXTRA_OPEN_TAB)
val OpenOverlayKey = ActionParameters.Key<String>(MainActivity.EXTRA_OPEN_OVERLAY)

fun openApp(tab: String, overlay: String? = null): Action = actionStartActivity<MainActivity>(
    if (overlay == null) actionParametersOf(OpenTabKey to tab) else actionParametersOf(OpenTabKey to tab, OpenOverlayKey to overlay)
)

/** This widget's colours, from the style picked for it and the app's current theme. */
suspend fun widgetColors(context: Context, id: GlanceId): WidgetColors {
    ThemeStore.load(context)
    val wid = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrDefault(-1)
    return WidgetStyleStore.get(context, wid).colors(ThemeStore.palette)
}

/** Rounded, see-through background every trove widget sits on. */
@Composable
fun WidgetSurface(c: WidgetColors, onClick: Action?, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ColorProvider(c.bg))
            .then(if (onClick != null) GlanceModifier.clickable(onClick) else GlanceModifier)
            .padding(padding),
        content = content
    )
}

fun ts(color: Color, size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = weight)

fun money(currency: String, v: Double): String {
    val n = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(Math.round(v))
    return if (currency.equals("INR", true)) "₹$n" else "$currency $n"
}

/** "today", "tomorrow", "in 5 days", "2 days late". */
fun daysLabel(days: Int): String = when {
    days < -1 -> "${-days} days late"
    days == -1 -> "yesterday"
    days == 0 -> "today"
    days == 1 -> "tomorrow"
    else -> "in $days days"
}

object TroveWidgets {
    /** Refresh every trove widget on the home screen. */
    suspend fun updateAll(context: Context) {
        listOf(TodayWidget(), CaptureWidget(), MoneyWidget(), ComingUpWidget()).forEach { runCatching { it.updateAll(context) } }
    }
}
