package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val syncRepository = SyncRepository(db, application.applicationContext)
    private val tokenManager = TokenManager(application.applicationContext)

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState

    fun startSync() {
        viewModelScope.launch {
            _uiState.value = SyncUiState.Loading("Synchronizacja danych...", 0.10f)

            syncRepository.syncAllLocalData(clearBeforeSync = true).fold(
                onSuccess = {
                    tokenManager.setBootstrapped(true)
                    _uiState.value = SyncUiState.Success
                },
                onFailure = { error ->
                    if (tokenManager.isBootstrapped()) {
                        _uiState.value = SyncUiState.OfflineAvailable
                    } else {
                        _uiState.value = SyncUiState.Error("Błąd synchronizacji: ${error.message}")
                    }
                }
            )
        }
    }

    fun syncTables() {
        viewModelScope.launch {
            syncRepository.syncTables()
        }
    }
}

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data class Loading(val message: String, val progress: Float) : SyncUiState
    data object Success : SyncUiState
    data object OfflineAvailable : SyncUiState
    data class Error(val message: String) : SyncUiState
}
