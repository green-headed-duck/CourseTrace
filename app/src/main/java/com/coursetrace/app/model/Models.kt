package com.coursetrace.app.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class WeekPattern { EVERY, ODD, EVEN }

@Serializable
enum class MaterialKind { DOCUMENT, SLIDE, IMAGE, VIDEO, AUDIO, CODE, LINK, OTHER }

@Serializable
enum class LearningEventKind { TRANSCRIPT, QUESTION, PAIN_POINT, WRONG_ANSWER, PROGRESS, DECISION, NOTE }

@Serializable
enum class ImportSource { API_RELAY, CHATGPT_MOBILE, MANUAL }

@Serializable
data class Term(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startDate: String,
    val weekCount: Int = 20,
    val archived: Boolean = false,
)

@Serializable
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val termId: String,
    val name: String,
    val teacher: String = "",
    val colorArgb: Long = 0xFF4F46E5,
    val defaultReminderMinutes: Int = 15,
    val notes: String = "",
    val archived: Boolean = false,
)

@Serializable
data class CourseSlot(
    val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val room: String = "",
    val startWeek: Int = 1,
    val endWeek: Int = 20,
    val weekPattern: WeekPattern = WeekPattern.EVERY,
    val weeks: List<Int> = emptyList(),
)

@Serializable
data class ScheduleException(
    val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val originalDate: String,
    val cancelled: Boolean = false,
    val replacementDate: String? = null,
    val replacementStartTime: String? = null,
    val replacementEndTime: String? = null,
    val replacementRoom: String? = null,
    val note: String = "",
)

@Serializable
data class MaterialItem(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val ownerType: String,
    val title: String,
    val uri: String,
    val kind: MaterialKind = MaterialKind.DOCUMENT,
    val week: Int? = null,
    val chapter: String = "",
    val tags: List<String> = emptyList(),
    val checksum: String = "",
    val addedAt: String,
)

@Serializable
data class LearningEvent(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val timestamp: String,
    val kind: LearningEventKind,
    val content: String,
    val sequence: Int,
    val source: String = "local",
)

@Serializable
data class LearningSession(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val ownerType: String,
    val startedAt: String,
    val endedAt: String? = null,
    val title: String,
    val summary: String = "",
    val rawTranscript: String = "",
    val transcriptComplete: Boolean = false,
)

@Serializable
data class StudyProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val colorArgb: Long = 0xFF0F766E,
    val currentProgress: String = "",
    val archived: Boolean = false,
)

@Serializable
data class NextMaterialPrediction(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val generatedAt: String,
    val title: String,
    val reason: String,
    val confidence: Float,
    val materialIds: List<String> = emptyList(),
    val sourceSessionId: String? = null,
)

@Serializable
data class ApiProfile(
    val name: String = "API易中转",
    val baseUrl: String = "https://api.apiyi.com/v1",
    val model: String = "gpt-5.6-luna",
    val useBackendProxy: Boolean = false,
    val backendUrl: String = "",
)

@Serializable
data class ChatGptLinkProfile(
    val enabled: Boolean = false,
    val relayUrl: String = "",
    val deviceId: String = UUID.randomUUID().toString(),
    val lastSequence: Long = 0,
    val lastSyncAt: String? = null,
)

@Serializable
data class PeriodTime(
    val period: Int,
    val startTime: String,
    val endTime: String,
)

@Serializable
data class ScheduleTimeProfile(
    val id: String = "scut-international",
    val name: String = "华南理工大学 · 大学城/国际校区",
    val periods: List<PeriodTime> = scutInternationalPeriods(),
)

fun scutInternationalPeriods(): List<PeriodTime> = listOf(
    PeriodTime(1, "08:50", "09:35"),
    PeriodTime(2, "09:40", "10:25"),
    PeriodTime(3, "10:40", "11:25"),
    PeriodTime(4, "11:30", "12:15"),
    PeriodTime(5, "14:00", "14:45"),
    PeriodTime(6, "14:50", "15:35"),
    PeriodTime(7, "15:45", "16:30"),
    PeriodTime(8, "16:35", "17:20"),
    PeriodTime(9, "19:00", "19:45"),
    PeriodTime(10, "19:55", "20:40"),
    PeriodTime(11, "20:50", "21:35"),
)

@Serializable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val reduceMotion: Boolean = false,
    val notificationLeadMinutes: Int = 15,
    val hideSensitiveOnLockScreen: Boolean = true,
    val biometricLock: Boolean = false,
    val earlyAlarmEnabled: Boolean = false,
    val earlyThreshold: String = "09:30",
    val wakeLeadMinutes: Int = 90,
    val rawTranscriptByDefault: Boolean = true,
    val encryptedRemoteBackupEnabled: Boolean = false,
    val updateChannel: String = "stable",
    val updateManifestUrl: String = "",
    val apiProfile: ApiProfile = ApiProfile(),
    val chatGptLink: ChatGptLinkProfile = ChatGptLinkProfile(),
    val scheduleTimeProfile: ScheduleTimeProfile = ScheduleTimeProfile(),
)

@Serializable
data class ImportedSlot(
    val courseName: String,
    val teacher: String = "",
    val dayOfWeek: Int,
    val startTime: String = "",
    val endTime: String = "",
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val room: String = "",
    val startWeek: Int = 1,
    val endWeek: Int = 20,
    val weekPattern: WeekPattern = WeekPattern.EVERY,
    val weeks: List<Int> = emptyList(),
    val confidence: Float = 1f,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class ImportedUnscheduledCourse(
    val courseName: String,
    val teacher: String = "",
    val weeks: List<Int> = emptyList(),
    val notes: String = "",
)

@Serializable
data class ImportDraft(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: String,
    val source: ImportSource,
    val sourceName: String,
    val slots: List<ImportedSlot>,
    val unscheduledCourses: List<ImportedUnscheduledCourse> = emptyList(),
    val warnings: List<String> = emptyList(),
    val termName: String? = null,
    val termStartDate: String? = null,
    val termWeekCount: Int? = null,
)

@Serializable
data class AppState(
    val schemaVersion: Int = 1,
    val terms: List<Term> = emptyList(),
    val courses: List<Course> = emptyList(),
    val slots: List<CourseSlot> = emptyList(),
    val exceptions: List<ScheduleException> = emptyList(),
    val materials: List<MaterialItem> = emptyList(),
    val sessions: List<LearningSession> = emptyList(),
    val events: List<LearningEvent> = emptyList(),
    val projects: List<StudyProject> = emptyList(),
    val predictions: List<NextMaterialPrediction> = emptyList(),
    val importDrafts: List<ImportDraft> = emptyList(),
    val appliedRelayChangeIds: List<String> = emptyList(),
    val preferences: AppPreferences = AppPreferences(),
    val activeTermId: String? = null,
)
