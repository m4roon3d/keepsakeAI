package com.keepsakeai.app.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.keepsakeai.app.MainActivity
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles Android ACTION_ASSIST (bottom-corner swipe up or power-button assist gesture)
 * when the user selects keepsake AI as their Digital Assistant app.
 */
class AssistSnapActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            Toast.makeText(this, "Captured via Assist to keepsake AI", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_TRIGGER_SNAP, true)
            }
            startActivity(intent)
        }
        finish()
    }
}
