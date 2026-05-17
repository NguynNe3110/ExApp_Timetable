package com.uzuu.timetable

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ClassSearchFragment : Fragment(R.layout.fragment_class_search) {
    private lateinit var firebaseRepository: FirebaseRepository
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchButton: MaterialButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var noResultsText: TextView
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var adapter: ClassSearchAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseRepository = FirebaseRepository()
        searchInput = view.findViewById(R.id.searchInput)
        searchButton = view.findViewById(R.id.searchButton)
        loadingProgress = view.findViewById(R.id.loadingProgress)
        noResultsText = view.findViewById(R.id.noResultsText)
        resultsRecyclerView = view.findViewById(R.id.searchResultsRecyclerView)

        adapter = ClassSearchAdapter { classId ->
            navigateToClassTimetable(classId)
        }
        resultsRecyclerView.adapter = adapter

        searchButton.setOnClickListener {
            performSearch()
        }
    }

    private fun performSearch() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(requireContext(), "Nhập tên lớp học để tìm kiếm", Toast.LENGTH_SHORT).show()
            return
        }

        loadingProgress.visibility = View.VISIBLE
        noResultsText.visibility = View.GONE
        resultsRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = firebaseRepository.searchClasses(query)
                loadingProgress.visibility = View.GONE

                if (results.isEmpty()) {
                    noResultsText.visibility = View.VISIBLE
                } else {
                    resultsRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(results)
                }
            } catch (e: Exception) {
                loadingProgress.visibility = View.GONE
                noResultsText.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Lỗi khi tìm kiếm: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToClassTimetable(classId: String) {
        findNavController().navigate(
            R.id.class_timetable_fragment,
            Bundle().apply { putString("classId", classId) }
        )
    }
}

class ClassSearchAdapter(
    private val onClassClick: (String) -> Unit
) : androidx.recyclerview.widget.ListAdapter<ClassTimetable, ClassSearchViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<ClassTimetable>() {
        override fun areItemsTheSame(oldItem: ClassTimetable, newItem: ClassTimetable): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClassTimetable, newItem: ClassTimetable): Boolean {
            return oldItem == newItem
        }
    }
) {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ClassSearchViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_class_search_result, parent, false)
        return ClassSearchViewHolder(view, onClassClick)
    }

    override fun onBindViewHolder(holder: ClassSearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ClassSearchViewHolder(
    itemView: View,
    private val onClassClick: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
    private val classNameText: TextView = itemView.findViewById(R.id.classNameText)
    private val subjectsCountText: TextView = itemView.findViewById(R.id.subjectsCountText)

    fun bind(classTimetable: ClassTimetable) {
        classNameText.text = classTimetable.className
        subjectsCountText.text = "${classTimetable.entries.size} môn học"
        itemView.setOnClickListener {
            onClassClick(classTimetable.id)
        }
    }
}
