package com.uzuu.timetable

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

class AddClassFragment : Fragment(R.layout.fragment_add_class) {
    private companion object {
        const val DEBUG_TAG = "DEBUG"
    }

    private val firebaseRepository by lazy { FirebaseRepository() }
    private val draftEntries = mutableListOf<TimetableEntry>()

    private lateinit var classNameInput: TextInputEditText
    private lateinit var createdByInput: TextInputEditText
    private lateinit var addLessonButton: MaterialButton
    private lateinit var saveClassButton: MaterialButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var lessonCountText: TextView
    private lateinit var lessonsContainer: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(DEBUG_TAG, "AddClassFragment.onViewCreated")

        classNameInput = view.findViewById(R.id.classNameInput)
        createdByInput = view.findViewById(R.id.createdByInput)
        addLessonButton = view.findViewById(R.id.addLessonButton)
        saveClassButton = view.findViewById(R.id.saveClassButton)
        loadingProgress = view.findViewById(R.id.loadingProgress)
        lessonCountText = view.findViewById(R.id.lessonCountText)
        lessonsContainer = view.findViewById(R.id.lessonsContainer)

        addLessonButton.setOnClickListener { showLessonDialog() }
        saveClassButton.setOnClickListener { saveClass() }

        renderDraftLessons()
    }

    private fun showLessonDialog(existing: TimetableEntry? = null, editingIndex: Int? = null) {
        Log.d(DEBUG_TAG, "showLessonDialog: existing=${existing != null} editingIndex=$editingIndex")
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_timetable_entry, null, false)
        val subjectField = dialogView.findViewById<TextInputEditText>(R.id.subjectField)
        val roomField = dialogView.findViewById<TextInputEditText>(R.id.roomField)
        val dayField = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dayField)
        val startTimeField = dialogView.findViewById<TextInputEditText>(R.id.startTimeField)
        val endTimeField = dialogView.findViewById<TextInputEditText>(R.id.endTimeField)
        val statusField = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.statusField)
        val cycleSwitch = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.cycleSwitch)
        val repeatGapField = dialogView.findViewById<TextInputEditText>(R.id.repeatGapField)
        val noteField = dialogView.findViewById<TextInputEditText>(R.id.noteField)

        val dayOptions = orderedWeekDays().map(::dayLabel)
        val statusOptions = listOf("Online", "Offline")
        dayField.setAdapter(createDropdownAdapter(dayOptions))
        statusField.setAdapter(createDropdownAdapter(statusOptions))
        repeatGapField.setText("1")

        if (existing != null) {
            subjectField.setText(existing.subject)
            roomField.setText(existing.room)
            dayField.setText(dayLabel(existing.dayOfWeek), false)
            startTimeField.setText(formatTime(existing.startMinuteOfDay))
            endTimeField.setText(formatTime(existing.endMinuteOfDay))
            statusField.setText(if (existing.baseStatus == StudyStatus.ONLINE) "Online" else "Offline", false)
            cycleSwitch.isChecked = existing.cycleEnabled
            repeatGapField.setText(existing.repeatGapWeeks.toString())
            noteField.setText(existing.note)
        } else {
            dayField.setText(dayOptions.first(), false)
            statusField.setText(statusOptions.first(), false)
        }

        fun updateCycleVisibility() {
            dialogView.findViewById<View>(R.id.cycleFieldsContainer).visibility = if (cycleSwitch.isChecked) View.VISIBLE else View.GONE
        }

        updateCycleVisibility()
        cycleSwitch.setOnCheckedChangeListener { _, _ -> updateCycleVisibility() }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Thêm tiết học" else "Sửa tiết học")
            .setView(dialogView)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ ->
                val subject = subjectField.text?.toString()?.trim().orEmpty()
                val room = roomField.text?.toString()?.trim().orEmpty()
                val dayLabelValue = dayField.text?.toString()?.trim().orEmpty()
                val startText = startTimeField.text?.toString()?.trim().orEmpty()
                val endText = endTimeField.text?.toString()?.trim().orEmpty()
                val statusText = statusField.text?.toString()?.trim().orEmpty()
                val note = noteField.text?.toString()?.trim().orEmpty()
                val cycleEnabled = cycleSwitch.isChecked
                val repeatGapWeeks = repeatGapField.text?.toString()?.trim()?.toIntOrNull() ?: 1

                val dayOfWeek = orderedWeekDays().firstOrNull { dayLabel(it) == dayLabelValue }
                val startMinute = parseTimeToMinuteOfDay(startText)
                val endMinute = parseTimeToMinuteOfDay(endText)
                val baseStatus = if (statusText == "Offline") StudyStatus.OFFLINE else StudyStatus.ONLINE

                if (subject.isBlank() || room.isBlank() || dayOfWeek == null || startMinute == null || endMinute == null) {
                    Toast.makeText(requireContext(), "Vui lòng nhập đủ và đúng thông tin tiết học", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (endMinute <= startMinute) {
                    Toast.makeText(requireContext(), "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val draft = (existing ?: TimetableEntry(
                    id = System.currentTimeMillis(),
                    dayOfWeek = Calendar.MONDAY,
                    subject = "",
                    room = "",
                    startMinuteOfDay = 0,
                    endMinuteOfDay = 0,
                    baseStatus = StudyStatus.ONLINE,
                    cycleEnabled = false,
                    cycleStartWeekOfYear = currentWeekOfYear(),
                    repeatGapWeeks = 1,
                )).copy(
                    id = existing?.id ?: System.currentTimeMillis(),
                    subject = subject,
                    room = room,
                    dayOfWeek = dayOfWeek,
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    baseStatus = baseStatus,
                    cycleEnabled = cycleEnabled,
                    cycleStartWeekOfYear = currentWeekOfYear(),
                    repeatGapWeeks = if (cycleEnabled) repeatGapWeeks.coerceAtLeast(1) else 1,
                    note = note,
                )

                if (editingIndex != null && editingIndex in draftEntries.indices) {
                    draftEntries[editingIndex] = draft
                } else {
                    draftEntries.add(draft)
                }
                renderDraftLessons()
            }
            .show()
    }

    private fun saveClass() {
        val className = classNameInput.text?.toString()?.trim().orEmpty()
        val createdBy = createdByInput.text?.toString()?.trim().orEmpty()

        Log.d(DEBUG_TAG, "saveClass: pressed className='$className' createdBy='$createdBy' draftCount=${draftEntries.size}")

        if (className.isBlank()) {
            classNameInput.error = "Nhập tên lớp học"
            Log.d(DEBUG_TAG, "saveClass: validation failed because className is blank")
            return
        }
        if (draftEntries.isEmpty()) {
            Log.d(DEBUG_TAG, "saveClass: validation failed because draftEntries is empty")
            Toast.makeText(requireContext(), "Hãy thêm ít nhất một tiết học", Toast.LENGTH_SHORT).show()
            return
        }

        loadingProgress.visibility = View.VISIBLE
        saveClassButton.isEnabled = false
        lifecycleScope.launch {
            try {
                Log.d(DEBUG_TAG, "saveClass: sending data to Firebase")
                val success = withTimeoutOrNull(15000) {
                    firebaseRepository.saveClass(
                        ClassTimetable(
                            className = className,
                            entries = draftEntries.sortedWith(compareBy<TimetableEntry> { it.dayOfWeek }.thenBy { it.startMinuteOfDay }),
                            createdBy = if (createdBy.isBlank()) "manual" else createdBy,
                        )
                    )
                }

                when {
                    success == true -> {
                        Log.d(DEBUG_TAG, "saveClass: success")
                        Toast.makeText(requireContext(), "✓ Đã lưu lớp học mới", Toast.LENGTH_SHORT).show()
                        classNameInput.text?.clear()
                        createdByInput.text?.clear()
                        draftEntries.clear()
                        renderDraftLessons()
                    }
                    success == false -> {
                        Log.d(DEBUG_TAG, "saveClass: failed or rejected by Firebase")
                        Toast.makeText(
                            requireContext(),
                            "✗ Firebase lỗi. Kiểm tra:\n1. Kết nối Internet\n2. Firebase Rules\n3. Logcat",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> { // Timeout
                        Log.d(DEBUG_TAG, "saveClass: timeout after 15000ms")
                        Toast.makeText(requireContext(), "⏱ Hết giờ. Kiểm tra kết nối Internet", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.d(DEBUG_TAG, "saveClass: exception ${e.message}", e)
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingProgress.visibility = View.GONE
                saveClassButton.isEnabled = true
            }
        }
    }

    private fun renderDraftLessons() {
        Log.d(DEBUG_TAG, "renderDraftLessons: draftCount=${draftEntries.size}")
        lessonsContainer.removeAllViews()
        lessonCountText.text = if (draftEntries.isEmpty()) {
            "Chưa có tiết học nào"
        } else {
            "Đã thêm ${draftEntries.size} tiết học"
        }

        if (draftEntries.isEmpty()) {
            lessonsContainer.addView(TextView(requireContext()).apply {
                text = "Danh sách tiết học sẽ xuất hiện ở đây"
                setTextColor(0xFF666666.toInt())
                textSize = 14f
            })
            return
        }

        draftEntries.forEachIndexed { index, entry ->
            lessonsContainer.addView(createDraftCard(index, entry))
        }
    }

    private fun createDraftCard(index: Int, entry: TimetableEntry): MaterialCardView {
        val card = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_timetable_entry, lessonsContainer, false) as MaterialCardView

        val subjectText = card.findViewById<TextView>(R.id.subjectText)
        val timeText = card.findViewById<TextView>(R.id.timeText)
        val roomText = card.findViewById<TextView>(R.id.roomText)
        val noteText = card.findViewById<TextView>(R.id.noteText)
        val statusChip = card.findViewById<com.google.android.material.chip.Chip>(R.id.statusChip)
        val cycleChip = card.findViewById<com.google.android.material.chip.Chip>(R.id.cycleChip)
        val editButton = card.findViewById<MaterialButton>(R.id.editButton)
        val deleteButton = card.findViewById<MaterialButton>(R.id.deleteButton)

        subjectText.text = entry.subject
        timeText.text = "${dayLabel(entry.dayOfWeek)} • ${formatTime(entry.startMinuteOfDay)} - ${formatTime(entry.endMinuteOfDay)}"
        roomText.text = "Phòng: ${entry.room}"

        if (entry.note.isBlank()) {
            noteText.visibility = View.GONE
        } else {
            noteText.visibility = View.VISIBLE
            noteText.text = entry.note
        }

        val statusColor = if (entry.baseStatus == StudyStatus.ONLINE) R.color.status_online else R.color.status_offline
        statusChip.text = if (entry.baseStatus == StudyStatus.ONLINE) "Online" else "Offline"
        statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), statusColor)
        )

        if (entry.cycleEnabled) {
            cycleChip.text = "Chu kì: N=${entry.repeatGapWeeks}"
            cycleChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.cycle_active)
            )
        } else {
            cycleChip.text = "Chu kì: tắt"
            cycleChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.cycle_inactive)
            )
        }

        editButton.text = "Sửa"
        deleteButton.text = "Xóa"
        editButton.setOnClickListener { showLessonDialog(entry, index) }
        deleteButton.setOnClickListener {
            draftEntries.removeAt(index)
            renderDraftLessons()
        }

        return card
    }

    private fun createDropdownAdapter(options: List<String>) =
        android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)

    private fun orderedWeekDays() = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY,
    )

    private fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.MONDAY -> "Thứ 2"
        Calendar.TUESDAY -> "Thứ 3"
        Calendar.WEDNESDAY -> "Thứ 4"
        Calendar.THURSDAY -> "Thứ 5"
        Calendar.FRIDAY -> "Thứ 6"
        Calendar.SATURDAY -> "Thứ 7"
        else -> "Chủ nhật"
    }

    private fun parseTimeToMinuteOfDay(input: String): Int? {
        val parts = input.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun formatTime(minuteOfDay: Int): String {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        return String.format("%02d:%02d", hour, minute)
    }

    private fun currentWeekOfYear(): Int = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
