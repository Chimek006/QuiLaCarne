package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.remote.api.TableService
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import retrofit2.Response

internal class RemoteTableStatusGateway(
    private val tableService: TableService
) : TableStatusGateway {
    override suspend fun changeStatus(change: TableStatusChange) {
        when (change.statusToken) {
            STATUS_AVAILABLE -> tableService.markTableAvailable(change.tableToken)
            STATUS_CLEANING -> tableService.markTableCleaning(change.tableToken)
            STATUS_OUT_OF_SERVICE -> tableService.markTableOutOfService(change.tableToken)
            else -> error("Nieobslugiwany status stolika ${change.statusToken}")
        }.requireApiSuccess("Nie udalo sie zmienic statusu stolika w API")
    }

    private fun Response<ApiResponse<Unit>>.requireApiSuccess(fallbackMessage: String) {
        val body = body()
        if (!isSuccessful) {
            val rawError = errorBody()?.string()
            throw IllegalStateException(body?.message ?: ApiResponseLogic.errorMessageFromBody(rawError) ?: fallbackMessage)
        }

        ApiResponseLogic.assertWrapperSuccess(body, fallbackMessage)
    }

    private companion object {
        const val STATUS_AVAILABLE = "AVAILABLE"
        const val STATUS_CLEANING = "CLEANING"
        const val STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE"
    }
}
