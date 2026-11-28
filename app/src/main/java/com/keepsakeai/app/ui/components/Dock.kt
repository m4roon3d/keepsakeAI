package com.keepsakeai.app.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlinx.coroutines.delay

/** Bottom padding screens need so content clears the floating dock. */
val DockClearance = 110.dp

private data class DockItem(val tab: Int, val label: String, val icon: ImageVector)

private val dockItems = listOf(
    DockItem(MainActivity.TAB_HOME, "trove", Icons.Rounded.Dashboard),
    DockItem(MainActivity.TAB_SNAPSAKE, "lens", Icons.Rounded.CenterFocusWeak),
    DockItem(MainActivity.TAB_SPEND, "spend", Icons.Rounded.AccountBalanceWallet),
    DockItem(MainActivity.TAB_SETTINGS, "settings", Icons.Rounded.Tune),
)

/** Floating frosted-glass pill dock + iridescent Pilot orb. */
@Composable
fun FloatingDock(
    selectedTab: Int,
    /** Pager position 0..4 (trove, lens, spend, settings, Pilot), read every frame so the pill follows a swipe. */
    position: () -> Float = { dockItems.indexOfFirst { it.tab == selectedTab }.takeIf { it >= 0 }?.toFloat() ?: 4f },
    pendingUpi: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFlow.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gap.l, vertical = Gap.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .shadow(20.dp, shape, ambientColor = Color(0x401E293B), spotColor = Color(0x401E293B))
                .clip(shape)
                .background(if (c.dark) Color(0xE6171F34) else Color(0xEBFFFFFF))
                .border(1.dp, c.glassBorder, shape)
                .padding(horizontal = Gap.s, vertical = Gap.s),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
          // One highlight pill that slides between items, following the finger during a swipe.
          androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val slot = maxWidth / dockItems.size
            val pillColor = Flow.Indigo.copy(alpha = if (c.dark) 0.28f else 0.14f)
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val p = position()
                        translationX = slot.toPx() * p.coerceIn(0f, (dockItems.size - 1).toFloat())
                        alpha = 1f - (p - (dockItems.size - 1)).coerceIn(0f, 1f)
                    }
            ) {
                Box(Modifier.width(slot).fillMaxHeight().clip(shape).background(pillColor))
            }
            Row(Modifier.fillMaxWidth()) {
            dockItems.forEach { item ->
                val selected = selectedTab == item.tab
                val fg by animateColorAsState(if (selected) Flow.Indigo else c.muted, label = "dockFg")
                val pop by animateFloatAsState(
                    if (selected) 1.12f else 1f,
                    spring(dampingRatio = 0.45f, stiffness = 500f), label = "dockPop"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(item.tab) }
                        .padding(vertical = Gap.s),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, item.label, tint = fg, modifier = Modifier.size(21.dp).graphicsLayer { scaleX = pop; scaleY = pop })
                        Spacer(Modifier.height(Gap.xxs))
                        Text(item.label, fontSize = Ts.caption, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
                    }
                    if (item.tab == MainActivity.TAB_SPEND && pendingUpi > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = Gap.l)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Flow.Amber)
                        )
                    }
                }
            }
            }
          }
        }
        Spacer(Modifier.width(Gap.m))
        Box(Modifier.graphicsLayer {
            val near = (position() - (dockItems.size - 1)).coerceIn(0f, 1f)
            val s = 1f + 0.12f * near
            scaleX = s; scaleY = s
        }) {
            AuroraOrb(
                icon = Icons.Rounded.AutoAwesome,
                contentDescription = "Ask trove Pilot",
                onClick = { onSelect(MainActivity.TAB_PILOT) },
                size = 60.dp
            )
        }
    }
}

/** Dark pill toast for viewModel.statusMessage ("Saved to Travel"). */
@Composable
fun StatusToast(viewModel: MindSpaceViewModel, modifier: Modifier = Modifier) {
    val msg by viewModel.statusMessage.collectAsState()
    LaunchedEffect(msg) {
        if (msg != null) {
            delay(2600)
            viewModel.statusMessage.value = null
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier.padding(top = Gap.s)
    ) {
        Row(
            Modifier
                .shadow(16.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color(0xF00F172A))
                .padding(horizontal = Gap.l, vertical = Gap.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Gap.s))
            Text(msg.orEmpty(), color = Color.White, fontSize = Ts.title, fontWeight = FontWeight.SemiBold)
        }
    }
}
