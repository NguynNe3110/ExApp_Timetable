package com.uzuu.timetable

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private val repository by lazy { TimetableRepository(this) }
    private val entries = mutableListOf<TimetableEntry>()

    private lateinit var weekContainer: LinearLayout
    private lateinit var todaySummaryText: TextView
    private lateinit var clockText: TextView
    private lateinit var reminderText: TextView

    private var pendingReminderTime: ReminderTime? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingReminderTime?.let { saveReminderTime(it) }
        } else {
            pendingReminderTime = null
            showToast("Chưa cấp quyền thông báo nên nhắc lịch sẽ không hiển thị")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupButtons()

        entries.addAll(repository.loadEntries())
        renderAll()

        repository.loadReminderTime()?.let { reminderTime ->
            NotificationScheduler.schedule(this, reminderTime)
        }
    }

    private fun bindViews() {
        weekContainer = findViewById(R.id.weekContainer)
        todaySummaryText = findViewById(R.id.todaySummaryText)
        clockText = findViewById(R.id.clockText)
        reminderText = findViewById(R.id.reminderText)
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.addLessonButton).setOnClickListener {
            showEntryDialog(null)
        }

        findViewById<MaterialButton>(R.id.clearAllButton).setOnClickListener {
            confirmClearAll()
        }

        findViewById<MaterialButton>(R.id.setReminderButton).setOnClickListener {
            chooseReminderTime()
        }

        findViewById<MaterialButton>(R.id.cancelReminderButton).setOnClickListener {
            repository.saveReminderTime(null)
            NotificationScheduler.cancel(this)
            renderAll()
            showToast("Đã tắt nhắc lịch")
        }
    }

    private fun renderAll() {
        renderSummary()
        renderWeekSections()
        renderReminderState()
    }

    private fun renderSummary() {
        val calendar = Calendar.getInstance()
        val todayLabel = formatTodayLabel(calendar)
        clockText.text = todayLabel

        val todayEntries = entries
            .filter { it.dayOfWeek == calendar.get(Calendar.DAY_OF_WEEK) }
            .sortedBy { it.startMinuteOfDay }

        val weekNumber = currentWeekOfYear(calendar)
        val summaryText = when {
            todayEntries.isEmpty() -> "Hôm nay chưa có môn học nào."
            else -> {
                val nextClass = todayEntries.firstOrNull { it.startMinuteOfDay >= calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) }
                    ?: todayEntries.first()
                val nextStatus = nextClass.effectiveStatus(weekNumber)
                "${todayEntries.size} môn hôm nay. Tiết gần nhất: ${nextClass.subject} lúc ${formatTime(nextClass.startMinuteOfDay)} (${statusLabel(nextStatus)})"
            }
        }

        todaySummaryText.text = summaryText
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

            weekContainer.addView(createDayCard(dayOfWeek, dayEntries, currentWeek))
        }
    }

    private fun createDayCard(dayOfWeek: Int, dayEntries: List<TimetableEntry>, weekOfYear: Int): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.day_card_spacing)
            }
            radius = resources.getDimensionPixelSize(R.dimen.day_card_corner).toFloat()
            setCardBackgroundColor(getColorCompat(R.color.day_card_background))
            strokeColor = getColorCompat(R.color.day_card_stroke)
            strokeWidth = resources.getDimensionPixelSize(R.dimen.day_card_stroke)
            cardElevation = 0f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.day_card_padding),
                resources.getDimensionPixelSize(R.dimen.day_card_padding),
                resources.getDimensionPixelSize(R.dimen.day_card_padding),
                resources.getDimensionPixelSize(R.dimen.day_card_padding),
            )
        }

        val header = TextView(this).apply {
            text = "${dayLabel(dayOfWeek)} · ${dayEntries.size} môn"
            textSize = 17f
            setTextColor(getColorCompat(android.R.color.black))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(header)

        if (dayEntries.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "Chưa có môn được thêm vào ngày này"
                textSize = 14f
                setTextColor(getColorCompat(R.color.day_card_empty_text))
                setPadding(0, dp(8), 0, 0)
            }
            content.addView(emptyText)
        } else {
            dayEntries.forEach { entry ->
                content.addView(createEntryCard(entry, weekOfYear))
            }
        }

        card.addView(content)
        return card
    }

    private fun createEntryCard(entry: TimetableEntry, weekOfYear: Int): View {
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
            getColorCompat(if (effectiveStatus == StudyStatus.ONLINE) R.color.status_online else R.color.status_offline),
        )
        statusChip.setTextColor(getColorCompat(android.R.color.black))

        if (entry.cycleEnabled) {
            cycleChip.text = if (entry.onlineOnOddWeeks) {
                "Chu kì: tuần lẻ online"
            } else {
                "Chu kì: tuần chẵn online"
            }
            cycleChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(getColorCompat(R.color.cycle_active))
        } else {
            cycleChip.text = "Chu kì: tắt"
            cycleChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(getColorCompat(R.color.cycle_inactive))
        }
        cycleChip.setTextColor(getColorCompat(android.R.color.black))

        editButton.setOnClickListener {
            showEntryDialog(entry)
        }
        deleteButton.setOnClickListener {
            confirmDelete(entry)
        }

        return card
    }

    private fun showEntryDialog(existing: TimetableEntry?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_timetable_entry, null, false)
        val subjectField = view.findViewById<TextInputEditText>(R.id.subjectField)
        val roomField = view.findViewById<TextInputEditText>(R.id.roomField)
        val dayField = view.findViewById<MaterialAutoCompleteTextView>(R.id.dayField)
        val startTimeField = view.findViewById<TextInputEditText>(R.id.startTimeField)
        val endTimeField = view.findViewById<TextInputEditText>(R.id.endTimeField)
        val statusField = view.findViewById<MaterialAutoCompleteTextView>(R.id.statusField)
        val cycleSwitch = view.findViewById<MaterialSwitch>(R.id.cycleSwitch)
        val cycleWeekField = view.findViewById<MaterialAutoCompleteTextView>(R.id.cycleWeekField)
        val noteField = view.findViewById<TextInputEditText>(R.id.noteField)

        val dayOptions = orderedWeekDays().map(::dayLabel)
        val statusOptions = listOf("Online", "Offline")
        val cycleOptions = listOf("Tuần lẻ", "Tuần chẵn")

        dayField.setAdapter(createDropdownAdapter(dayOptions))
        statusField.setAdapter(createDropdownAdapter(statusOptions))
        cycleWeekField.setAdapter(createDropdownAdapter(cycleOptions))

        if (existing != null) {
            subjectField.setText(existing.subject)
            roomField.setText(existing.room)
            dayField.setText(dayLabel(existing.dayOfWeek), false)
            startTimeField.setText(formatTime(existing.startMinuteOfDay))
            endTimeField.setText(formatTime(existing.endMinuteOfDay))
            statusField.setText(statusLabel(existing.baseStatus), false)
            cycleSwitch.isChecked = existing.cycleEnabled
            cycleWeekField.setText(if (existing.onlineOnOddWeeks) "Tuần lẻ" else "Tuần chẵn", false)
            noteField.setText(existing.note)
        } else {
            dayField.setText(dayOptions.first(), false)
            statusField.setText(statusOptions.first(), false)
            cycleWeekField.setText(cycleOptions.first(), false)
        }

        val title = if (existing == null) "Thêm môn học" else "Chỉnh sửa môn học"
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
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
                val cycleText = cycleWeekField.text?.toString()?.trim().orEmpty()

                val dayOfWeek = orderedWeekDays().firstOrNull { dayLabel(it) == dayLabelValue }
                val startMinute = parseTimeToMinuteOfDay(startTimeText)
                val endMinute = parseTimeToMinuteOfDay(endTimeText)
                val baseStatus = if (statusText == "Offline") StudyStatus.OFFLINE else StudyStatus.ONLINE
                val onlineOnOddWeeks = cycleText != "Tuần chẵn"

                if (subject.isBlank()) {
                    subjectField.error = "Nhập tên môn học"
                    return@setOnClickListener
                }

                if (room.isBlank()) {
                    roomField.error = "Nhập phòng học"
                    return@setOnClickListener
                }

                if (dayOfWeek == null) {
                    showToast("Chọn một ngày trong tuần")
                    return@setOnClickListener
                }

                if (startMinute == null || endMinute == null) {
                    showToast("Giờ phải theo định dạng HH:mm, ví dụ 07:30")
                    return@setOnClickListener
                }

                if (endMinute <= startMinute) {
                    showToast("Giờ kết thúc phải sau giờ bắt đầu")
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
                    onlineOnOddWeeks = onlineOnOddWeeks,
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
            this,
            { _, hourOfDay, minute ->
                val selectedTime = ReminderTime(hourOfDay, minute)
                requestNotificationPermissionIfNeeded(selectedTime)
            },
            reminder.hour,
            reminder.minute,
            true,
        ).show()
    }

    private fun requestNotificationPermissionIfNeeded(reminderTime: ReminderTime) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingReminderTime = reminderTime
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            saveReminderTime(reminderTime)
        }
    }

    private fun saveReminderTime(reminderTime: ReminderTime) {
        repository.saveReminderTime(reminderTime)
        NotificationScheduler.schedule(this, reminderTime)
        pendingReminderTime = null
        renderAll()
        showToast("Đã đặt nhắc lịch lúc ${formatReminderTime(reminderTime)}")
    }

    private fun confirmDelete(entry: TimetableEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Xóa môn học")
            .setMessage("Xóa ${entry.subject} khỏi ${dayLabel(entry.dayOfWeek)}?")
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

        MaterialAlertDialogBuilder(this)
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
                .thenBy { it.subject.lowercase() },
        )

        repository.saveEntries(entries)
        renderAll()
    }

    private fun createDropdownAdapter(options: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
    }

    private fun statusLabel(status: StudyStatus): String {
        return if (status == StudyStatus.ONLINE) "Online" else "Offline"
    }

    private fun formatReminderTime(reminderTime: ReminderTime): String {
        return String.format("%02d:%02d", reminderTime.hour, reminderTime.minute)
    }

    private fun getColorCompat(colorResId: Int): Int {
        return androidx.core.content.ContextCompat.getColor(this, colorResId)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
