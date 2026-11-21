package com.keepsakeai.app.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.GradientCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.Recurring
import com.keepsakeai.app.ui.spend.TrendBars
import com.keepsakeai.app.ui.spend.TimeRange
import com.keepsakeai.app.ui.spend.breakdown
import com.keepsakeai.app.ui.spend.buckets
import com.keepsakeai.app.ui.spend.detectRecurring
import com.keepsakeai.app.ui.spend.inRange
import com.keepsakeai.app.ui.spend.percentChange
import com.keepsakeai.app.ui.spend.startOfDay
import com.keepsakeai.app.ui.spend.topMerchants
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import com.keepsakeai.app.ui.theme.ThemeStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Last 7 days (ending today) compared with the 7 days before. Pure, unit-tested. */
data class WeekReview(
    val range: TimeRange,
    val spent: Double,
    val prevSpent: Double,
    val change: Int?,
    val payments: Int,
    val topCategory: Pair<String, Double>?,
    val topMerchant: Pair<String, Double>?,
    val biggestMover: Triple<String, Double, Double>?, // category, this week, last week
    val kept: Int,
    val topCollection: String?,
    val highlights: List<String>,
    val dueNextWeek: List<Recurring>,
    val insight: String,
)

fun computeWeek(expenses: List<ExpenseEntity>, memories: List<MemoryEntity>, now: Long = System.currentTimeMillis()): WeekReview {
    val end = startOfDay(now) + DAY
    val range = TimeRange(end - 7 * DAY, end)
    val prev = TimeRange(end - 14 * DAY, end - 7 * DAY)
    val week = expenses.inRange(range)
    val last = expenses.inRange(prev)
    val spent = week.sumOf { it.amount }
    val prevSpent = last.sumOf { it.amount }
    val thisCats = week.breakdown { it.category }.toMap()
    val lastCats = last.breakdown { it.category }.toMap()
    val mover = (thisCats.keys + lastCats.keys).map { k -> Triple(k, thisCats[k] ?: 0.0, lastCats[k] ?: 0.0) }
        .maxByOrNull { kotlin.math.abs(it.second - it.third) }
        ?.takeIf { kotlin.math.abs(it.second - it.third) >= 200 }
    val weekMem = memories.filter { it.createdAt in range.start until range.end }
    val due = expenses.detectRecurring(now).filter { it.daysUntilDue(now) in 0..7 }
    fun r(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)
    val change = percentChange(spent, prevSpent)
    val insight = when {
        spent == 0.0 && weekMem.isEmpty() -> "A quiet week. Snap something worth keeping!"
        mover != null && mover.second > mover.third ->
            "${mover.first} jumped to ${r(mover.second)} from ${r(mover.third)} last week."
        mover != null -> "Nice: ${mover.first} dropped to ${r(mover.second)} from ${r(mover.third)}."
        change != null && change <= -10 -> "You spent ${-change}% less than last week."
        change != null && change >= 10 -> "Spending is up $change% on last week."
        else -> "Steady week. Spending is close to last week's."
    }
    return WeekReview(
        range = range,
        spent = spent,
        prevSpent = prevSpent,
        change = change,
        payments = week.size,
        topCategory = thisCats.maxByOrNull { it.value }?.toPair(),
        topMerchant = week.topMerchants(1).firstOrNull()?.let { it.name to it.total },
        biggestMover = mover,
        kept = weekMem.size,
        topCollection = weekMem.groupBy { it.collectionName }.maxByOrNull { it.value.size }?.key,
        highlights = weekMem.sortedByDescending { it.createdAt }.take(3).map { it.title },
        dueNextWeek = due,
        insight = insight,
    )
}

private fun r(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)

@Composable
fun WeeklyReviewScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()
    val memories by viewModel.allMemories.collectAsState()
    val w = remember(expenses, memories) { computeWeek(expenses, memories) }
    val fmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
    val rangeText = "${fmt.format(Date(w.range.start))} – ${fmt.format(Date(w.range.end - 1))}".lowercase()

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FlowHeader(eyebrow = rangeText, title = "your ", gradientSuffix = "week", joinSuffix = true, leading = { LogoMark(size = 46.dp) }) {
                GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close)
            }
        }
        item {
            GradientCard(Flow.suggestGradient, Modifier.fillMaxWidth()) {
                Text("✦ insight", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Text(w.insight, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
            }
        }
        item {
            GradientCard(Flow.spendGradient, Modifier.fillMaxWidth(), glow = Flow.Emerald) {
                Text("spent this week", color = Color(0xFFD1FAE5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(r(w.spent), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    buildString {
                        append("${w.payments} payments")
                        w.change?.let { append(" · ${if (it >= 0) "▲" else "▼"} ${kotlin.math.abs(it)}% vs last week") }
                    },
                    color = Color.White, fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                TrendBars(expenses.buckets(w.range), Color.White, height = 46.dp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("top category", w.topCategory?.let { "${it.first}\n${r(it.second)}" } ?: "—", Modifier.weight(1f))
                Stat("top merchant", w.topMerchant?.let { "${it.first}\n${r(it.second)}" } ?: "—", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("memories kept", "${w.kept}" + (w.topCollection?.let { "\nmostly $it" } ?: ""), Modifier.weight(1f))
                Stat("renewals next week", if (w.dueNextWeek.isEmpty()) "none" else "${w.dueNextWeek.size}\n${r(w.dueNextWeek.sumOf { it.amount })}", Modifier.weight(1f))
            }
        }
        if (w.highlights.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("you kept", style = MaterialTheme.typography.titleSmall, color = c.text)
                    w.highlights.forEach { Text("• $it", fontSize = 13.sp, color = c.text, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientButton("share", onClick = { shareWeek(context, w, rangeText) }, icon = Icons.Rounded.IosShare, modifier = Modifier.weight(1f))
                GlassButton("ask pilot", onClick = {
                    viewModel.pilotDraft.value = "Review my week ($rangeText): what stood out in my spending and what I saved, and one thing to do better next week."
                    onClose()
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = LocalFlow.current
    GlassCard(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.muted)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text, lineHeight = 20.sp)
    }
}

/** Renders a story-style summary card and opens the share sheet. */
private fun shareWeek(context: Context, w: WeekReview, rangeText: String) {
    val p = ThemeStore.palette
    val width = 1080
    val height = 1350
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(p.darkBase.toArgb(), p.primaryDeep.toArgb(), p.darkBaseBottom.toArgb()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    fun line(s: String, y: Float, size: Float, bold: Boolean = false, alpha: Int = 255) {
        text.textSize = size; text.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL); text.alpha = alpha
        cv.drawText(s, 90f, y, text)
    }
    line("trove · your week", 150f, 42f, bold = true, alpha = 220)
    line(rangeText, 205f, 34f, alpha = 170)
    line(r(w.spent), 380f, 130f, bold = true)
    line("spent · ${w.payments} payments" + (w.change?.let { "  ${if (it >= 0) "▲" else "▼"}${kotlin.math.abs(it)}%" } ?: ""), 450f, 38f, alpha = 200)
    // Insight bubble
    paint.color = Color.White.copy(alpha = 0.12f).toArgb()
    cv.drawRoundRect(RectF(70f, 520f, width - 70f, 740f), 48f, 48f, paint)
    val words = w.insight.split(" ")
    var y = 600f
    var cur = ""
    words.forEach { wd ->
        val next = if (cur.isEmpty()) wd else "$cur $wd"
        text.textSize = 46f
        if (text.measureText(next) > width - 220) { line(cur, y, 46f, bold = true); y += 62f; cur = wd } else cur = next
    }
    if (cur.isNotEmpty()) line(cur, y, 46f, bold = true)
    var yy = 840f
    w.topCategory?.let { line("top category  ${it.first} · ${r(it.second)}", yy, 40f); yy += 70f }
    w.topMerchant?.let { line("top merchant  ${it.first} · ${r(it.second)}", yy, 40f); yy += 70f }
    line("memories kept  ${w.kept}", yy, 40f); yy += 70f
    if (w.dueNextWeek.isNotEmpty()) { line("renewals next week  ${w.dueNextWeek.size} · ${r(w.dueNextWeek.sumOf { it.amount })}", yy, 40f) }
    line("made with trove", height - 90f, 32f, alpha = 150)

    val dir = File(context.cacheDir, "shares").apply { mkdirs() }
    val file = File(dir, "week_${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your week"))
}
