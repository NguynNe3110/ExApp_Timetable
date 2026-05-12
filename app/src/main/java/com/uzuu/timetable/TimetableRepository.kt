package com.uzuu.timetable

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class TimetableRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadEntries(): MutableList<TimetableEntry> {
        val rawEntries = preferences.getString(KEY_ENTRIES, "[]") ?: "[]"
        val jsonArray = JSONArray(rawEntries)
        val entries = mutableListOf<TimetableEntry>()

        for (index in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(index) ?: continue
            entries += item.toTimetableEntry()
        }

        return entries
    }

    fun saveEntries(entries: List<TimetableEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            jsonArray.put(entry.toJson())
        }
        preferences.edit().putString(KEY_ENTRIES, jsonArray.toString()).apply()
    }

    fun loadReminderTime(): ReminderTime? {
        if (!preferences.contains(KEY_REMINDER_ENABLED) || !preferences.getBoolean(KEY_REMINDER_ENABLED, false)) {
            return null
        }

        val hour = preferences.getInt(KEY_REMINDER_HOUR, -1)
        val minute = preferences.getInt(KEY_REMINDER_MINUTE, -1)
        if (hour !in 0..23 || minute !in 0..59) {
            return null
        }

        return ReminderTime(hour, minute)
    }

    fun saveReminderTime(reminderTime: ReminderTime?) {
        val editor = preferences.edit()
        if (reminderTime == null) {
            editor.putBoolean(KEY_REMINDER_ENABLED, false)
            editor.remove(KEY_REMINDER_HOUR)
            editor.remove(KEY_REMINDER_MINUTE)
        } else {
            editor.putBoolean(KEY_REMINDER_ENABLED, true)
            editor.putInt(KEY_REMINDER_HOUR, reminderTime.hour)
            editor.putInt(KEY_REMINDER_MINUTE, reminderTime.minute)
        }
        editor.apply()
    }

    private fun TimetableEntry.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("dayOfWeek", dayOfWeek)
            .put("subject", subject)
            .put("room", room)
            .put("startMinuteOfDay", startMinuteOfDay)
            .put("endMinuteOfDay", endMinuteOfDay)
            .put("baseStatus", baseStatus.name)
            .put("cycleEnabled", cycleEnabled)
            .put("cycleStartWeekOfYear", cycleStartWeekOfYear)
            .put("repeatGapWeeks", repeatGapWeeks)
            .put("note", note)
    }

    private fun JSONObject.toTimetableEntry(): TimetableEntry {
        val cycleEnabled = optBoolean("cycleEnabled", false)
        val oldOnlineOnOddWeeks = optBoolean("onlineOnOddWeeks", false)
        val legacyCycleLengthWeeks = optInt("cycleLengthWeeks", if (cycleEnabled) 3 else 1).coerceAtLeast(1)
        val cycleStartWeekOfYear = optInt("cycleStartWeekOfYear", 1).coerceAtLeast(1)
        val repeatGapWeeks = when {
            has("repeatGapWeeks") -> optInt("repeatGapWeeks", 2).coerceAtLeast(1)
            has("cycleLengthWeeks") -> (legacyCycleLengthWeeks - 1).coerceAtLeast(1)
            has("onlineOnOddWeeks") -> if (oldOnlineOnOddWeeks) 1 else 2
            else -> 2
        }

        return TimetableEntry(
            id = optLong("id", System.currentTimeMillis()),
            dayOfWeek = optInt("dayOfWeek", java.util.Calendar.MONDAY),
            subject = optString("subject", ""),
            room = optString("room", ""),
            startMinuteOfDay = optInt("startMinuteOfDay", 0),
            endMinuteOfDay = optInt("endMinuteOfDay", 0),
            baseStatus = runCatching {
                StudyStatus.valueOf(optString("baseStatus", StudyStatus.ONLINE.name))
            }.getOrDefault(StudyStatus.ONLINE),
            cycleEnabled = cycleEnabled,
            cycleStartWeekOfYear = cycleStartWeekOfYear,
            repeatGapWeeks = repeatGapWeeks,
            note = optString("note", ""),
        )
    }

    companion object {
        private const val PREFS_NAME = "timetable_store"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
    }
}
