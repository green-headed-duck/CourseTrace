package com.coursetrace.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.os.Build
import com.coursetrace.app.data.AppRepository
import com.coursetrace.app.domain.ScheduleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class CourseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = AppRepository(context.applicationContext).state.value
                val courseId = intent.getStringExtra(NotificationScheduler.EXTRA_COURSE_ID)
                val date = intent.getStringExtra(NotificationScheduler.EXTRA_DATE)?.let(LocalDate::parse)
                    ?: LocalDate.now()
                val scheduled = ScheduleEngine.classesOn(state, date).find { it.course.id == courseId }
                when (intent.getStringExtra(NotificationScheduler.EXTRA_MODE) ?: NotificationScheduler.MODE_REMINDER) {
                    NotificationScheduler.MODE_END -> context.getSystemService(NotificationManager::class.java)
                        .cancel(NotificationScheduler.LIVE_NOTIFICATION_ID)
                    NotificationScheduler.MODE_START,
                    NotificationScheduler.MODE_LIVE -> if (scheduled != null) {
                        val scheduler = NotificationScheduler(context)
                        if (Build.VERSION.SDK_INT >= 36) scheduler.showLiveClass(scheduled)
                        else scheduler.showReminder(scheduled)
                    }
                    else -> if (scheduled != null) NotificationScheduler(context).showReminder(scheduled)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
