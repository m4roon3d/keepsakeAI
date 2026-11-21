package com.keepsakeai.app.ui.spend

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlin.math.atan2
import kotlin.math.hypot

data class Slice(val key: String, val value: Double, val color: Color)

/** Animated donut; tap a slice to select it. Centre shows the selected slice or the total. */
@Composable
fun Donut(
    slices: List<Slice>,
    selected: Set<String>,
    centerTop: String,
    centerBottom: String,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
) {
    val c = LocalFlow.current
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(slices.map { it.key to it.value }) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(slices) {
                detectTapGestures { pos ->
                    val cx = this.size.width / 2f
                    val cy = this.size.height / 2f
                    val r = hypot(pos.x - cx, pos.y - cy)
                    if (r < this.size.width * 0.28f || r > this.size.width * 0.52f) return@detectTapGestures
                    var deg = Math.toDegrees(atan2((pos.y - cy).toDouble(), (pos.x - cx).toDouble())) + 90.0
                    if (deg < 0) deg += 360.0
                    var acc = 0.0
                    for (s in slices) {
                        acc += s.value / total * 360.0
                        if (deg <= acc) { onTap(s.key); break }
                    }
                }
            }
        ) {
            val strokeW = this.size.width * 0.14f
            val inset = strokeW / 2f + 6f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(c.divider, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokeW))
            var start = -90f
            val gap = if (slices.size > 1) 2.2f else 0f
            slices.forEach { s ->
                val full = (s.value / total * 360.0).toFloat()
                val sw = (full * sweep.value - gap).coerceAtLeast(0.5f)
                val dim = selected.isNotEmpty() && s.key !in selected
                val w = if (s.key in selected) strokeW * 1.22f else strokeW
                drawArc(
                    s.color.copy(alpha = if (dim) 0.28f else 1f), start + gap / 2, sw, false,
                    Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Butt)
                )
                start += full * sweep.value
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
            Text(centerBottom, fontSize = 11.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size * 0.5f))
        }
    }
}

/** Rounded bars for daily/weekly spend; the biggest bar is fully opaque. */
@Composable
fun TrendBars(
    buckets: List<Bucket>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(buckets.map { it.total }) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val max = buckets.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1.0
    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = buckets.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = (slot * 0.62f).coerceAtMost(14.dp.toPx())
        buckets.forEachIndexed { i, b ->
            val frac = (b.total / max).toFloat()
            val h = if (b.total > 0) (size.height * frac * grow.value).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
            val x = i * slot + (slot - barW) / 2
            val alpha = when {
                b.total <= 0 -> 0.22f
                b.total == max -> 1f
                else -> 0.55f + 0.35f * frac
            }
            drawRoundRect(color.copy(alpha = alpha), Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2, barW / 2))
        }
    }
}

/** Single stacked bar split by share; each segment is tappable via the legend below. */
@Composable
fun StackedBar(slices: List<Slice>, selected: Set<String>, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Row(modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(c.divider), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        slices.forEach { s ->
            val dim = selected.isNotEmpty() && s.key !in selected
            Box(
                Modifier.weight((s.value / total).toFloat().coerceAtLeast(0.02f)).fillMaxHeight()
                    .background(s.color.copy(alpha = if (dim) 0.28f else 1f))
            )
        }
    }
}

/** Legend row: dot, name, share and amount. Tapping toggles the filter. */
@Composable
fun LegendRow(
    color: Color,
    name: String,
    amount: String,
    share: Int,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalFlow.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color.copy(alpha = if (dimmed) 0.35f else 1f)))
        Spacer(Modifier.width(8.dp))
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (dimmed) c.muted else c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$share%", fontSize = 11.sp, color = c.muted)
        Spacer(Modifier.width(8.dp))
        Text(amount, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (dimmed) c.muted else c.text)
    }
}
