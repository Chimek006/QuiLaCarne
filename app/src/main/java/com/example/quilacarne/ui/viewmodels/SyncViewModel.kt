package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.TokenManager
import com.example.quilacarne.data.repository.sync.SyncRepository
import com.example.quilacarne.ui.state.SyncUiState
import com.example.quilacarne.ui.state.SyncUiStateError
import com.example.quilacarne.ui.state.SyncUiStateIdle
import com.example.quilacarne.ui.state.SyncUiStateLoading
import com.example.quilacarne.ui.state.SyncUiStateOfflineAvailable
import com.example.quilacarne.ui.state.SyncUiStateSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val syncRepository = SyncRepository(db, application.applicationContext)
    private val tokenManager = TokenManager(application.applicationContext)

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiStateIdle)
    val uiState: StateFlow<SyncUiState> = _uiState

    fun startSync() {
        viewModelScope.launch {
            _uiState.value = SyncUiStateLoading("Synchronizacja danych...", 0.10f)

            syncRepository.syncAllLocalData(clearBeforeSync = false).fold(
                onSuccess = {
                    tokenManager.setBootstrapped(true)
                    _uiState.value = SyncUiStateSuccess
                },
                onFailure = { error ->
                    if (tokenManager.isBootstrapped()) {
                        _uiState.value = SyncUiStateOfflineAvailable
                    } else {
                        _uiState.value = SyncUiStateError("Blad synchronizacji: ${error.message}")
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
