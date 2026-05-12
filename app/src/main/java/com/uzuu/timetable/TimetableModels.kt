package com.uzuu.timetable

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StudyStatus {
    ONLINE,
    OFFLINE
}

data class ReminderTime(
    val hour: Int,
    val minute: Int,
)

data class TimetableEntry(
    val id: Long,
    val dayOfWeek: Int,
    val subject: String,
    val room: String,
    val cycleStartWeekOfYear: Int,
    val cycleLengthWeeks: Int,
    val onlineWeekIndices: List<Int>,
    val endMinuteOfDay: Int,
    val baseStatus: StudyStatus,
    val cycleEnabled: Boolean,
    val onlineOnOddWeeks: Boolean,
    val note: String = "",
) {
    fun effectiveStatus(currentWeekOfYear: Int): StudyStatus {
        if (!cycleEnabled) {
        val normalizedLength = cycleLengthWeeks.coerceAtLeast(1)
        val normalizedStart = cycleStartWeekOfYear.coerceAtLeast(1)
        val relativeWeekIndex = floorMod(currentWeekOfYear - normalizedStart, normalizedLength) + 1
        return if (onlineWeekIndices.contains(relativeWeekIndex)) {
            StudyStatus.ONLINE
        return if (onlineOnOddWeeks) {
            StudyStatus.OFFLINE
        } else {
            if (isOddWeek) StudyStatus.OFFLINE else StudyStatus.ONLINE
        }
    }
}

private val vietnameseLocale = Locale("vi", "VN")

fun dayLabel(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        Calendar.MONDAY -> "Thứ 2"
        Calendar.TUESDAY -> "Thứ 3"
        Calendar.WEDNESDAY -> "Thứ 4"
        Calendar.THURSDAY -> "Thứ 5"
        Calendar.FRIDAY -> "Thứ 6"
        Calendar.SATURDAY -> "Thứ 7"
        Calendar.SUNDAY -> "Chủ nhật"
        else -> "Không rõ"
    }
}

fun orderedWeekDays(): List<Int> {
    return listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY,
    )
}

fun formatTime(minuteOfDay: Int): String {
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    return String.format(vietnameseLocale, "%02d:%02d", hour, minute)
}

fun formatTodayLabel(calendar: Calendar = Calendar.getInstance()): String {
    val formatter = SimpleDateFormat("EEEE, dd/MM/yyyy", vietnameseLocale)
    return formatter.format(Date(calendar.timeInMillis))
}

fun currentWeekOfYear(calendar: Calendar = Calendar.getInstance()): Int {
    return calendar.get(Calendar.WEEK_OF_YEAR)
}

fun normalizeWeekIndex(value: Int, cycleLengthWeeks: Int): Int {
    if (cycleLengthWeeks <= 0) {
        return 1
    }

    val normalized = ((value - 1) % cycleLengthWeeks + cycleLengthWeeks) % cycleLengthWeeks
    return normalized + 1
}

fun floorMod(value: Int, divisor: Int): Int {
    return ((value % divisor) + divisor) % divisor
}

fun parseOnlineWeeks(input: String, cycleLengthWeeks: Int): List<Int>? {
    val normalizedLength = cycleLengthWeeks.coerceAtLeast(1)
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return null
    }

    val parsed = trimmed
        .split(',', ' ', ';')
        .mapNotNull { token -> token.trim().toIntOrNull() }
        .filter { index -> index in 1..normalizedLength }
        .distinct()

    return parsed.ifEmpty { null }
}

fun parseTimeToMinuteOfDay(input: String): Int? {
    val parts = input.trim().split(":")
    if (parts.size != 2) {
        return null
    }

    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) {
        return null
    }

    return hour * 60 + minute
}
