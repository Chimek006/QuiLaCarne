package com.example.quilacarne.data.repository.sync

import java.util.UUID

internal class TableStatusManager(
    private val gateway: TableStatusGateway
) {
    suspend fun changeStatus(
        tableId: UUID,
        tableToken: String,
        statusToken: String
    ): Result<Unit> = runCatching {
        val change = TableStatusChange(
            tableId = tableId,
            tableToken = tableToken.trim(),
            statusToken = TableStatusChangePlan.normalizeStatusToken(statusToken)
        )

        require(change.tableToken.isNotBlank()) { "Brak tokena API dla stolika $tableId" }
        gateway.changeStatus(change)
    }
}
