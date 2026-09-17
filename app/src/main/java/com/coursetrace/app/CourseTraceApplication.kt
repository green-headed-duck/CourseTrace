package com.coursetrace.app

import android.app.Application
import android.util.Log
import com.coursetrace.app.data.AppRepository
import com.coursetrace.app.data.OpenAiCompatibleClient
import com.coursetrace.app.data.PdfImportService
import com.coursetrace.app.data.SecureSettings
import com.coursetrace.app.notifications.NotificationScheduler
import com.coursetrace.app.data.RelaySyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import com.coursetrace.app.widget.NextClassWidgetProvider

class CourseTraceApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repository: AppRepository
        private set
    lateinit var secureSettings: SecureSettings
        private set
    lateinit var apiClient: OpenAiCompatibleClient
        private set
    lateinit var pdfImportService: PdfImportService
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        secureSettings = SecureSettings(this)
        apiClient = OpenAiCompatibleClient()
        pdfImportService = PdfImportService(this, apiClient)
        runStartupStage("notification-channels") {
            NotificationScheduler(this).createChannels()
        }
        runStartupStage("relay-scheduler") {
            RelaySyncScheduler.update(this, repository.state.value.preferences.chatGptLink.enabled)
        }
        runStartupStage("next-class-widget") {
            NextClassWidgetProvider.updateAll(this, repository.state.value)
        }
        applicationScope.launch {
            runStartupStage("course-reminders") {
                NotificationScheduler(this@CourseTraceApplication).reschedule(repository.state.value)
            }
        }
    }

    private inline fun runStartupStage(stage: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.e("CourseTraceStartup", "$stage failed", error)
            runCatching {
                File(filesDir, "startup-failures.log").appendText(
                    "${System.currentTimeMillis()} $stage ${error.javaClass.name}: ${error.message}\n",
                )
            }
        }
    }
}
