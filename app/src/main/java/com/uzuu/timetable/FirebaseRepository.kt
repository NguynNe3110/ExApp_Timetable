package com.uzuu.timetable

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseRepository {
    private companion object {
        const val DEBUG_TAG = "DEBUG"
    }

    private val database = FirebaseDatabase.getInstance()
    private val classesRef = database.getReference("classes")
    private val proposalsRef = database.getReference("proposals")

    init {
        // Giữ data luôn được sync — không cần thoát app mới thấy thay đổi
        classesRef.keepSynced(true)
        proposalsRef.keepSynced(true)
    }

    // Classes operations
    suspend fun getClasses(): List<ClassTimetable> = suspendCancellableCoroutine { continuation ->
        Log.d(DEBUG_TAG, "getClasses: start")
        classesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(DEBUG_TAG, "getClasses: snapshot children=${snapshot.childrenCount}")
                val classes = mutableListOf<ClassTimetable>()
                snapshot.children.forEach { child ->
                    try {
                        val classData = child.value as? Map<*, *> ?: return@forEach
                        val entries = (classData["entries"] as? List<*>)?.mapNotNull { entryData ->
                            if (entryData is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                mapToTimetableEntry(entryData as Map<String, Any?>)
                            } else null
                        } ?: emptyList()

                        @Suppress("UNCHECKED_CAST")
                        classes.add(
                            ClassTimetable(
                                id = classData["id"] as? String ?: child.key ?: "",
                                className = classData["className"] as? String ?: "",
                                entries = entries,
                                createdBy = classData["createdBy"] as? String ?: "",
                                lastModified = (classData["lastModified"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                version = (classData["version"] as? Number)?.toInt() ?: 1
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("FirebaseRepository", "Error parsing class data", e)
                    }
                }
                continuation.resume(classes)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(DEBUG_TAG, "getClasses: failed to read classes: ${error.message}", error.toException())
                continuation.resume(emptyList())
            }
        })
    }

    suspend fun searchClasses(query: String): List<ClassTimetable> {
        val allClasses = getClasses()
        return allClasses.filter { it.className.contains(query, ignoreCase = true) }
    }

    suspend fun getClassById(classId: String): ClassTimetable? = suspendCancellableCoroutine { continuation ->
        Log.d(DEBUG_TAG, "getClassById: start classId=$classId")
        classesRef.child(classId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val classData = snapshot.value as? Map<*, *>
                    if (classData != null) {
                        val entries = (classData["entries"] as? List<*>)?.mapNotNull { entryData ->
                            if (entryData is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                mapToTimetableEntry(entryData as Map<String, Any?>)
                            } else null
                        } ?: emptyList()

                        @Suppress("UNCHECKED_CAST")
                        val result = ClassTimetable(
                            id = classData["id"] as? String ?: classId,
                            className = classData["className"] as? String ?: "",
                            entries = entries,
                            createdBy = classData["createdBy"] as? String ?: "",
                            lastModified = (classData["lastModified"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            version = (classData["version"] as? Number)?.toInt() ?: 1
                        )
                        continuation.resume(result)
                    } else {
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseRepository", "Error parsing class data", e)
                    continuation.resume(null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(DEBUG_TAG, "getClassById: failed classId=$classId message=${error.message}", error.toException())
                continuation.resume(null)
            }
        })
    }

    suspend fun saveClass(classTimetable: ClassTimetable): Boolean = suspendCancellableCoroutine { continuation ->
        val classId = classTimetable.id.ifEmpty {
            classesRef.push().key ?: return@suspendCancellableCoroutine continuation.resume(false)
        }
        val updatedClass = classTimetable.copy(
            id = classId,
            lastModified = System.currentTimeMillis(),
            version = (classTimetable.version + 1)
        )

        Log.d(DEBUG_TAG, "saveClass: start classId=$classId className=${updatedClass.className} entries=${updatedClass.entries.size}")
        classesRef.child(classId).setValue(updatedClass.toMap())
            .addOnSuccessListener {
                Log.d(DEBUG_TAG, "saveClass: success classId=$classId")
                continuation.resume(true)
            }
            .addOnFailureListener { e ->
                Log.d(DEBUG_TAG, "saveClass: failed classId=$classId message=${e.message}", e)
                continuation.resume(false)
            }
    }

    // Proposals operations
    suspend fun getProposals(): List<TimetableProposal> = suspendCancellableCoroutine { continuation ->
        Log.d(DEBUG_TAG, "getProposals: start")
        proposalsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(DEBUG_TAG, "getProposals: snapshot children=${snapshot.childrenCount}")
                val proposals = mutableListOf<TimetableProposal>()
                snapshot.children.forEach { child ->
                    try {
                        val proposalData = child.value as? Map<*, *> ?: return@forEach
                        val entries = (proposalData["proposedEntries"] as? List<*>)?.mapNotNull { entryData ->
                            if (entryData is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                mapToTimetableEntry(entryData as Map<String, Any?>)
                            } else null
                        } ?: emptyList()

                        @Suppress("UNCHECKED_CAST")
                        proposals.add(
                            TimetableProposal(
                                id = proposalData["id"] as? String ?: child.key ?: "",
                                classId = proposalData["classId"] as? String ?: "",
                                className = proposalData["className"] as? String ?: "",
                                proposedBy = proposalData["proposedBy"] as? String ?: "",
                                proposedEntries = entries,
                                description = proposalData["description"] as? String ?: "",
                                status = try {
                                    ProposalStatus.valueOf(proposalData["status"] as? String ?: ProposalStatus.PENDING.name)
                                } catch (e: Exception) {
                                    ProposalStatus.PENDING
                                },
                                createdAt = (proposalData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                approvedBy = proposalData["approvedBy"] as? String,
                                approvedAt = (proposalData["approvedAt"] as? Number)?.toLong()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("FirebaseRepository", "Error parsing proposal data", e)
                    }
                }
                continuation.resume(proposals)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(DEBUG_TAG, "getProposals: failed: ${error.message}", error.toException())
                continuation.resume(emptyList())
            }
        })
    }

    suspend fun getPendingProposals(): List<TimetableProposal> {
        val allProposals = getProposals()
        return allProposals.filter { it.status == ProposalStatus.PENDING }
    }

    suspend fun saveProposal(proposal: TimetableProposal): Boolean = suspendCancellableCoroutine { continuation ->
        val proposalId = proposal.id.ifEmpty {
            proposalsRef.push().key ?: return@suspendCancellableCoroutine continuation.resume(false)
        }
        val updatedProposal = proposal.copy(id = proposalId)

        proposalsRef.child(proposalId).setValue(updatedProposal.toMap())
            .addOnSuccessListener { continuation.resume(true) }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Failed to save proposal: ${e.message}")
                continuation.resume(false)
            }
    }

    suspend fun approveProposal(proposalId: String, adminId: String): Boolean = suspendCancellableCoroutine { continuation ->
        proposalsRef.child(proposalId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val proposalData = snapshot.value as? Map<*, *>
                if (proposalData == null) {
                    Log.d(DEBUG_TAG, "approveProposal: not found proposalId=$proposalId")
                    continuation.resume(false)
                    return
                }

                val classId = proposalData["classId"] as? String ?: ""
                val className = proposalData["className"] as? String ?: ""
                val entries = (proposalData["proposedEntries"] as? List<*>)?.mapNotNull { entryData ->
                    if (entryData is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        mapToTimetableEntry(entryData as Map<String, Any?>)
                    } else null
                } ?: emptyList()

                if (classId.isBlank()) {
                    Log.d(DEBUG_TAG, "approveProposal: classId is blank proposalId=$proposalId")
                    continuation.resume(false)
                    return
                }

                val updatedClass = ClassTimetable(
                    id = classId,
                    className = className,
                    entries = entries,
                    createdBy = proposalData["proposedBy"] as? String ?: "",
                    lastModified = System.currentTimeMillis(),
                    version = ((proposalData["version"] as? Number)?.toInt() ?: 1) + 1,
                )

                classesRef.child(classId).setValue(updatedClass.toMap())
                    .addOnSuccessListener {
                        proposalsRef.child(proposalId).updateChildren(
                            mapOf(
                                "status" to ProposalStatus.APPROVED.name,
                                "approvedBy" to adminId,
                                "approvedAt" to System.currentTimeMillis(),
                            )
                        ).addOnSuccessListener {
                            Log.d(DEBUG_TAG, "approveProposal: success proposalId=$proposalId classId=$classId")
                            continuation.resume(true)
                        }.addOnFailureListener { e ->
                            Log.d(DEBUG_TAG, "approveProposal: class updated but status update failed: ${e.message}", e)
                            continuation.resume(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.d(DEBUG_TAG, "approveProposal: failed to update class: ${e.message}", e)
                        continuation.resume(false)
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.d(DEBUG_TAG, "approveProposal: cancelled proposalId=$proposalId: ${error.message}", error.toException())
                continuation.resume(false)
            }
        })
    }

    suspend fun rejectProposal(proposalId: String): Boolean = suspendCancellableCoroutine { continuation ->
        proposalsRef.child(proposalId).child("status").setValue(ProposalStatus.REJECTED.name)
            .addOnSuccessListener { continuation.resume(true) }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Failed to reject proposal: ${e.message}")
                continuation.resume(false)
            }
    }

    suspend fun getProposalsByClass(classId: String): List<TimetableProposal> {
        val allProposals = getProposals()
        return allProposals.filter { it.classId == classId }
    }
}