package com.keepsakeai.app

import androidx.compose.foundation.layout.Box
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.remember
import com.keepsakeai.app.ui.screens.EntityScreen
import com.keepsakeai.app.ui.brief.MorningBriefScreen
import com.keepsakeai.app.ui.journal.VoiceJournalScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.mutableStateOf
import com.keepsakeai.app.data.preferences.SecurityPrefs
import com.keepsakeai.app.security.BiometricVaultHelper
import com.keepsakeai.app.ui.screens.AiLogScreen
import com.keepsakeai.app.ui.screens.LockScreen
import com.keepsakeai.app.ui.screens.MemoryDetailScreen
import com.keepsakeai.app.ui.review.WeeklyReviewScreen
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

class MainActivity : FragmentActivity() {

    private val viewModel: MindSpaceViewModel by viewModels()

    /** Tab requested by the widget or a shortcut; consumed by the UI. */
    private val requestedTab = mutableIntStateOf(-1)

    /** App lock: locked on launch and after a few minutes in the background. */
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L
    private val lockAfterMs = 3 * 60 * 1000L

    override fun onStart() {
        super.onStart()
        if (SecurityPrefs(this).appLock && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > lockAfterMs) {
            locked.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    private fun unlock() {
        if (!BiometricVaultHelper.canAuthenticate(this)) { locked.value = false; return }
        BiometricVaultHelper.authenticate(this, onSuccess = { locked.value = false }, onError = { })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked.value = SecurityPrefs(this).appLock
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
                val draft by viewModel.pilotDraft.collectAsState()
                LaunchedEffect(draft) { if (draft != null) selectedTab = TAB_PILOT }
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

                // Swipe order matches the dock left to right, with the Pilot orb last.
                val pageOrder = remember { listOf(TAB_HOME, TAB_SNAPSAKE, TAB_SPEND, TAB_SETTINGS, TAB_PILOT) }
                val pager = rememberPagerState(initialPage = pageOrder.indexOf(selectedTab).coerceAtLeast(0)) { pageOrder.size }
                LaunchedEffect(selectedTab) {
                    val p = pageOrder.indexOf(selectedTab)
                    if (p >= 0 && pager.currentPage != p) pager.animateScrollToPage(p, animationSpec = tween(380, easing = FastOutSlowInEasing))
                }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { p -> if (selectedTab != pageOrder[p]) selectedTab = pageOrder[p] }
                }
                val openMemories by viewModel.memoryStack.collectAsState()
                val openOverlay by viewModel.overlay.collectAsState()

                AuroraBackground {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding()
                    HorizontalPager(
                        state = pager,
                        userScrollEnabled = openMemories.isEmpty() && openOverlay == null,
                        beyondViewportPageCount = 0,
                        key = { pageOrder[it] },
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                    val tab = pageOrder[page]
                    // Depth while swiping: the page leaving shrinks and fades a little.
                    val pageModifier = Modifier.graphicsLayer {
                        val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        val d = kotlin.math.abs(off)
                        val s = 1f - 0.06f * d
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.35f * d
                    }
                    Box(pageModifier) {
                    when (tab) {
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
                            modifier = contentModifier,
                            viewModel = viewModel
                        )
                    }
                    }
                    }

                    StatusToast(viewModel, Modifier.align(Alignment.TopCenter).statusBarsPadding())

                    FloatingDock(
                        selectedTab = selectedTab,
                        position = { pager.currentPage + pager.currentPageOffsetFraction },
                        pendingUpi = viewModel.pendingUpi.collectAsState().value.size,
                        onSelect = { selectedTab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                    )

                    // Full-screen overlays: weekly review, AI activity log
                    val overlay by viewModel.overlay.collectAsState()
                    var shownOverlay by remember { mutableStateOf<String?>(null) }
                    overlay?.let { shownOverlay = it }
                    AnimatedVisibility(
                        visible = overlay != null,
                        enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(240)),
                        exit = slideOutVertically(tween(240)) { it / 6 } + fadeOut(tween(180))
                    ) {
                        BackHandler { viewModel.overlay.value = null }
                        AuroraBackground(Modifier.fillMaxSize()) {
                            val m = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                            when (shownOverlay) {
                                OVERLAY_REVIEW -> WeeklyReviewScreen(viewModel, onClose = { viewModel.overlay.value = null }, modifier = m)
                                OVERLAY_AI_LOG -> AiLogScreen(viewModel, onClose = { viewModel.overlay.value = null }, modifier = m)
                                OVERLAY_BRIEF -> MorningBriefScreen(
                                    viewModel, onClose = { viewModel.overlay.value = null },
                                    onOpenTab = { t ->
                                        if (t == "todos") { viewModel.hubRequest.value = "TODOS"; selectedTab = TAB_HOME }
                                        else if (t == "spend") selectedTab = TAB_SPEND
                                    },
                                    modifier = m
                                )
                                OVERLAY_NEAR -> com.keepsakeai.app.ui.screens.NearHereScreen(viewModel, onClose = { viewModel.overlay.value = null }, modifier = m)
                                OVERLAY_JOURNAL -> VoiceJournalScreen(viewModel, onClose = { viewModel.overlay.value = null }, modifier = m)
                                else -> viewModel.overlay.value = null
                            }
                        }
                    }

                    // Person / place / project pages
                    val entityKey by viewModel.openEntityKey.collectAsState()
                    var shownEntity by remember { mutableStateOf<String?>(null) }
                    if (entityKey != null) shownEntity = entityKey
                    AnimatedVisibility(
                        visible = entityKey != null,
                        enter = slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200)),
                        exit = slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(160))
                    ) {
                        BackHandler { viewModel.openEntityKey.value = null }
                        AuroraBackground(Modifier.fillMaxSize()) {
                            shownEntity?.let { k ->
                                EntityScreen(viewModel, k, onClose = { viewModel.openEntityKey.value = null },
                                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding())
                            }
                        }
                    }

                    // Memory pages sit above everything else; back steps through related memories
                    val memStack by viewModel.memoryStack.collectAsState()
                    var shownMemory by remember { mutableStateOf<Long?>(null) }
                    memStack.lastOrNull()?.let { shownMemory = it }
                    AnimatedVisibility(
                        visible = memStack.isNotEmpty(),
                        enter = slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(200)),
                        exit = slideOutHorizontally(tween(200)) { it / 4 } + fadeOut(tween(160))
                    ) {
                        BackHandler { viewModel.closeMemory() }
                        AuroraBackground(Modifier.fillMaxSize()) {
                            shownMemory?.let { memId ->
                                AnimatedContent(memId, transitionSpec = {
                                    (slideInHorizontally(tween(240)) { it / 5 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120))
                                }, label = "memory") { id ->
                                    MemoryDetailScreen(
                                        viewModel, id,
                                        onClose = { viewModel.closeMemory() },
                                        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                                    )
                                }
                            }
                        }
                    }

                    if (locked.value) {
                        LaunchedEffect(Unit) { unlock() }
                        LockScreen(onUnlock = { unlock() })
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
        intent.getStringExtra(EXTRA_OPEN_OVERLAY)?.let { viewModel.overlay.value = it }
        when (intent.getStringExtra(EXTRA_OPEN_TAB)) {
            "home" -> requestedTab.intValue = TAB_HOME
            "note" -> { requestedTab.intValue = TAB_HOME; viewModel.hubRequest.value = "KEEPSAKES"; viewModel.focusNote.value = true }
            "lens", "snapsake" -> requestedTab.intValue = TAB_SNAPSAKE
            "camera" -> { requestedTab.intValue = TAB_SNAPSAKE; viewModel.autoCamera.value = true }
            "journal" -> { requestedTab.intValue = TAB_HOME; viewModel.overlay.value = OVERLAY_JOURNAL }
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
            } else if (type.startsWith("image/") || type.startsWith("video/")) {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { captureShared(listOf(it)) }
            }
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris: List<Uri> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
            captureShared(uris.take(10))
        }
    }

    // Shared photos keep their GPS only if we can ask for the original: ask once, then save either way.
    private var pendingShared: List<Uri> = emptyList()
    private val mediaLocationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.captureMediaUris(pendingShared, CaptureSource.SHARE_SHEET)
        pendingShared = emptyList()
    }

    private fun captureShared(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (com.keepsakeai.app.capture.MediaMeta.canReadPhotoLocation(this)) {
            viewModel.captureMediaUris(uris, CaptureSource.SHARE_SHEET)
        } else {
            pendingShared = uris
            mediaLocationPermission.launch(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    companion object {
        const val EXTRA_TRIGGER_SNAP = "extra_trigger_snap"
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_OPEN_OVERLAY = "open_overlay"
        const val OVERLAY_REVIEW = "review"
        const val OVERLAY_AI_LOG = "ai_log"
        const val OVERLAY_BRIEF = "brief"
        const val OVERLAY_JOURNAL = "journal"
        const val OVERLAY_NEAR = "near"
        const val TAB_HOME = 0
        const val TAB_SNAPSAKE = 1
        const val TAB_PILOT = 2
        const val TAB_SPEND = 3
        const val TAB_SETTINGS = 4
    }
}
