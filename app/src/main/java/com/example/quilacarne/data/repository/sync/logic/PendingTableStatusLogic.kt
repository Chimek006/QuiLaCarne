package com.example.quilacarne.data.repository.sync.logic

import com.example.quilacarne.data.repository.sync.PendingRequestRepository

internal object PendingTableStatusLogic {
    fun statusTokenForAction(action: String?): String? {
        return when (action) {
            PendingRequestRepository.ACTION_MARK_AVAILABLE -> "AVAILABLE"
            PendingRequestRepository.ACTION_MARK_CLEANING -> "CLEANING"
            PendingRequestRepository.ACTION_MARK_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
            else -> null
        }
    }
}
