package com.uzuu.timetable

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val ADMIN_CODE = "1234" // TODO: Move this to secure location like Firebase Remote Config
    private lateinit var firebaseRepository: FirebaseRepository

    private lateinit var adminCodeInput: TextInputEditText
    private lateinit var unlockAdminButton: MaterialButton
    private lateinit var adminPanelCard: MaterialCardView
    private lateinit var proposalsCard: MaterialCardView
    private lateinit var proposalsRecyclerView: RecyclerView
    private lateinit var proposalAdapter: ProposalAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        firebaseRepository = FirebaseRepository()

        adminCodeInput = view.findViewById(R.id.adminCodeInput)
        unlockAdminButton = view.findViewById(R.id.unlockAdminButton)
        adminPanelCard = view.findViewById(R.id.adminPanelCard)
        proposalsCard = view.findViewById(R.id.proposalsCard)
        proposalsRecyclerView = view.findViewById(R.id.proposalsRecyclerView)

        unlockAdminButton.setOnClickListener {
            validateAdminCode()
        }

        proposalAdapter = ProposalAdapter(
            onApprove = { proposalId -> approveProposal(proposalId) },
            onReject = { proposalId -> rejectProposal(proposalId) }
        )
        proposalsRecyclerView.adapter = proposalAdapter
    }

    private fun validateAdminCode() {
        val enteredCode = adminCodeInput.text?.toString()?.trim().orEmpty()

        if (enteredCode.isBlank()) {
            Toast.makeText(requireContext(), "Nhập mã admin", Toast.LENGTH_SHORT).show()
            return
        }

        if (enteredCode == ADMIN_CODE) {
            adminPanelCard.visibility = View.GONE
            proposalsCard.visibility = View.VISIBLE
            loadProposals()
            Toast.makeText(requireContext(), "Đã mở khóa chế độ quản lý", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Mã admin không chính xác", Toast.LENGTH_SHORT).show()
            adminCodeInput.error = "Mã không đúng"
        }
    }

    private fun loadProposals() {
        lifecycleScope.launch {
            try {
                val proposals = firebaseRepository.getPendingProposals()
                proposalAdapter.submitList(proposals)

                if (proposals.isEmpty()) {
                    proposalsRecyclerView.visibility = View.GONE
                } else {
                    proposalsRecyclerView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi khi tải đề xuất: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun approveProposal(proposalId: String) {
        lifecycleScope.launch {
            try {
                val success = firebaseRepository.approveProposal(proposalId, "admin_${System.currentTimeMillis()}")
                if (success) {
                    Toast.makeText(requireContext(), "Đã phê duyệt đề xuất", Toast.LENGTH_SHORT).show()
                    loadProposals()
                } else {
                    Toast.makeText(requireContext(), "Lỗi khi phê duyệt", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rejectProposal(proposalId: String) {
        lifecycleScope.launch {
            try {
                val success = firebaseRepository.rejectProposal(proposalId)
                if (success) {
                    Toast.makeText(requireContext(), "Đã từ chối đề xuất", Toast.LENGTH_SHORT).show()
                    loadProposals()
                } else {
                    Toast.makeText(requireContext(), "Lỗi khi từ chối", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class ProposalAdapter(
    private val onApprove: (String) -> Unit,
    private val onReject: (String) -> Unit
) : androidx.recyclerview.widget.ListAdapter<TimetableProposal, ProposalViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<TimetableProposal>() {
        override fun areItemsTheSame(oldItem: TimetableProposal, newItem: TimetableProposal): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimetableProposal, newItem: TimetableProposal): Boolean {
            return oldItem == newItem
        }
    }
) {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ProposalViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_proposal, parent, false)
        return ProposalViewHolder(view, onApprove, onReject)
    }

    override fun onBindViewHolder(holder: ProposalViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ProposalViewHolder(
    itemView: View,
    private val onApprove: (String) -> Unit,
    private val onReject: (String) -> Unit
) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
    private val classNameText: android.widget.TextView = itemView.findViewById(R.id.proposalClassNameText)
    private val authorText: android.widget.TextView = itemView.findViewById(R.id.proposalAuthorText)
    private val descriptionText: android.widget.TextView = itemView.findViewById(R.id.proposalDescriptionText)
    private val approveButton: MaterialButton = itemView.findViewById(R.id.approveButton)
    private val rejectButton: MaterialButton = itemView.findViewById(R.id.rejectButton)

    fun bind(proposal: TimetableProposal) {
        classNameText.text = proposal.className
        authorText.text = "Đề xuất bởi: ${proposal.proposedBy}"
        descriptionText.text = if (proposal.description.isBlank()) {
            "Không có mô tả"
        } else {
            proposal.description
        }

        approveButton.setOnClickListener {
            onApprove(proposal.id)
        }

        rejectButton.setOnClickListener {
            onReject(proposal.id)
        }
    }
}
