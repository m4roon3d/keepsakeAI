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

class MindSpaceApp : Application() {

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
        database = MindSpaceDatabase.getInstance(this)
        apiKeyStore = ApiKeyStore(this)
        mindPilotClient = MultiModelMindPilotClient(apiKeyStore)
        vectorSearch = SemanticVectorSearch(apiKeyStore)
        ocrProcessor = OcrProcessor(this)

        scheduleDailyMindSuggest()
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
