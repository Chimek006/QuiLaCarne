package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ReportClientViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val syncRepository = SyncRepository(db, application.applicationContext)

    private val _uiState = MutableStateFlow<ReportClientUiState>(ReportClientUiState.Idle)
    val uiState: StateFlow<ReportClientUiState> = _uiState

    fun submitReport(
        tableId: UUID,
        reason: String,
        description: String
    ) {
        viewModelScope.launch {
            _uiState.value = ReportClientUiState.Sending

            val payloadReason = buildReason(reason, description)

            syncRepository.createClientReportForTable(tableId, payloadReason)
                .onSuccess {
                    _uiState.value = ReportClientUiState.Sent
                }
                .onFailure { error ->
                    _uiState.value = ReportClientUiState.Error(
                        error.message ?: "Nie udalo sie wyslac zgloszenia"
                    )
                }
        }
    }

    fun consumeError() {
        if (_uiState.value is ReportClientUiState.Error) {
            _uiState.value = ReportClientUiState.Idle
        }
    }

    private fun buildReason(reason: String, description: String): String {
        return listOf(reason.trim(), description.trim())
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .take(500)
    }
}

sealed interface ReportClientUiState {
    data object Idle : ReportClientUiState
    data object Sending : ReportClientUiState
    data object Sent : ReportClientUiState
    data class Error(val message: String) : ReportClientUiState
}
