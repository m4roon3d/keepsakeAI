package com.keepsakeai.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

/** Full-screen aurora backdrop: soft violet, sky and pink glows over a pale (or midnight) base. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalFlow.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.base, c.baseBottom)))
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(Brush.radialGradient(listOf(c.glow1, Color.Transparent), Offset(w * 0.12f, h * 0.06f), w * 0.85f), w * 0.85f, Offset(w * 0.12f, h * 0.06f))
                drawCircle(Brush.radialGradient(listOf(c.glow2, Color.Transparent), Offset(w * 0.9f, h * 0.14f), w * 0.8f), w * 0.8f, Offset(w * 0.9f, h * 0.14f))
                drawCircle(Brush.radialGradient(listOf(c.glow3, Color.Transparent), Offset(w * 0.8f, h * 0.8f), w * 0.9f), w * 0.9f, Offset(w * 0.8f, h * 0.8f))
            },
        content = content
    )
}

/** Springy press scale, like the prototype's `.spring-card:active`. */
@Composable
fun Modifier.springPress(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this.scale(s)
}

/** Frosted glass surface. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    padding: PaddingValues = PaddingValues(Gap.l),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = LocalFlow.current
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.springPress(interaction) else Modifier)
            .shadow(if (c.dark) 14.dp else 10.dp, shape, ambientColor = Color(0x331E293B), spotColor = Color(0x261E293B))
            .clip(shape)
            .background(c.glass)
            .border(1.dp, c.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(interaction, null, onClick = onClick) else Modifier)
            .animateContentSize(tween(260, easing = FastOutSlowInEasing))
            .padding(padding),
        content = content
    )
}

/** Vivid gradient hero card (Suggest, Spend total). */
@Composable
fun GradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    glow: Color = Flow.Indigo,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .shadow(18.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(brush)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.12f), size.width * 0.45f, Offset(size.width * 0.95f, -size.height * 0.1f))
            }
            .padding(Gap.l),
        content = content
    )
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = LocalFlow.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .springPress(interaction)
            .clip(shape)
            .background(if (selected) c.chipSelected else c.glass)
            .border(1.dp, if (selected) Color.Transparent else c.glassBorder, shape)
            .clickable(interaction, null, onClick = onClick)
            .padding(horizontal = Gap.l, vertical = Gap.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c.onChipSelected else Flow.Indigo, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(Gap.s))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) c.onChipSelected else c.text,
            maxLines = 1
        )
    }
}

/** Small tinted badge, e.g. "UPI Auto-Detect". */
@Composable
fun Badge(text: String, tint: Color, modifier: Modifier = Modifier, brush: Brush? = null) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(tint.copy(alpha = 0.14f)))
            .padding(horizontal = Gap.m, vertical = Gap.xs)
    ) {
        Text(text, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = if (brush != null) Color.White else tint)
    }
}

/** Primary call-to-action with a gradient fill. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = Flow.primaryGradient,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .springPress(interaction)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Flow.Indigo, spotColor = Flow.Indigo)
            .clip(shape)
            .background(brush, alpha = if (enabled) 1f else 0.45f)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Gap.l, vertical = Gap.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Gap.s))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** Secondary glass button. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Flow.Indigo,
    enabled: Boolean = true,
) {
    val c = LocalFlow.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .springPress(interaction)
            .clip(shape)
            .background(c.glass)
            .border(1.dp, c.glassBorder, shape)
            .clickable(interaction, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Gap.l, vertical = Gap.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Gap.s))
        }
        Text(text, color = if (enabled) c.text else c.muted, style = MaterialTheme.typography.labelLarge)
    }
}

/** Round icon button with the iridescent aurora fill. */
@Composable
fun AuroraOrb(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .springPress(interaction)
            .size(size)
            .shadow(16.dp, CircleShape, ambientColor = Flow.Indigo, spotColor = Flow.Sky)
            .clip(CircleShape)
            .background(Flow.auroraSweep)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

/** Glass text input (pill or rounded box). */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = if (singleLine) RoundedCornerShape(50) else RoundedCornerShape(22.dp),
    bare: Boolean = false,
    fieldModifier: Modifier = Modifier,
) {
    val c = LocalFlow.current
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (bare) Modifier else Modifier.background(c.glass).border(1.dp, c.glassBorder, shape))
            .padding(horizontal = Gap.l, vertical = Gap.m),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, tint = Flow.Indigo, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Gap.m))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text, fontSize = Ts.title),
            cursorBrush = SolidColor(Flow.Indigo),
            modifier = Modifier.weight(1f).then(fieldModifier),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = Ts.title)
                    inner()
                }
            }
        )
    }
}

/** Screen header: small eyebrow + bold title, with an optional trailing slot. */
@Composable
fun FlowHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    gradientSuffix: String? = null,
    joinSuffix: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val c = LocalFlow.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Gap.s, bottom = Gap.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Gap.m))
        }
        Column(Modifier.weight(1f)) {
            Text(eyebrow, color = c.muted, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.text)) { append(title) }
                    if (gradientSuffix != null) {
                        if (!joinSuffix) append(" ")
                        withStyle(SpanStyle(brush = Flow.titleGradient)) { append(gradientSuffix) }
                    }
                },
                style = MaterialTheme.typography.headlineMedium
            )
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = LocalFlow.current
    Row(modifier.fillMaxWidth().padding(horizontal = Gap.xxs), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = c.muted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = Ts.meta, color = c.muted)
    }
}

/** Simple on/off glass toggle row used in Pilot and Settings. */
@Composable
fun GlassToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Flow.Indigo
) {
    val c = LocalFlow.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .springPress(interaction)
            .clip(shape)
            .background(c.glass)
            .border(1.dp, if (checked) accent.copy(alpha = 0.5f) else c.glassBorder, shape)
            .clickable(interaction, null, onClick = onToggle)
            .padding(horizontal = Gap.l, vertical = Gap.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.text, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 34.dp, height = 20.dp)
                .clip(CircleShape)
                .background(if (checked) accent else c.muted.copy(alpha = 0.3f))
                .padding(Gap.xxs),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(Color.White))
        }
    }
}
