package com.example.quilacarne.data.repository.order

import com.example.quilacarne.data.remote.network.RetrofitClient
import com.example.quilacarne.data.remote.dto.response.BootstrapResponse
import com.example.quilacarne.data.remote.dto.response.DictionaryItem
import com.example.quilacarne.data.remote.dto.response.OrderItemSyncDto
import com.example.quilacarne.data.remote.dto.response.OrderSyncDto
import com.example.quilacarne.data.remote.dto.response.values
import java.lang.Exception

class OrderRepository {
    private val api = RetrofitClient.orderService

    suspend fun getOrderItemStatuses(): Result<List<DictionaryItem>> {
        return try {
            val response = api.getOrderItemStatuses()
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()?.data.values())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Błąd pobierania słowników"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrdersSync(page: Int): Result<List<OrderSyncDto>> {
        return try {
            val response = api.syncOrders(page = page)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()?.data?.items ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Błąd synchronizacji zamówień"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrderItemsSync(page: Int): Result<List<OrderItemSyncDto>> {
        return try {
            val response = api.syncOrderItems(page = page)
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                Result.success(response.body()?.data?.items ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "Błąd synchronizacji pozycji"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBootstrap(): Result<BootstrapResponse> {
        return try {
            val response = api.getBootstrap()
            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(Exception("Brak danych (data=null) w odpowiedzi bootstrap"))
                }
            } else {
                Result.failure(Exception(response.body()?.message ?: "Błąd pobierania manifestu"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
