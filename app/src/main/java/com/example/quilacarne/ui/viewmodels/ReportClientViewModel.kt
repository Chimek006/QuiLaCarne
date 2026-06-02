package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.repository.sync.SyncRepository
import com.example.quilacarne.ui.state.ReportClientUiState
import com.example.quilacarne.ui.state.ReportClientUiStateError
import com.example.quilacarne.ui.state.ReportClientUiStateIdle
import com.example.quilacarne.ui.state.ReportClientUiStateSending
import com.example.quilacarne.ui.state.ReportClientUiStateSent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ReportClientViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val syncRepository = SyncRepository(db, application.applicationContext)

    private val _uiState = MutableStateFlow<ReportClientUiState>(ReportClientUiStateIdle)
    val uiState: StateFlow<ReportClientUiState> = _uiState

    fun submitReport(
        tableId: UUID,
        reason: String,
        description: String
    ) {
        viewModelScope.launch {
            _uiState.value = ReportClientUiStateSending

            val payloadReason = buildReason(reason, description)

            syncRepository.createClientReportForTable(tableId, payloadReason)
                .onSuccess {
                    _uiState.value = ReportClientUiStateSent
                }
                .onFailure { error ->
                    _uiState.value = ReportClientUiStateError(
                        error.message ?: "Nie udalo sie wyslac zgloszenia"
                    )
                }
        }
    }

    fun consumeError() {
        if (_uiState.value is ReportClientUiStateError) {
            _uiState.value = ReportClientUiStateIdle
        }
    }

    private fun buildReason(reason: String, description: String): String {
        return listOf(reason.trim(), description.trim())
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .take(500)
    }
}
