package com.uzuu.timetable

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.Calendar

class ClassTimetableFragment : Fragment(R.layout.fragment_class_timetable) {
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var timetableRepository: TimetableRepository

    private val draftEntries = mutableListOf<TimetableEntry>()
    private var currentClass: ClassTimetable? = null

    private lateinit var classNameText: TextView
    private lateinit var weekContainer: LinearLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var submitButton: MaterialButton
    private lateinit var importButton: MaterialButton

    private val classId: String
        get() = requireArguments().getString("classId")
            ?: throw IllegalStateException("classId argument is required")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseRepository = FirebaseRepository()
        timetableRepository = TimetableRepository(requireContext())

        classNameText = view.findViewById(R.id.classNameText)
        weekContainer = view.findViewById(R.id.weekContainer)
        loadingProgress = view.findViewById(R.id.loadingProgress)
        submitButton = view.findViewById(R.id.submitButton)
        importButton = view.findViewById(R.id.importButton)

        submitButton.setOnClickListener { showSubmitProposalDialog() }
        importButton.setOnClickListener { importToMySchedule() }

        loadClassTimetable()
    }

    private fun loadClassTimetable() {
        loadingProgress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val classTimetable = firebaseRepository.getClassById(classId)
                loadingProgress.visibility = View.GONE

                if (classTimetable != null) {
                    currentClass = classTimetable
                    draftEntries.clear()
                    draftEntries.addAll(classTimetable.entries)
                    classNameText.text = classTimetable.className
                    renderTimetable(draftEntries)
                } else {
                    Toast.makeText(requireContext(), "Không tìm thấy lớp học", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderTimetable(entries: List<TimetableEntry>) {
        weekContainer.removeAllViews()
        val currentWeek = currentWeekOfYear()

        orderedWeekDays().forEach { dayOfWeek ->
            val dayEntries = entries
                .filter { it.dayOfWeek == dayOfWeek }
                .sortedWith(compareBy<TimetableEntry> { it.startMinuteOfDay }.thenBy { it.subject.lowercase() })

            weekContainer.addView(createDayCard(dayOfWeek, dayEntries, currentWeek))
        }
    }

    private fun createDayCard(dayOfWeek: Int, dayEntries: List<TimetableEntry>, weekOfYear: Int): View {
        val context = requireContext()
        val card = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(12)
            }
            radius = dp(12).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.day_card_background))
            strokeColor = ContextCompat.getColor(context, R.color.day_card_stroke)
            strokeWidth = dp(1)
            cardElevation = 0f
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val header = TextView(context).apply {
            text = "${dayLabel(dayOfWeek)} · ${dayEntries.size} môn"
            textSize = 17f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        content.addView(header)

        if (dayEntries.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = "Chưa có môn được thêm vào ngày này"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.day_card_empty_text))
                setPadding(0, dp(8), 0, 0)
            }
            content.addView(emptyText)
        } else {
            dayEntries.forEachIndexed { index, entry ->
                content.addView(createEntryCard(entry, index, weekOfYear))
            }
        }

        card.addView(content)
        return card
    }

    private fun createEntryCard(entry: TimetableEntry, index: Int, weekOfYear: Int): View {
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
        statusChip.text = if (effectiveStatus == StudyStatus.ONLINE) "Online" else "Offline"
        statusChip.chipBackgroundColor = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (effectiveStatus == StudyStatus.ONLINE) R.color.status_online else R.color.status_offline)
        )

        if (entry.cycleEnabled) {
            cycleChip.text = "Chu kì: N=${entry.repeatGapWeeks}"
            cycleChip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.cycle_active))
        } else {
            cycleChip.text = "Chu kì: tắt"
            cycleChip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.cycle_inactive))
        }

        editButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE

        editButton.setOnClickListener {
            showEntryDialog(entry, index)
        }
        deleteButton.setOnClickListener {
            confirmDeleteEntry(index)
        }

        return card
    }

    private fun showEntryDialog(existing: TimetableEntry? = null, editingIndex: Int? = null) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_timetable_entry, null, false)
        val subjectField = dialogView.findViewById<TextInputEditText>(R.id.subjectField)
        val roomField = dialogView.findViewById<TextInputEditText>(R.id.roomField)
        val dayField = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dayField)
        val startTimeField = dialogView.findViewById<TextInputEditText>(R.id.startTimeField)
        val endTimeField = dialogView.findViewById<TextInputEditText>(R.id.endTimeField)
        val statusField = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.statusField)
        val cycleSwitch = dialogView.findViewById<MaterialSwitch>(R.id.cycleSwitch)
        val repeatGapField = dialogView.findViewById<TextInputEditText>(R.id.repeatGapField)
        val noteField = dialogView.findViewById<TextInputEditText>(R.id.noteField)

        val dayOptions = orderedWeekDays().map(::dayLabel)
        val statusOptions = listOf("Online", "Offline")
        dayField.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, dayOptions))
        statusField.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, statusOptions))
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
            .setTitle(if (existing == null) "Sửa tiết học" else "Sửa tiết học")
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
                renderTimetable(draftEntries)
            }
            .show()
    }

    private fun confirmDeleteEntry(index: Int) {
        val entry = draftEntries.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa tiết học")
            .setMessage("Xóa ${entry.subject} khỏi bản nháp?")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                if (index in draftEntries.indices) {
                    draftEntries.removeAt(index)
                    renderTimetable(draftEntries)
                }
            }
            .show()
    }

    private fun showSubmitProposalDialog() {
        if (draftEntries.isEmpty()) {
            Toast.makeText(requireContext(), "Bản nháp đang trống", Toast.LENGTH_SHORT).show()
            return
        }

        val descriptionInput = TextInputEditText(requireContext()).apply {
            hint = "Mô tả đề xuất (tùy chọn)"
            setText("Đề xuất cập nhật thời khóa biểu lớp")
            setSelection(text?.length ?: 0)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gửi bản nháp lên admin")
            .setMessage("Toàn bộ chỉnh sửa sửa/xóa hiện tại sẽ được gửi thành một đề xuất để admin phê duyệt.")
            .setView(descriptionInput)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Gửi") { _, _ ->
                val description = descriptionInput.text?.toString()?.trim().orEmpty()
                submitProposal(description)
            }
            .show()
    }

    private fun submitProposal(description: String) {
        lifecycleScope.launch {
            try {
                val currentClass = currentClass ?: firebaseRepository.getClassById(classId) ?: return@launch
                val proposal = TimetableProposal(
                    classId = classId,
                    className = currentClass.className,
                    proposedBy = "user_${System.currentTimeMillis()}",
                    proposedEntries = draftEntries.sortedWith(compareBy<TimetableEntry> { it.dayOfWeek }.thenBy { it.startMinuteOfDay }),
                    description = description,
                    status = ProposalStatus.PENDING,
                )

                val success = firebaseRepository.saveProposal(proposal)
                if (success) {
                    Toast.makeText(requireContext(), "Đã gửi đề xuất bản nháp cho admin", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Lỗi khi gửi đề xuất", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importToMySchedule() {
        lifecycleScope.launch {
            try {
                val current = currentClass ?: firebaseRepository.getClassById(classId) ?: return@launch
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Ghi đè thời khóa biểu")
                    .setMessage("Nhập lịch từ lớp ${current.className} sẽ xóa toàn bộ lịch hiện tại của bạn và thay bằng lịch của lớp này. Bạn có muốn tiếp tục không?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Ghi đè") { _, _ ->
                        val entries = current.entries.map { it.copy(id = System.currentTimeMillis() + kotlin.random.Random.nextLong()) }
                        timetableRepository.saveEntries(entries)
                        Toast.makeText(requireContext(), "Đã ghi đè toàn bộ thời khóa biểu của bạn", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
