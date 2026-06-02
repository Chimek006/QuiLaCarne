package com.example.quilacarne.data.sync.coordinator

data class OperationalSyncActions(
    val syncAllLocalData: suspend () -> Result<Unit>,
    val syncOperationalData: suspend (String) -> Result<Unit>,
    val syncMenu: suspend (String) -> Result<Unit>
)
