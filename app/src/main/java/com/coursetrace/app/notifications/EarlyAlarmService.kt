package com.coursetrace.app.notifications

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.model.AppState
import java.time.LocalTime

class EarlyAlarmService(private val context: Context) {
    fun setNextEarlyClassAlarm(state: AppState): Result<String> = runCatching {
        val threshold = LocalTime.parse(state.preferences.earlyThreshold)
        val scheduled = ScheduleEngine.nextEarlyClass(state, threshold)
            ?: error("未来两周没有早课")
        val alarmAt = scheduled.start.minusMinutes(state.preferences.wakeLeadMinutes.toLong())
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarmAt.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarmAt.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "${scheduled.course.name} · ${scheduled.room}")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        require(intent.resolveActivity(context.packageManager) != null) { "系统没有可用的闹钟应用" }
        context.startActivity(intent)
        "已请求系统设置 ${alarmAt.toLocalDate()} ${alarmAt.toLocalTime()} 的闹钟"
    }
}
