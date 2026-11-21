package com.keepsakeai.app.ui.components

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
    DockItem(MainActivity.TAB_HOME, "keepsake", Icons.Rounded.Dashboard),
    DockItem(MainActivity.TAB_SNAPSAKE, "snapsake", Icons.Rounded.CenterFocusWeak),
    DockItem(MainActivity.TAB_SPEND, "spend", Icons.Rounded.AccountBalanceWallet),
    DockItem(MainActivity.TAB_SETTINGS, "settings", Icons.Rounded.Tune),
)

/** Floating frosted-glass pill dock + iridescent Pilot orb. */
@Composable
fun FloatingDock(
    selectedTab: Int,
    pendingUpi: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFlow.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .shadow(20.dp, shape, ambientColor = Color(0x401E293B), spotColor = Color(0x401E293B))
                .clip(shape)
                .background(if (c.dark) Color(0xE6171F34) else Color(0xEBFFFFFF))
                .border(1.dp, c.glassBorder, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dockItems.forEach { item ->
                val selected = selectedTab == item.tab
                val bg by animateColorAsState(
                    if (selected) Flow.Indigo.copy(alpha = if (c.dark) 0.28f else 0.14f) else Color.Transparent,
                    label = "dockBg"
                )
                val fg by animateColorAsState(if (selected) Flow.Indigo else c.muted, label = "dockFg")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(bg)
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(item.tab) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, item.label, tint = fg, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
                    }
                    if (item.tab == MainActivity.TAB_SPEND && pendingUpi > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 14.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Flow.Amber)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        AuroraOrb(
            icon = Icons.Rounded.AutoAwesome,
            contentDescription = "Ask keepsake Pilot",
            onClick = { onSelect(MainActivity.TAB_PILOT) },
            size = 60.dp
        )
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
        modifier = modifier.padding(top = 8.dp)
    ) {
        Row(
            Modifier
                .shadow(16.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(Color(0xF00F172A))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(msg.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
