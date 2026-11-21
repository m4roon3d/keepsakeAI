package com.keepsakeai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.ui.theme.Palette
import com.keepsakeai.app.ui.theme.ThemeStore

/**
 * The trove aurora capsule, drawn in code so it stays crisp at any size and
 * takes on the active theme's colours. Same geometry as the launcher icon
 * (branding/fg.svg), scaled from its 108-unit grid; the capsule spans x 38-70, y 27-81.
 */
enum class MarkGlyph { SPARK, LENS, ORBIT, WALLET, TUNE }

@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    palette: Palette = ThemeStore.palette,
    halo: Boolean = true,
    glyph: MarkGlyph = MarkGlyph.SPARK,
) {
    Canvas(modifier.size(size)) { drawLogo(palette, halo, glyph) }
}

private fun DrawScope.drawLogo(p: Palette, halo: Boolean, glyph: MarkGlyph) {
    // Fit the capsule's 54-unit height into the canvas with a little room for the halo.
    val unit = size.height / 62f
    fun x(v: Float) = size.width / 2f + (v - 54f) * unit
    fun y(v: Float) = size.height / 2f + (v - 54f) * unit

    val warm = lerp(p.accent, Color(0xFFFDBA74), 0.6f)
    if (halo) {
        drawCircle(
            Brush.radialGradient(
                0.4f to p.accent.copy(alpha = 0.45f),
                0.75f to p.primary.copy(alpha = 0.18f),
                1f to Color.Transparent,
                center = Offset(x(54f), y(54f)),
                radius = 31f * unit
            ),
            radius = 31f * unit,
            center = Offset(x(54f), y(54f))
        )
    }

    val capsule = RoundRect(x(38f), y(27f), x(70f), y(81f), CornerRadius(16f * unit))
    val capsulePath = Path().apply { addRoundRect(capsule) }
    drawPath(
        capsulePath,
        Brush.linearGradient(
            0f to warm,
            0.28f to p.accent,
            0.55f to p.tertiary,
            0.78f to p.primary,
            1f to p.secondaryBright,
            start = Offset(x(40f), y(28f)),
            end = Offset(x(68f), y(80f))
        )
    )
    // Depth toward the bottom-right
    drawPath(
        capsulePath,
        Brush.linearGradient(
            0.5f to Color.Transparent,
            1f to Color(0x590B1020),
            start = Offset(x(40f), y(40f)),
            end = Offset(x(70f), y(78f))
        )
    )
    // Glass sheen on the left
    val sheen = Path().apply {
        moveTo(x(46.5f), y(33f))
        cubicTo(x(43f), y(35.5f), x(41.5f), y(39.5f), x(41.5f), y(44f))
        lineTo(x(41.5f), y(63f))
        cubicTo(x(41.5f), y(66f), x(42.3f), y(68.6f), x(43.8f), y(70.8f))
        cubicTo(x(45.2f), y(60f), x(45.6f), y(45f), x(50.5f), y(31.2f))
        cubicTo(x(49f), y(31.6f), x(47.7f), y(32.2f), x(46.5f), y(33f))
        close()
    }
    drawPath(
        sheen,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.75f),
            1f to Color.Transparent,
            start = Offset(x(42f), y(32f)),
            end = Offset(x(50f), y(70f))
        )
    )
    // Inner ring + rim
    val inner = RoundRect(x(42.5f), y(31.5f), x(65.5f), y(76.5f), CornerRadius(11.5f * unit))
    drawPath(Path().apply { addRoundRect(inner) }, Color.White.copy(alpha = 0.28f), style = Stroke(0.9f * unit))
    drawPath(
        capsulePath,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f),
            0.5f to Color.White.copy(alpha = 0.35f),
            1f to lerp(p.secondaryBright, Color.White, 0.5f).copy(alpha = 0.9f),
            start = Offset(x(38f), y(27f)),
            end = Offset(x(70f), y(81f))
        ),
        style = Stroke(1.6f * unit)
    )
    // Spark
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.7f), 1f to Color.Transparent,
            center = Offset(x(54f), y(52f)), radius = 11f * unit
        ),
        radius = 11f * unit, center = Offset(x(54f), y(52f))
    )
    val line = Stroke(width = 2.2f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    if (glyph == MarkGlyph.SPARK) {
        drawPath(spark(::x, ::y, 54f, 51.5f, 8f, 9f), Color.White)
        drawPath(spark(::x, ::y, 61.5f, 42.8f, 2.5f, 2.8f), Color.White.copy(alpha = 0.85f))
    } else if (glyph == MarkGlyph.ORBIT) {
        // Pilot: a spark circled by an orbit
        drawCircle(Color.White.copy(alpha = 0.9f), radius = 9.5f * unit, center = Offset(x(54f), y(52f)), style = Stroke(1.6f * unit))
        drawPath(spark(::x, ::y, 54f, 52f, 5.5f, 6.2f), Color.White)
        drawCircle(Color.White, radius = 1.8f * unit, center = Offset(x(61.2f), y(45.6f)))
    } else if (glyph == MarkGlyph.WALLET) {
        // Spend: wallet with a clasp
        val body = RoundRect(x(45.5f), y(45f), x(62.5f), y(60f), CornerRadius(3f * unit))
        drawPath(Path().apply { addRoundRect(body) }, Color.White, style = line)
        drawPath(Path().apply { moveTo(x(47.5f), y(45f)); lineTo(x(57.5f), y(40.5f)); lineTo(x(59f), y(45f)) }, Color.White, style = line)
        val clasp = RoundRect(x(55.5f), y(50f), x(62.5f), y(55f), CornerRadius(2f * unit))
        drawPath(Path().apply { addRoundRect(clasp) }, Color.White)
    } else if (glyph == MarkGlyph.TUNE) {
        // Settings: three sliders
        listOf(Triple(45f, 58.5f, 0.38f), Triple(51.5f, 58.5f, 0.75f), Triple(58f, 58.5f, 0.2f)).forEachIndexed { i, (yy, _, knob) ->
            val l = 46f; val r = 62f
            drawLine(Color.White.copy(alpha = 0.85f), Offset(x(l), y(yy)), Offset(x(r), y(yy)), strokeWidth = 1.9f * unit, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.White, radius = 2.6f * unit, center = Offset(x(l + (r - l) * knob), y(yy)))
        }
    } else {
        // Lens: four focus brackets around a dot (Snapsake)
        val stroke = line
        val l = 46.5f; val r = 61.5f; val t = 44f; val b = 59f; val arm = 4.2f
        val brackets = Path().apply {
            moveTo(x(l), y(t + arm)); lineTo(x(l), y(t)); lineTo(x(l + arm), y(t))
            moveTo(x(r - arm), y(t)); lineTo(x(r), y(t)); lineTo(x(r), y(t + arm))
            moveTo(x(r), y(b - arm)); lineTo(x(r), y(b)); lineTo(x(r - arm), y(b))
            moveTo(x(l + arm), y(b)); lineTo(x(l), y(b)); lineTo(x(l), y(b - arm))
        }
        drawPath(brackets, Color.White, style = stroke)
        drawCircle(Color.White, radius = 2.6f * unit, center = Offset(x(54f), y(51.5f)))
    }
}

/** Four-point star with curved sides, centred at (cx, cy). */
private fun spark(x: (Float) -> Float, y: (Float) -> Float, cx: Float, cy: Float, rx: Float, ry: Float): Path {
    val k = 0.18f
    return Path().apply {
        moveTo(x(cx), y(cy - ry))
        cubicTo(x(cx + rx * k), y(cy - ry * k * 2f), x(cx + rx * k * 2f), y(cy - ry * k), x(cx + rx), y(cy))
        cubicTo(x(cx + rx * k * 2f), y(cy + ry * k), x(cx + rx * k), y(cy + ry * k * 2f), x(cx), y(cy + ry))
        cubicTo(x(cx - rx * k), y(cy + ry * k * 2f), x(cx - rx * k * 2f), y(cy + ry * k), x(cx - rx), y(cy))
        cubicTo(x(cx - rx * k * 2f), y(cy - ry * k), x(cx - rx * k), y(cy - ry * k * 2f), x(cx), y(cy - ry))
        close()
    }
}
