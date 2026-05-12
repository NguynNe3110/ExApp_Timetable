package com.uzuu.timetable

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

object NotificationScheduler {
    const val CHANNEL_ID = "timetable_reminders"
    private const val CHANNEL_NAME = "Nhắc thời khóa biểu"
    private const val CHANNEL_DESCRIPTION = "Thông báo lịch học hằng ngày"
    private const val REQUEST_CODE_REMINDER = 2001

    fun schedule(context: Context, reminderTime: ReminderTime) {
        ensureChannel(context)

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return

        val reminderIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REMINDER,
            Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_REMIND
            },
            pendingIntentFlags(),
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_REMINDER,
            Intent(context, MainActivity::class.java),
            pendingIntentFlags(),
        )

        val nextTrigger = nextTriggerMillis(reminderTime.hour, reminderTime.minute)

        val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canUseExact) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(nextTrigger, contentIntent),
                reminderIntent,
            )
        } else {
            // Fallback for devices where exact alarm permission is blocked.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTrigger,
                reminderIntent,
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val reminderIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_REMINDER,
            Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_REMIND
            },
            pendingIntentFlags(),
        )
        alarmManager.cancel(reminderIntent)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        manager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, title: String, message: String) {
        ensureChannel(context)

        val openIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_REMINDER,
            Intent(context, MainActivity::class.java),
            pendingIntentFlags(),
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    const val ACTION_REMIND = "com.uzuu.timetable.ACTION_REMIND"
    const val REMINDER_NOTIFICATION_ID = 2302
}
