package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.remote.api.TableService
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.example.quilacarne.data.remote.dto.response.DictionaryData
import com.example.quilacarne.data.remote.dto.response.StatusDictionaryDto
import com.example.quilacarne.data.remote.dto.response.TableSyncResponse
import com.example.quilacarne.data.remote.dto.response.TablesData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.util.UUID

class TableStatusBridgeTest {
    @Test
    fun managerDelegatesNormalizedChangeToGateway() = runTest {
        val tableId = UUID.randomUUID()
        val gateway = RecordingGateway()
        val manager = TableStatusManager(gateway)

        val result = manager.changeStatus(
            tableId = tableId,
            tableToken = " remote-table-token ",
            statusToken = " cleaning "
        )

        assertTrue(result.isSuccess)
        assertEquals(
            TableStatusChange(
                tableId = tableId,
                tableToken = "remote-table-token",
                statusToken = "CLEANING"
            ),
            gateway.change
        )
    }

    @Test
    fun changePlanMapsQueuedPathAndOptimisticAction() {
        val change = TableStatusChange(
            tableId = UUID.randomUUID(),
            tableToken = "table-token",
            statusToken = "OUT_OF_SERVICE"
        )

        assertEquals("tables/table-token/out-of-services", TableStatusChangePlan.endpointPath(change))
        assertEquals(
            PendingRequestRepository.ACTION_MARK_OUT_OF_SERVICE,
            TableStatusChangePlan.optimisticAction(change)
        )
    }

    @Test
    fun remoteGatewayCallsMatchingRetrofitEndpoint() = runTest {
        val tableService = RecordingTableService()
        val gateway = RemoteTableStatusGateway(tableService)
        val tableId = UUID.randomUUID()

        gateway.changeStatus(TableStatusChange(tableId, "table-token-1", "AVAILABLE"))
        gateway.changeStatus(TableStatusChange(tableId, "table-token-2", "CLEANING"))
        gateway.changeStatus(TableStatusChange(tableId, "table-token-3", "OUT_OF_SERVICE"))

        assertEquals(
            listOf(
                "available:table-token-1",
                "cleaning:table-token-2",
                "out-of-service:table-token-3"
            ),
            tableService.calls
        )
    }

    private class RecordingGateway : TableStatusGateway {
        var change: TableStatusChange? = null

        override suspend fun changeStatus(change: TableStatusChange) {
            this.change = change
        }
    }

    private class RecordingTableService : TableService {
        val calls = mutableListOf<String>()

        override suspend fun getTables(
            startTime: String,
            endTime: String
        ): Response<ApiResponse<TablesData>> {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun getStatusDictionary(
            lang: String
        ): Response<ApiResponse<DictionaryData<StatusDictionaryDto>>> {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun syncTables(page: Int): Response<TableSyncResponse> {
            throw UnsupportedOperationException("Not used in this test")
        }

        override suspend fun markTableCleaning(token: String): Response<ApiResponse<Unit>> {
            calls += "cleaning:$token"
            return success()
        }

        override suspend fun markTableAvailable(token: String): Response<ApiResponse<Unit>> {
            calls += "available:$token"
            return success()
        }

        override suspend fun markTableOutOfService(token: String): Response<ApiResponse<Unit>> {
            calls += "out-of-service:$token"
            return success()
        }

        private fun success(): Response<ApiResponse<Unit>> {
            return Response.success(ApiResponse(isSuccess = true))
        }
    }
}
