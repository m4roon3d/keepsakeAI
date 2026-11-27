package com.keepsakeai.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.ui.theme.LocalFlow

/** Counts of [times] per week for the last [weeks] weeks, oldest first. */
fun weeklyCounts(times: List<Long>, weeks: Int = 8, now: Long = System.currentTimeMillis()): List<Float> {
    val week = 7L * 24 * 60 * 60 * 1000
    val out = FloatArray(weeks)
    times.forEach { t -> val ago = ((now - t) / week).toInt(); if (t <= now && ago in 0 until weeks) out[weeks - 1 - ago] += 1f }
    return out.toList()
}

/** Counts of [times] per day for the last [days] days, oldest first. */
fun dailyCounts(times: List<Long>, days: Int = 14, now: Long = System.currentTimeMillis()): List<Float> {
    val today = com.keepsakeai.app.ui.spend.startOfDay(now)
    val day = 24L * 60 * 60 * 1000
    val out = FloatArray(days)
    times.forEach { t -> val ago = Math.floorDiv(today - com.keepsakeai.app.ui.spend.startOfDay(t), day).toInt(); if (ago in 0 until days) out[days - 1 - ago] += 1f }
    return out.toList()
}

/** Fades and lifts content in the first time it appears; [order] staggers a group so it settles top to bottom. */
fun Modifier.appear(order: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((order.coerceIn(0, 8) * 45).toLong())
        p.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 18.dp.toPx()
    }
}

/** A thin rounded bar that fills to [fraction] with a short ease. */
@Composable
fun FillBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = LocalFlow.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "fill")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.divider)) {
        if (f > 0f) Box(
            Modifier.fillMaxWidth(f).fillMaxHeight().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color)))
        )
    }
}

/** Progress ring that sweeps to [fraction]; put a count or label in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = LocalFlow.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val s = Size(this.size.width - w, this.size.height - w)
            drawArc(c.divider, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(w))
            if (f > 0f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), s, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}

/** Small bars that grow in, for "activity over time" next to a person or a period. The latest bar is the boldest. */
@Composable
fun MiniBars(values: List<Float>, color: Color, modifier: Modifier = Modifier, height: Dp = 22.dp) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Canvas(modifier.height(height)) {
        val n = values.size.coerceAtLeast(1)
        val slot = this.size.width / n
        val bw = (slot * 0.6f).coerceAtMost(10.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = if (v > 0f) (this.size.height * (v / max) * grow.value).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
            val alpha = when { v <= 0f -> 0.2f; i == values.lastIndex -> 1f; else -> 0.45f + 0.4f * (v / max) }
            drawRoundRect(
                color.copy(alpha = alpha), Offset(i * slot + (slot - bw) / 2, this.size.height - h),
                Size(bw, h), CornerRadius(bw / 2, bw / 2)
            )
        }
    }
}
