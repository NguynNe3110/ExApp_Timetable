package com.uzuu.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminderTime = TimetableRepository(context).loadReminderTime() ?: return
        NotificationScheduler.schedule(context, reminderTime)
    }
}
