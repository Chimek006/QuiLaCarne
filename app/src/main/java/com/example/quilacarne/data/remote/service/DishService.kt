package com.example.quilacarne.data.remote.service

import com.example.quilacarne.data.remote.dto.response.AllergenDictionaryData
import com.example.quilacarne.data.remote.dto.response.ApiResponse
import com.example.quilacarne.data.remote.dto.response.CategoriesData
import com.example.quilacarne.data.remote.dto.response.DishSyncDto
import com.example.quilacarne.data.remote.dto.response.IngredientSyncDto
import com.example.quilacarne.data.remote.dto.response.PaginatedList
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DishService {
    @GET("sync/dishes")
    suspend fun syncDishes(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<DishSyncDto>>>

    @GET("sync/ingredients")
    suspend fun syncIngredients(
        @Query("page") page: Int = 1
    ): Response<ApiResponse<PaginatedList<IngredientSyncDto>>>

    @GET("dishes/dictionary")
    suspend fun getCategories(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<ApiResponse<CategoriesData>>

    @GET("dishes/allergens/dictionary")
    suspend fun getAllergens(
        @Header("Accept-Language") lang: String = "pl"
    ): Response<ApiResponse<AllergenDictionaryData>>
}
