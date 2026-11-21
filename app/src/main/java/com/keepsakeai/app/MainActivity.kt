package com.keepsakeai.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.keepsakeai.app.capture.ScreenCaptureService
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.AuroraBackground
import com.keepsakeai.app.ui.components.FloatingDock
import com.keepsakeai.app.ui.components.StatusToast
import com.keepsakeai.app.ui.screens.ExpensesScreen
import com.keepsakeai.app.ui.screens.HomeFeedScreen
import com.keepsakeai.app.ui.screens.MindPilotScreen
import com.keepsakeai.app.ui.screens.SettingsScreen
import com.keepsakeai.app.ui.screens.SnapsakeScreen
import com.keepsakeai.app.ui.theme.MindSpaceTheme
import com.keepsakeai.app.ui.theme.isFlowDark

class MainActivity : ComponentActivity() {

    private val viewModel: MindSpaceViewModel by viewModels()

    /** Tab requested by the widget or a shortcut; consumed by the UI. */
    private val requestedTab = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        enableEdgeToEdge()

        setContent {
            MindSpaceTheme {
                val dark = isFlowDark()
                DisposableEffect(dark) {
                    // Keep status/nav bar icons readable when the in-app mode differs from the system's.
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                var selectedTab by rememberSaveable { mutableIntStateOf(TAB_HOME) }
                val wanted = requestedTab.intValue
                LaunchedEffect(wanted) {
                    if (wanted >= 0) {
                        selectedTab = wanted
                        requestedTab.intValue = -1
                    }
                }

                val projectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        val svc = Intent(this, ScreenCaptureService::class.java).apply {
                            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                            putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                        }
                        startForegroundService(svc)
                    }
                }

                val triggerScreenSnap = {
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(mgr.createScreenCaptureIntent())
                }

                AuroraBackground {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding()
                    when (selectedTab) {
                        TAB_HOME -> HomeFeedScreen(
                            viewModel = viewModel,
                            onTriggerScreenSnap = triggerScreenSnap,
                            onOpenPilot = { selectedTab = TAB_PILOT },
                            onOpenSnapsake = { selectedTab = TAB_SNAPSAKE },
                            onOpenSpend = { selectedTab = TAB_SPEND },
                            modifier = contentModifier
                        )
                        TAB_SNAPSAKE -> SnapsakeScreen(
                            viewModel = viewModel,
                            onSavedNavigateHome = { selectedTab = TAB_HOME },
                            modifier = contentModifier
                        )
                        TAB_PILOT -> MindPilotScreen(
                            viewModel = viewModel,
                            modifier = contentModifier
                        )
                        TAB_SPEND -> ExpensesScreen(
                            viewModel = viewModel,
                            modifier = contentModifier,
                            onAskPilot = { q -> viewModel.pilotDraft.value = q; selectedTab = TAB_PILOT }
                        )
                        TAB_SETTINGS -> SettingsScreen(
                            modifier = contentModifier
                        )
                    }

                    StatusToast(viewModel, Modifier.align(Alignment.TopCenter).statusBarsPadding())

                    FloatingDock(
                        selectedTab = selectedTab,
                        pendingUpi = viewModel.pendingUpi.collectAsState().value.size,
                        onSelect = { selectedTab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            "home" -> requestedTab.intValue = TAB_HOME
            "snapsake" -> requestedTab.intValue = TAB_SNAPSAKE
            "pilot" -> requestedTab.intValue = TAB_PILOT
            "spend" -> requestedTab.intValue = TAB_SPEND
            "settings" -> requestedTab.intValue = TAB_SETTINGS
        }
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type.orEmpty()
            if (type.startsWith("text/")) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val source = if (sharedText.contains("http")) CaptureSource.LINK_CAPTURE else CaptureSource.SHARE_SHEET
                viewModel.captureText(sharedText, source = source)
            } else if (type.startsWith("image/")) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { viewModel.captureImageUri(it, CaptureSource.SCREENSHOT_OCR) }
            }
        }
    }

    companion object {
        const val EXTRA_TRIGGER_SNAP = "extra_trigger_snap"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_OVERLAY = "open_overlay"
        const val TAB_HOME = 0
        const val TAB_SNAPSAKE = 1
        const val TAB_PILOT = 2
        const val TAB_SPEND = 3
        const val TAB_SETTINGS = 4
    }
}
