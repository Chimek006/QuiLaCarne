package com.example.quilacarne.data.repository.sync

internal class QueuedTableStatusGateway(
    private val pendingRequestRepository: PendingRequestRepository
) : TableStatusGateway {
    override suspend fun changeStatus(change: TableStatusChange) {
        pendingRequestRepository.enqueue(
            method = PendingRequestRepository.METHOD_PATCH,
            path = TableStatusChangePlan.endpointPath(change),
            entityType = PendingRequestRepository.ENTITY_TABLE,
            entityLocalId = change.tableId.toString(),
            optimisticAction = TableStatusChangePlan.optimisticAction(change)
        )
    }
}
