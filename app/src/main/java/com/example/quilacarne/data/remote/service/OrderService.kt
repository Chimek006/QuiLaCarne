package com.example.quilacarne.data.remote.service

import com.example.quilacarne.data.remote.dto.request.ReportCreateRequest
import com.example.quilacarne.data.remote.dto.request.ReservationCreateRequest
import com.example.quilacarne.data.remote.dto.request.ReservationDishRequest
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.example.quilacarne.data.remote.dto.response.BootstrapResponse
import com.example.quilacarne.data.remote.dto.response.DictionaryData
import com.example.quilacarne.data.remote.dto.response.DictionaryItem
import com.example.quilacarne.data.remote.dto.response.GuestReportSyncDto
import com.example.quilacarne.data.remote.dto.response.OrderItemSyncDto
import com.example.quilacarne.data.remote.dto.response.OrderSyncDto
import com.example.quilacarne.data.remote.dto.response.PaginatedList
import com.example.quilacarne.data.remote.dto.response.ReservationSyncDto
import com.example.quilacarne.data.remote.dto.response.UserSyncDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrderService {

    @GET("order/item/dictionary")
    suspend fun getOrderItemStatuses(): Response<ApiResponse<DictionaryData<DictionaryItem>>>

    @GET("sync/orders")
    suspend fun syncOrders(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<OrderSyncDto>>>

    @GET("sync/order-items")
    suspend fun syncOrderItems(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<OrderItemSyncDto>>>

    @GET("sync/users")
    suspend fun syncUsers(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<UserSyncDto>>>

    @GET("sync/reservations")
    suspend fun syncReservations(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<ReservationSyncDto>>>

    @GET("sync/reports")
    suspend fun syncReports(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<GuestReportSyncDto>>>

    @GET("sync/bootstrap")
    suspend fun getBootstrap(): Response<ApiResponse<BootstrapResponse>>

    @POST("reservations")
    suspend fun createReservation(
        @Body request: ReservationCreateRequest
    ): Response<ApiResponse<Unit>>

    @PATCH("reservations/{token}/assign-waiter")
    suspend fun assignWaiterToReservation(
        @Path("token") reservationToken: String
    ): Response<ApiResponse<Unit>>

    @PATCH("reservations/{token}/complete")
    suspend fun completeReservation(
        @Path("token") reservationToken: String
    ): Response<ApiResponse<Unit>>

    @PATCH("reservations/{token}/absent")
    suspend fun markReservationAbsent(
        @Path("token") reservationToken: String
    ): Response<ApiResponse<Unit>>

    @POST("reservations/item/add")
    suspend fun addReservationItems(
        @Query("reservationToken") reservationToken: String,
        @Body items: List<ReservationDishRequest>
    ): Response<ApiResponse<Unit>>

    @HTTP(method = "DELETE", path = "reservations/item/remove", hasBody = true)
    suspend fun removeReservationItem(
        @Query("reservationToken") reservationToken: String,
        @Body item: ReservationDishRequest
    ): Response<ApiResponse<Unit>>

    @POST("report")
    suspend fun createReport(
        @Body request: ReportCreateRequest
    ): Response<ApiResponse<Unit>>
}
