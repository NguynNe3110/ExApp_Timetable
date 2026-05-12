package com.uzuu.timetable

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val repository = TimetableRepository(context)
        val reminderTime = repository.loadReminderTime() ?: return
        val entries = repository.loadEntries()
        val today = Calendar.getInstance()
        val todayEntries = entries
            .filter { it.dayOfWeek == today.get(Calendar.DAY_OF_WEEK) }
            .sortedBy { it.startMinuteOfDay }

        val weekOfYear = currentWeekOfYear(today)
        val message = if (todayEntries.isEmpty()) {
            "Hôm nay chưa có môn học nào được lên lịch."
        } else {
            val summary = todayEntries.joinToString(separator = " • ") { entry ->
                val statusText = if (entry.effectiveStatus(weekOfYear) == StudyStatus.ONLINE) "Online" else "Offline"
                "${formatTime(entry.startMinuteOfDay)} ${entry.subject} - Phòng ${entry.room} ($statusText)"
            }
            "Hôm nay có ${todayEntries.size} môn: $summary"
        }

        NotificationScheduler.showNotification(
            context = context,
            title = "Nhắc thời khóa biểu hôm nay",
            message = message,
        )

        NotificationScheduler.schedule(context, reminderTime)
    }
}
