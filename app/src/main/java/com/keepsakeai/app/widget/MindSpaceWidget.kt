package com.keepsakeai.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.R
import com.keepsakeai.app.ui.theme.Palette
import com.keepsakeai.app.ui.theme.ThemeStore
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** Snapshot of what the widget shows. */
private data class WidgetData(
    val memories: Int,
    val monthSpend: Double,
    val pendingUpi: Int,
    val latestTitle: String?,
    val latestCollection: String?,
    val recent: List<Pair<String, String>>, // collection to title
)

val OpenTabKey = ActionParameters.Key<String>(MainActivity.EXTRA_OPEN_TAB)

/**
 * keepsake AI home-screen widget: aurora glass card in the active theme with live stats,
 * the latest memory and one-tap shortcuts into snapsake, Spend and Pilot.
 */
class MindSpaceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, DpSize(250.dp, 165.dp), DpSize(250.dp, 210.dp), LARGE, DpSize(250.dp, 320.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MindSpaceApp
        ThemeStore.load(context)
        val palette = ThemeStore.palette
        val dao = app.database.dao()
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val recent = runCatching { dao.getRecentMemories(3) }.getOrDefault(emptyList())
        val data = WidgetData(
            memories = runCatching { dao.countMemories() }.getOrDefault(0),
            monthSpend = runCatching { dao.sumExpensesSince(monthStart) }.getOrDefault(0.0),
            pendingUpi = runCatching { dao.countPendingUpi() }.getOrDefault(0),
            latestTitle = recent.firstOrNull()?.title,
            latestCollection = recent.firstOrNull()?.collectionName,
            recent = recent.map { it.collectionName to it.title },
        )
        val currency = app.apiKeyStore.defaultCurrency.ifBlank { "INR" }
        val bg = auroraBitmap(palette, 900, 460)
        val orb = orbBitmap(palette, 360, 120)

        provideContent { WidgetContent(data, palette, currency, bg, orb) }
    }

    companion object {
        val SMALL = DpSize(130.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 260.dp)
    }
}

class MindSpaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MindSpaceWidget()
}

// ── Layout ───────────────────────────────────────────────────────────

private val White = Color.White
private val Soft = Color(0xB3FFFFFF)
private val Faint = Color(0x1FFFFFFF)

private fun open(tab: String) = actionStartActivity<MainActivity>(actionParametersOf(OpenTabKey to tab))

@Composable
private fun WidgetContent(d: WidgetData, p: Palette, currency: String, bg: Bitmap, orb: Bitmap) {
    val size = LocalSize.current
    val narrow = size.width < 240.dp
    val h = size.height.value
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(28.dp).clickable(open("home"))
    ) {
        Image(ImageProvider(bg), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
            if (narrow) {
                SmallBody(d, orb)
            } else {
                // Height tiers: header+stats (~110dp) → +buttons (~160) → +latest (~205) → +list (~245+)
                Header(p, showOrb = h < 156, orb = orb)
                Spacer(GlanceModifier.height(10.dp))
                StatRow(d, p, currency)
                if (h >= 156) {
                    if (h >= 245) {
                        Spacer(GlanceModifier.height(8.dp))
                        RecentList(d, p, rows = ((h - 164) / 36).toInt().coerceIn(1, 3))
                    } else if (h >= 205) {
                        Spacer(GlanceModifier.height(8.dp))
                        LatestCard(d, p)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Actions(d, orb)
                }
            }
        }
    }
}

@Composable
private fun Header(p: Palette, showOrb: Boolean, orb: Bitmap) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_launcher_foreground), "keepsake AI", GlanceModifier.size(40.dp))
        Spacer(GlanceModifier.width(4.dp))
        Text("keepsake", style = TextStyle(color = ColorProvider(White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Text(" AI", style = TextStyle(color = ColorProvider(p.secondaryBright), fontSize = 16.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.defaultWeight())
        if (showOrb) {
            OrbButton("ask", orb, GlanceModifier.width(76.dp))
        } else {
            Text(greeting(), style = TextStyle(color = ColorProvider(Soft), fontSize = 11.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun SmallBody(d: WidgetData, orb: Bitmap) {
    Column(GlanceModifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_launcher_foreground), "keepsake AI", GlanceModifier.size(40.dp))
            Column {
                Text("${d.memories}", style = TextStyle(color = ColorProvider(White), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text("kept", style = TextStyle(color = ColorProvider(Soft), fontSize = 11.sp))
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        OrbButton("ask", orb, GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun StatRow(d: WidgetData, p: Palette, currency: String) {
    Row(GlanceModifier.fillMaxWidth()) {
        Stat("${d.memories}", "kept", p.secondaryBright, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        Stat(money(currency, d.monthSpend), "this month", Color(0xFF34D399), GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        Stat("${d.pendingUpi}", "to review", if (d.pendingUpi > 0) Color(0xFFFBBF24) else Soft, GlanceModifier.defaultWeight(), tab = "spend")
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color, modifier: GlanceModifier, tab: String = "home") {
    Column(
        modifier.background(Faint).cornerRadius(16.dp).padding(horizontal = 10.dp, vertical = 7.dp).clickable(open(tab))
    ) {
        Text(value, maxLines = 1, style = TextStyle(color = ColorProvider(accent), fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Text(label, maxLines = 1, style = TextStyle(color = ColorProvider(Soft), fontSize = 10.sp))
    }
}

@Composable
private fun LatestCard(d: WidgetData, p: Palette) {
    Row(
        GlanceModifier.fillMaxWidth().background(Faint).cornerRadius(16.dp).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(GlanceModifier.size(width = 4.dp, height = 22.dp).background(p.accent).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                (d.latestCollection ?: "latest").uppercase(Locale.US), maxLines = 1,
                style = TextStyle(color = ColorProvider(p.secondaryBright), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                d.latestTitle ?: "Nothing kept yet. Snap something!", maxLines = 1,
                style = TextStyle(color = ColorProvider(White), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun RecentList(d: WidgetData, p: Palette, rows: Int) {
    Column(GlanceModifier.fillMaxWidth()) {
        if (d.recent.isEmpty()) {
            LatestCard(d, p)
        } else {
            d.recent.take(rows).forEachIndexed { i, (col, title) ->
                if (i > 0) Spacer(GlanceModifier.height(5.dp))
                Row(
                    GlanceModifier.fillMaxWidth().background(Faint).cornerRadius(14.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(GlanceModifier.size(width = 4.dp, height = 18.dp).background(listOf(p.accent, p.secondary, p.tertiary)[i % 3]).cornerRadius(2.dp)) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Text(title, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(col.lowercase(Locale.US), maxLines = 1, style = TextStyle(color = ColorProvider(Soft), fontSize = 10.sp))
                }
            }
        }
    }
}

@Composable
private fun Actions(d: WidgetData, orb: Bitmap) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassAction("snap", "snapsake", GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        GlassAction(if (d.pendingUpi > 0) "review ${d.pendingUpi}" else "spend", "spend", GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        OrbButton("ask pilot", orb, GlanceModifier.defaultWeight())
    }
}

@Composable
private fun GlassAction(label: String, tab: String, modifier: GlanceModifier) {
    Box(
        modifier.height(34.dp).background(Color(0x2EFFFFFF)).cornerRadius(17.dp).clickable(open(tab)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, maxLines = 1, style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun OrbButton(label: String, orb: Bitmap, modifier: GlanceModifier) {
    Box(modifier.height(34.dp).cornerRadius(17.dp).clickable(open("pilot")), contentAlignment = Alignment.Center) {
        Image(ImageProvider(orb), null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
        Text("✦ $label", maxLines = 1, style = TextStyle(color = ColorProvider(White), fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "good morning"
    in 12..16 -> "good afternoon"
    in 17..21 -> "good evening"
    else -> "night owl mode"
}

private fun money(code: String, v: Double): String {
    val sym = when (code.uppercase(Locale.US)) { "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; else -> "" }
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    return when {
        v >= 100_000 -> sym + String.format(Locale.US, "%.1fL", v / 100_000)
        else -> sym + nf.format(v)
    }
}

/** Deep-space base with three aurora glows in the palette's colours. */
private fun auroraBitmap(p: Palette, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(blend(p.darkBase, p.primaryDeep, 0.25f), p.darkBase.toArgb(), p.darkBaseBottom.toArgb()),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    fun glow(cx: Float, cy: Float, r: Float, color: Color, a: Float) {
        paint.shader = RadialGradient(cx, cy, r, color.copy(alpha = a).toArgb(), color.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, paint)
    }
    glow(w * 0.08f, h * 0.0f, w * 0.55f, p.glows[0], 0.65f)
    glow(w * 1.0f, h * 0.25f, w * 0.5f, p.glows[1], 0.45f)
    glow(w * 0.62f, h * 1.15f, w * 0.55f, p.glows[2], 0.5f)
    // Glass sheen across the top edge
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.35f, Color.White.copy(alpha = 0.10f).toArgb(), 0, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h * 0.35f, paint)
    return bmp
}

/** Iridescent pill fill for the Pilot shortcut. */
private fun orbBitmap(p: Palette, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(p.primary.toArgb(), p.tertiary.toArgb(), p.accent.toArgb(), p.secondary.toArgb()),
        floatArrayOf(0f, 0.4f, 0.7f, 1f), Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.White.copy(alpha = 0.25f).toArgb(), 0, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
    return bmp
}

private fun blend(a: Color, b: Color, t: Float): Int =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f).toArgb()
