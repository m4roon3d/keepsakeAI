package com.keepsakeai.app.capture

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Slim translucent right-edge swipe strip (ColorOS Smart Sidebar / Snap Key feel).
 * Swipe UP or INWARD (left) from the right screen edge in any app to snap instantly.
 */
class FloatingSnapService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var windowManager: WindowManager? = null
    private var edgeHandle: View? = null

    private var downX = 0f
    private var downY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val handleWidth = (18 * density).toInt()
        val handleHeight = (132 * density).toInt()

        val params = WindowManager.LayoutParams(
            handleWidth,
            handleHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(24f, 24f, 0f, 0f, 0f, 0f, 24f, 24f)
            setColor(Color.argb(145, 79, 70, 229)) // Translucent indigo Living Flow pill
        }

        edgeHandle = View(this).apply {
            background = pillBg
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = downX - event.rawX // positive = swipe inward (left)
                        val dy = downY - event.rawY // positive = swipe up
                        val threshold = 42 * density
                        if (dx > threshold || dy > threshold || (abs(dx) < 12 && abs(dy) < 12)) {
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            triggerInstantSnap()
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        runCatching { windowManager?.addView(edgeHandle, params) }
    }

    private fun triggerInstantSnap() {
        // Path 1: Zero-prompt capture via optional SnapAccessibilityService if enabled
        val screenText = SnapAccessibilityService.instance?.captureActiveScreenText().orEmpty()
        if (screenText.isNotBlank()) {
            val app = application as MindSpaceApp
            scope.launch {
                val analyzed = app.mindPilotClient.analyzeCapture(screenText)
                val vec = app.vectorSearch.embedText("${analyzed.title} ${analyzed.summary} $screenText")
                app.database.dao().insertMemory(
                    MemoryEntity(
                        title = analyzed.title,
                        rawContent = screenText,
                        aiSummary = analyzed.summary,
                        collectionName = analyzed.collection,
                        source = CaptureSource.SCREENSHOT_OCR,
                        tagsCsv = analyzed.tags.joinToString(","),
                        embeddingCsv = app.vectorSearch.encodeVector(vec)
                    )
                )
            }
            Toast.makeText(this, "Snapped to trove", Toast.LENGTH_SHORT).show()
            return
        }

        // Path 2: Fallback to MainActivity MediaProjection capture
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_TRIGGER_SNAP, true)
        }
        startActivity(launchIntent)
    }

    override fun onDestroy() {
        edgeHandle?.let { runCatching { windowManager?.removeView(it) } }
        super.onDestroy()
    }
}
