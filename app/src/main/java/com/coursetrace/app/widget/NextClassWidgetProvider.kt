package com.coursetrace.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.coursetrace.app.MainActivity
import com.coursetrace.app.R
import com.coursetrace.app.data.AppRepository
import com.coursetrace.app.domain.ScheduleEngine
import com.coursetrace.app.model.AppState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NextClassWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = AppRepository(context.applicationContext).state.value
        appWidgetIds.forEach { render(context, manager, it, state) }
    }

    companion object {
        fun updateAll(context: Context, state: AppState) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NextClassWidgetProvider::class.java))
            ids.forEach { render(context, manager, it, state) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, state: AppState) {
            val now = LocalDateTime.now()
            val next = ScheduleEngine.currentOrNext(state, now)
            val views = RemoteViews(context.packageName, R.layout.widget_next_class)
            if (next == null) {
                views.setTextViewText(R.id.widget_status, "课迹")
                views.setTextViewText(R.id.widget_title, "近期没有课程")
                views.setTextViewText(R.id.widget_detail, "点按打开课表或安排自学")
                views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, widgetId))
            } else {
                val ongoing = now in next.start..next.end
                val day = when (next.date) {
                    LocalDate.now() -> "今天"
                    LocalDate.now().plusDays(1) -> "明天"
                    else -> next.date.format(DateTimeFormatter.ofPattern("M月d日"))
                }
                views.setTextViewText(R.id.widget_status, if (ongoing) "正在上课" else "$day · 下一节")
                views.setTextViewText(R.id.widget_title, next.course.name)
                views.setTextViewText(
                    R.id.widget_detail,
                    "${next.start.toLocalTime()}–${next.end.toLocalTime()}  ${next.room.ifBlank { "教室待确认" }}",
                )
                val uri = Uri.parse("coursetrace://course/${next.course.id}?date=${next.date}")
                views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, widgetId, uri))
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun openAppIntent(context: Context, requestCode: Int, uri: Uri? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = if (uri == null) Intent.ACTION_MAIN else Intent.ACTION_VIEW
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
