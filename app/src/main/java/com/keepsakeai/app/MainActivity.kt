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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.keepsakeai.app.capture.ScreenCaptureService
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.screens.ExpensesScreen
import com.keepsakeai.app.ui.screens.HomeFeedScreen
import com.keepsakeai.app.ui.screens.MindPilotScreen
import com.keepsakeai.app.ui.screens.OSeeScannerScreen
import com.keepsakeai.app.ui.screens.SettingsScreen
import com.keepsakeai.app.ui.theme.MindSpaceTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MindSpaceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            MindSpaceTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

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

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Hub") },
                                label = { Text("Space") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.CameraAlt, contentDescription = "OSee") },
                                label = { Text("OSee") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Pilot") },
                                label = { Text("Pilot") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Expenses") },
                                label = { Text("Spend") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        0 -> HomeFeedScreen(
                            viewModel = viewModel,
                            onTriggerScreenSnap = triggerScreenSnap,
                            onOpenPilot = { selectedTab = 2 },
                            modifier = Modifier.padding(padding)
                        )
                        1 -> OSeeScannerScreen(
                            viewModel = viewModel,
                            onSavedNavigateHome = { selectedTab = 0 },
                            modifier = Modifier.padding(padding)
                        )
                        2 -> MindPilotScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(padding)
                        )
                        3 -> ExpensesScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(padding)
                        )
                        4 -> SettingsScreen(
                            modifier = Modifier.padding(padding)
                        )
                    }
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
    }
}
