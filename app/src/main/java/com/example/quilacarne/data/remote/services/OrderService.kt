package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OrderService {

    @GET("api/order/item/dictionary")
    suspend fun getOrderItemStatuses(): Response<ApiResponse<DictionaryData<DictionaryItem>>>

    @GET("api/sync/orders")
    suspend fun syncOrders(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<OrderSyncDto>>>

    @GET("api/sync/order-items")
    suspend fun syncOrderItems(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<OrderItemSyncDto>>>

    @GET("api/sync/users")
    suspend fun syncUsers(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<UserSyncDto>>>

    @GET("api/sync/bootstrap")
    suspend fun getBootstrap(): Response<ApiResponse<BootstrapResponse>>
}
