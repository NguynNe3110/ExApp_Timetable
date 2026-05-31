package com.uzuu.timetable

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

class HomeFragment : Fragment(R.layout.fragment_home) {
    private val repository by lazy { TimetableRepository(requireContext()) }
    private val entries = mutableListOf<TimetableEntry>()

    private lateinit var weekContainer: LinearLayout
    private lateinit var nextWeekContainer: LinearLayout
    private lateinit var nextWeekBadgeText: TextView
    private lateinit var toggleNextWeekButton: MaterialButton
    private lateinit var todaySummaryText: TextView
    private lateinit var clockText: TextView
    private lateinit var reminderText: TextView

    /** true = section preview đang hiển thị */
    private var nextWeekExpanded = true

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            showToast("Đã lưu giờ nhắc, nhưng bạn chưa cấp quyền thông báo")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        entries.clear()
        entries.addAll(repository.loadEntries())
        renderAll()

        repository.loadReminderTime()?.let { reminderTime ->
            NotificationScheduler.schedule(requireContext(), reminderTime)
        }
    }

    private fun bindViews() {
        val view = requireView()
        weekContainer = view.findViewById(R.id.weekContainer)
        nextWeekContainer = view.findViewById(R.id.nextWeekContainer)
        nextWeekBadgeText = view.findViewById(R.id.nextWeekBadgeText)
        toggleNextWeekButton = view.findViewById(R.id.toggleNextWeekButton)
        todaySummaryText = view.findViewById(R.id.todaySummaryText)
        clockText = view.findViewById(R.id.clockText)
        reminderText = view.findViewById(R.id.reminderText)
    }

    private fun setupButtons() {
        val view = requireView()

        view.findViewById<MaterialButton>(R.id.addLessonButton).setOnClickListener {
            it.withClickFeedback { showEntryDialog(null) }
        }

        view.findViewById<MaterialButton>(R.id.setReminderButton).setOnClickListener {
            it.withClickFeedback { chooseReminderTime() }
        }

        view.findViewById<MaterialButton>(R.id.clearAllButton).setOnClickListener {
            it.withClickFeedback { confirmClearAll() }
        }

        view.findViewById<MaterialButton>(R.id.cancelReminderButton).setOnClickListener {
            it.withClickFeedback {
                repository.saveReminderTime(null)
                NotificationScheduler.cancel(requireContext())
                renderAll()
                showToast("Đã tắt nhắc lịch")
            }
        }

        toggleNextWeekButton.setOnClickListener {
            nextWeekExpanded = !nextWeekExpanded
            applyNextWeekVisibility()
        }
    }

    private fun applyNextWeekVisibility() {
        nextWeekContainer.visibility = if (nextWeekExpanded) View.VISIBLE else View.GONE
        toggleNextWeekButton.text = if (nextWeekExpanded) "Ẩn" else "Hiện"
    }

    private fun renderAll() {
        renderSummary()
        renderWeekSections()
        renderNextWeekPreview()
        renderReminderState()
    }

    private fun renderSummary() {
        val calendar = Calendar.getInstance()
        val todayEntries = entries
            .filter { it.dayOfWeek == calendar.get(Calendar.DAY_OF_WEEK) }
            .sortedBy { it.startMinuteOfDay }

        val weekNumber = currentWeekOfYear(calendar)
        val todayLabel = formatTodayLabel(calendar)
        val nextClassForClock = todayEntries.firstOrNull {
            it.startMinuteOfDay >= calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        } ?: todayEntries.firstOrNull()

        clockText.text = if (nextClassForClock != null) {
            "$todayLabel • Phòng: ${nextClassForClock.room}"
        } else {
            todayLabel
        }

        todaySummaryText.text = when {
            todayEntries.isEmpty() -> "Hôm nay chưa có môn học nào."
            else -> {
                val nextClass = todayEntries.firstOrNull {
                    it.startMinuteOfDay >= calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                } ?: todayEntries.first()
                val nextStatus = nextClass.effectiveStatus(weekNumber)
                "${todayEntries.size} môn hôm nay. Tiết gần nhất: ${nextClass.subject} lúc ${formatTime(nextClass.startMinuteOfDay)} - Phòng ${nextClass.room} (${statusLabel(nextStatus)})"
            }
        }
    }

    private fun renderReminderState() {
        val reminder = repository.loadReminderTime()
        reminderText.text = if (reminder == null) {
            "Chưa đặt thời gian nhắc"
        } else {
            "Sẽ nhắc mỗi ngày lúc ${formatReminderTime(reminder)}"
        }
    }

    private fun renderWeekSections() {
        weekContainer.removeAllViews()
        val currentWeek = currentWeekOfYear()

        orderedWeekDays().forEach { dayOfWeek ->
            val dayEntries = entries
                .filter { it.dayOfWeek == dayOfWeek }
                .sortedWith(compareBy<TimetableEntry> { it.startMinuteOfDay }.thenBy { it.subject.lowercase() })

            weekContainer.addView(createDayCard(dayOfWeek, dayEntries, currentWeek, 0))
        }
    }

    // ────────────────────────────────────────────────
    //  Preview tuần tiếp theo
    // ────────────────────────────────────────────────

    private fun renderNextWeekPreview() {
        nextWeekContainer.removeAllViews()
        val nextWeek = currentWeekOfYear() + 1  // +1 tuần so với hiện tại

        // Badge mô tả
        nextWeekBadgeText.text = "Tuần tới (tuần $nextWeek) · trạng thái theo chu kì"

        orderedWeekDays().forEach { dayOfWeek ->
            val dayEntries = entries
                .filter { it.dayOfWeek == dayOfWeek }
                .sortedWith(compareBy<TimetableEntry> { it.startMinuteOfDay }.thenBy { it.subject.lowercase() })

            nextWeekContainer.addView(
                createDayCard(
                    dayOfWeek = dayOfWeek,
                    dayEntries = dayEntries,
                    weekOfYear = nextWeek,
                    weekOffset = 1,
                    isPreview = true,
                )
            )
        }

        applyNextWeekVisibility()
    }

    // ────────────────────────────────────────────────
    //  Card builders
    // ────────────────────────────────────────────────

    /**
     * @param isPreview  true → ẩn nút Sửa/Xóa, background nhạt hơn để phân biệt
     */
    private fun createDayCard(
        dayOfWeek: Int,
        dayEntries: List<TimetableEntry>,
        weekOfYear: Int,
        weekOffset: Int,
        isPreview: Boolean = false,
    ): MaterialCardView {
        val context = requireContext()

        // Preview dùng background hơi khác để người dùng biết đây là "xem trước"
        val bgColor = if (isPreview) {
            getColorCompat(R.color.day_card_preview_background)
        } else {
            getColorCompat(R.color.day_card_background)
        }

        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
            radius = dp(12).toFloat()
            setCardBackgroundColor(bgColor)
            strokeColor = getColorCompat(R.color.day_card_stroke)
            strokeWidth = dp(1)
            cardElevation = 0f
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        content.addView(TextView(context).apply {
            text = "${dayLabel(dayOfWeek)} · ${dayEntries.size} môn · ${formatWeekDayLabel(dayOfWeek, weekOffset)}"
            textSize = 17f
            setTextColor(getColorCompat(android.R.color.black))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        if (dayEntries.isEmpty()) {
            content.addView(TextView(context).apply {
                text = "Chưa có môn được thêm vào ngày này"
                textSize = 14f
                setTextColor(getColorCompat(R.color.day_card_empty_text))
                setPadding(0, dp(8), 0, 0)
            })
        } else {
            dayEntries.forEach { entry ->
                content.addView(createEntryCard(entry, weekOfYear, isPreview))
            }
        }

        card.addView(content)
        return card
    }

    private fun createEntryCard(entry: TimetableEntry, weekOfYear: Int, isPreview: Boolean = false): View {
        val card = layoutInflater.inflate(R.layout.item_timetable_entry, weekContainer, false) as MaterialCardView
        val subjectText = card.findViewById<TextView>(R.id.subjectText)
        val timeText = card.findViewById<TextView>(R.id.timeText)
        val roomText = card.findViewById<TextView>(R.id.roomText)
        val noteText = card.findViewById<TextView>(R.id.noteText)
        val statusChip = card.findViewById<Chip>(R.id.statusChip)
        val cycleChip = card.findViewById<Chip>(R.id.cycleChip)
        val editButton = card.findViewById<MaterialButton>(R.id.editButton)
        val deleteButton = card.findViewById<MaterialButton>(R.id.deleteButton)

        subjectText.text = entry.subject
        timeText.text = "${formatTime(entry.startMinuteOfDay)} - ${formatTime(entry.endMinuteOfDay)}"
        roomText.text = "Phòng: ${entry.room}"

        if (entry.note.isBlank()) {
            noteText.visibility = View.GONE
        } else {
            noteText.visibility = View.VISIBLE
            noteText.text = entry.note
        }

        val effectiveStatus = entry.effectiveStatus(weekOfYear)
        statusChip.text = statusLabel(effectiveStatus)
        statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
            getColorCompat(if (effectiveStatus == StudyStatus.ONLINE) R.color.status_online else R.color.status_offline)
        )
        statusChip.setTextColor(getColorCompat(android.R.color.black))

        if (entry.cycleEnabled) {
            cycleChip.text = "Chu kì: N=${entry.repeatGapWeeks}"
            cycleChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(getColorCompat(R.color.cycle_active))
        } else {
            cycleChip.text = "Chu kì: tắt"
            cycleChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(getColorCompat(R.color.cycle_inactive))
        }
        cycleChip.setTextColor(getColorCompat(android.R.color.black))

        if (isPreview) {
            // Trong preview: ẩn nút sửa/xóa — chỉ xem
            editButton.visibility = View.GONE
            deleteButton.visibility = View.GONE
        } else {
            editButton.setOnClickListener { it.withClickFeedback { showEntryDialog(entry) } }
            deleteButton.setOnClickListener { it.withClickFeedback { confirmDelete(entry) } }
        }

        return card
    }

    // ────────────────────────────────────────────────
    //  Dialog & actions (không đổi so với bản gốc)
    // ────────────────────────────────────────────────

    private fun showEntryDialog(existing: TimetableEntry?) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_timetable_entry, null, false)
        val subjectField = view.findViewById<TextInputEditText>(R.id.subjectField)
        val roomField = view.findViewById<TextInputEditText>(R.id.roomField)
        val dayField = view.findViewById<MaterialAutoCompleteTextView>(R.id.dayField)
        val startTimeField = view.findViewById<TextInputEditText>(R.id.startTimeField)
        val endTimeField = view.findViewById<TextInputEditText>(R.id.endTimeField)
        val statusField = view.findViewById<MaterialAutoCompleteTextView>(R.id.statusField)
        val cycleSwitch = view.findViewById<MaterialSwitch>(R.id.cycleSwitch)
        val repeatGapField = view.findViewById<TextInputEditText>(R.id.repeatGapField)
        val noteField = view.findViewById<TextInputEditText>(R.id.noteField)

        val dayOptions = orderedWeekDays().map(::dayLabel)
        val statusOptions = listOf("Online", "Offline")

        dayField.setAdapter(createDropdownAdapter(dayOptions))
        statusField.setAdapter(createDropdownAdapter(statusOptions))

        if (existing != null) {
            subjectField.setText(existing.subject)
            roomField.setText(existing.room)
            dayField.setText(dayLabel(existing.dayOfWeek), false)
            startTimeField.setText(formatTime(existing.startMinuteOfDay))
            endTimeField.setText(formatTime(existing.endMinuteOfDay))
            statusField.setText(statusLabel(existing.baseStatus), false)
            cycleSwitch.isChecked = existing.cycleEnabled
            repeatGapField.setText(existing.repeatGapWeeks.toString())
            noteField.setText(existing.note)
        } else {
            dayField.setText(dayOptions.first(), false)
            statusField.setText(statusOptions.first(), false)
            repeatGapField.setText("1")
        }

        fun updateCycleFieldsVisibility() {
            view.findViewById<View>(R.id.cycleFieldsContainer).visibility =
                if (cycleSwitch.isChecked) View.VISIBLE else View.GONE
        }

        updateCycleFieldsVisibility()
        cycleSwitch.setOnCheckedChangeListener { _, _ -> updateCycleFieldsVisibility() }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) "Thêm môn học" else "Chỉnh sửa môn học")
            .setView(view)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val subject = subjectField.text?.toString()?.trim().orEmpty()
                val room = roomField.text?.toString()?.trim().orEmpty()
                val dayLabelValue = dayField.text?.toString()?.trim().orEmpty()
                val startTimeText = startTimeField.text?.toString()?.trim().orEmpty()
                val endTimeText = endTimeField.text?.toString()?.trim().orEmpty()
                val statusText = statusField.text?.toString()?.trim().orEmpty()
                val note = noteField.text?.toString()?.trim().orEmpty()
                val cycleEnabled = cycleSwitch.isChecked
                val repeatGapWeeks = repeatGapField.text?.toString()?.trim()?.toIntOrNull()

                val dayOfWeek = orderedWeekDays().firstOrNull { dayLabel(it) == dayLabelValue }
                val startMinute = parseTimeToMinuteOfDay(startTimeText)
                val endMinute = parseTimeToMinuteOfDay(endTimeText)
                val baseStatus = if (statusText == "Offline") StudyStatus.OFFLINE else StudyStatus.ONLINE

                if (subject.isBlank()) { subjectField.error = "Nhập tên môn học"; return@setOnClickListener }
                if (room.isBlank()) { roomField.error = "Nhập phòng học"; return@setOnClickListener }
                if (dayOfWeek == null) { showToast("Chọn một ngày trong tuần"); return@setOnClickListener }
                if (startMinute == null || endMinute == null) { showToast("Giờ phải theo định dạng HH:mm, ví dụ 07:30"); return@setOnClickListener }
                if (endMinute <= startMinute) { showToast("Giờ kết thúc phải sau giờ bắt đầu"); return@setOnClickListener }
                if (cycleEnabled && (repeatGapWeeks == null || repeatGapWeeks < 1)) {
                    showToast("Khoảng cách lặp lại phải từ 1 tuần trở lên")
                    return@setOnClickListener
                }

                val entry = TimetableEntry(
                    id = existing?.id ?: System.currentTimeMillis(),
                    dayOfWeek = dayOfWeek,
                    subject = subject,
                    room = room,
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    baseStatus = baseStatus,
                    cycleEnabled = cycleEnabled,
                    cycleStartWeekOfYear = if (cycleEnabled) currentWeekOfYear() else 1,
                    repeatGapWeeks = repeatGapWeeks ?: 2,
                    note = note,
                )

                upsertEntry(entry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun chooseReminderTime() {
        val reminder = repository.loadReminderTime() ?: ReminderTime(7, 0)
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val selectedTime = ReminderTime(hourOfDay, minute)
                persistReminderTime(selectedTime)
                requestNotificationPermissionIfNeeded()
            },
            reminder.hour,
            reminder.minute,
            true,
        ).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        requestExactAlarmPermissionIfNeeded()
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Cần quyền đặt báo thức")
                    .setMessage("Để ứng dụng nhắc lịch học đúng giờ, bạn cần cho phép ứng dụng đặt báo thức chính xác trong cài đặt hệ thống.")
                    .setPositiveButton("Đi đến cài đặt") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.fromParts("package", requireContext().packageName, null)
                        })
                    }
                    .setNegativeButton("Để sau", null)
                    .show()
            }
        }
    }

    private fun persistReminderTime(reminderTime: ReminderTime) {
        repository.saveReminderTime(reminderTime)
        NotificationScheduler.schedule(requireContext(), reminderTime)
        renderAll()
        showToast("Đã đặt nhắc lịch lúc ${formatReminderTime(reminderTime)}")
    }

    private fun confirmDelete(entry: TimetableEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa môn học")
            .setMessage("Xóa \"${entry.subject}\" khỏi ${dayLabel(entry.dayOfWeek)}?")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                entries.removeAll { it.id == entry.id }
                repository.saveEntries(entries)
                renderAll()
            }
            .show()
    }

    private fun confirmClearAll() {
        if (entries.isEmpty()) {
            showToast("Chưa có dữ liệu để xóa")
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa toàn bộ thời khóa biểu")
            .setMessage("Hành động này sẽ xóa toàn bộ môn học trong tuần. Bạn muốn tiếp tục không?")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa hết") { _, _ ->
                entries.clear()
                repository.saveEntries(entries)
                renderAll()
                showToast("Đã xóa toàn bộ thời khóa biểu")
            }
            .show()
    }

    private fun upsertEntry(entry: TimetableEntry) {
        val existingIndex = entries.indexOfFirst { it.id == entry.id }
        if (existingIndex >= 0) {
            entries[existingIndex] = entry
        } else {
            entries.add(entry)
        }
        entries.sortWith(
            compareBy<TimetableEntry> { orderedWeekDays().indexOf(it.dayOfWeek) }
                .thenBy { it.startMinuteOfDay }
                .thenBy { it.subject.lowercase() }
        )
        repository.saveEntries(entries)
        renderAll()
    }

    // ────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────

    private fun createDropdownAdapter(options: List<String>): ArrayAdapter<String> =
        ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)

    private fun statusLabel(status: StudyStatus): String =
        if (status == StudyStatus.ONLINE) "Online" else "Offline"

    private fun formatReminderTime(reminderTime: ReminderTime): String =
        String.format("%02d:%02d", reminderTime.hour, reminderTime.minute)

    private fun getColorCompat(colorResId: Int): Int =
        ContextCompat.getColor(requireContext(), colorResId)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showToast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

/**
 * Feedback xúc giác + animation thu nhỏ khi nhấn.
 * Tự động scale về 1f sau khi action hoàn tất.
 */
fun View.withClickFeedback(action: () -> Unit) {
    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    animate()
        .scaleX(0.93f).scaleY(0.93f)
        .setDuration(65)
        .withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(110).start()
        }
        .start()
    action()
}