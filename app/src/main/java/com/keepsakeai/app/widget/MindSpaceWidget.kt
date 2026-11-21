package com.keepsakeai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.MindSpaceApp

class MindSpaceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MindSpaceApp
        val recent = runCatching { app.database.dao().getRecentMemories(5) }.getOrDefault(emptyList())
        val topTitle = recent.firstOrNull()?.title ?: "Ready to capture"
        val collectionCount = recent.map { it.collectionName }.distinct().size

        provideContent {
            MindSpaceWidgetContent(
                memoryCount = recent.size,
                collectionCount = collectionCount,
                latestHeadline = topTitle
            )
        }
    }
}

@Composable
private fun MindSpaceWidgetContent(
    memoryCount: Int,
    collectionCount: Int,
    latestHeadline: String
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.Start,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "keepsake AI",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF60A5FA)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "$memoryCount saved • $collectionCount hubs",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF94A3B8)),
                    fontSize = 11.sp
                )
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        Text(
            text = "Latest: $latestHeadline",
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(GlanceModifier.height(8.dp))

        Text(
            text = "Tap to Snap, Scan, or Ask keepsake Pilot →",
            style = TextStyle(
                color = ColorProvider(Color(0xFF34D399)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

class MindSpaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MindSpaceWidget()
}
