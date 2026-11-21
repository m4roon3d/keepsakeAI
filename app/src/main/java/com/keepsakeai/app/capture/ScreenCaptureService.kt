package com.keepsakeai.app.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(101, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(resultCode, data)
            captureSingleFrame()
        } else {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun captureSingleFrame() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "MindSpaceSnap",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                processCapturedBitmap(cropped)
            } else {
                cleanupAndStop()
            }
        }, 350)
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        val app = application as MindSpaceApp
        scope.launch {
            runCatching {
                val ocrText = app.ocrProcessor.extractTextFromBitmap(bitmap)
                if (ocrText.isNotBlank()) {
                    val analyzed = app.mindPilotClient.analyzeCapture(ocrText)
                    app.database.dao().insertMemory(
                        MemoryEntity(
                            title = analyzed.title,
                            rawContent = ocrText,
                            aiSummary = analyzed.summary,
                            collectionName = analyzed.collection,
                            source = CaptureSource.SCREENSHOT_OCR,
                            tagsCsv = analyzed.tags.joinToString(",")
                        )
                    )
                    analyzed.detectedExpense?.let { exp ->
                        app.database.dao().insertExpense(
                            ExpenseEntity(
                                merchant = exp.merchant,
                                amount = exp.amount,
                                currency = exp.currency,
                                category = exp.category,
                                sourceMethod = "Screen Snap OCR"
                            )
                        )
                    }
                }
            }
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val channelId = "keepsake_ai_capture"
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(channelId, "trove Capture", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("trove Snap")
            .setContentText("Capturing & analyzing screen content...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }
}
