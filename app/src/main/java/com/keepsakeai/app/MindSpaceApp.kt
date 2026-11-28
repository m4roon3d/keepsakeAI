package com.keepsakeai.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.keepsakeai.app.ai.MultiModelMindPilotClient
import com.keepsakeai.app.ai.SemanticVectorSearch
import com.keepsakeai.app.capture.OcrProcessor
import com.keepsakeai.app.data.local.MindSpaceDatabase
import com.keepsakeai.app.data.preferences.ApiKeyStore
import com.keepsakeai.app.ui.theme.ThemeStore
import com.keepsakeai.app.worker.DailyDigestWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class MindSpaceApp : Application() {

    /** Background scope for fire-and-forget work such as audit logging. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    lateinit var database: MindSpaceDatabase
        private set
    lateinit var apiKeyStore: ApiKeyStore
        private set
    lateinit var mindPilotClient: MultiModelMindPilotClient
        private set
    lateinit var vectorSearch: SemanticVectorSearch
        private set
    lateinit var ocrProcessor: OcrProcessor
        private set

    override fun onCreate() {
        super.onCreate()
        ThemeStore.load(this)
        com.keepsakeai.app.notify.Notifier.createChannels(this)
        database = MindSpaceDatabase.getInstance(this)
        apiKeyStore = ApiKeyStore(this)
        mindPilotClient = MultiModelMindPilotClient(apiKeyStore).also { client ->
            // AI activity log: record every outgoing call.
            client.auditSink = { provider, feature, prompt ->
                appScope.launch {
                    database.dao().insertAiAuditLog(
                        com.keepsakeai.app.data.local.AiCallAuditEntity(provider = provider, feature = feature, promptPreview = prompt.take(1500))
                    )
                }
            }
        }
        vectorSearch = SemanticVectorSearch(apiKeyStore)
        ocrProcessor = OcrProcessor(this)

        scheduleDailyMindSuggest()
        com.keepsakeai.app.worker.DailyNudgeWorker.scheduleAll(this)
        com.keepsakeai.app.worker.EmailSyncWorker.schedule(this)
        // Places visited was removed: stop the background check an earlier build scheduled.
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("places_visited")
        // Geofences don't survive a reboot or reinstall: put the errand alerts back.
        runCatching { com.keepsakeai.app.intents.PlaceAlerts.sync(this) }
    }

    private fun scheduleDailyMindSuggest() {
        val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(12, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_mind_suggest",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
