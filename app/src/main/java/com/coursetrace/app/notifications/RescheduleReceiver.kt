package com.coursetrace.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coursetrace.app.data.AppRepository

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val allowedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
        if (intent?.action !in allowedActions) return
        NotificationScheduler(context).reschedule(AppRepository(context.applicationContext).state.value)
    }
}
