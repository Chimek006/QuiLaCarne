package com.example.quilacarne.data.remote.services

import com.example.quilacarne.data.remote.models.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DishService {
    @GET("api/sync/dishes")
    suspend fun syncDishes(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<DishSyncDto>>>

    @GET("api/sync/ingredients")
    suspend fun syncIngredients(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<IngredientSyncDto>>>

    @GET("api/dishes/dictionary")
    suspend fun getCategories(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<ApiResponse<CategoriesData>>

    @GET("api/dishes/allergens/dictionary")
    suspend fun getAllergens(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<ApiResponse<AllergenDictionaryData>>
}
