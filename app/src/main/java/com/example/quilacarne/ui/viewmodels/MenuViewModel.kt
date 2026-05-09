package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.AllergenEntity
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishEntity
import kotlinx.coroutines.flow.*
import java.util.UUID

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val categories: StateFlow<List<DishCategoryEntity>> = db.dishCategoryDao()
        .getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allergens: StateFlow<List<AllergenEntity>> = db.ingredientDao()
        .getAllAllergens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<UUID?>(null)
    val selectedCategoryId: StateFlow<UUID?> = _selectedCategoryId

    private val _selectedAllergenIds = MutableStateFlow<Set<UUID>>(emptySet())
    val selectedAllergenIds: StateFlow<Set<UUID>> = _selectedAllergenIds

    val dishes: StateFlow<List<DishEntity>> = combine(
        db.dishDao().getDishesWithIngredients(),
        _selectedCategoryId,
        _selectedAllergenIds
    ) { allDishes, selectedCategoryId, selectedAllergenIds ->
        allDishes.filter { dishWithIngredients ->
            val dish = dishWithIngredients.dish
            val matchesCategory = selectedCategoryId == null || dish.categoryId == selectedCategoryId
            val dishAllergenIds = dishWithIngredients.ingredientsWithAllergens
                .flatMap { it.allergens }
                .map { it.id }
                .toSet()

            matchesCategory && selectedAllergenIds.none { it in dishAllergenIds }
        }.map { it.dish }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleAllergen(allergenId: UUID) {
        _selectedAllergenIds.value = _selectedAllergenIds.value.toMutableSet().apply {
            if (!add(allergenId)) {
                remove(allergenId)
            }
        }
    }

    fun selectCategory(categoryId: UUID?) {
        _selectedCategoryId.value = categoryId
    }

    fun getDishDetails(dishId: UUID): Flow<com.example.quilacarne.data.local.relations.DishWithIngredients?> {
        return db.dishDao().getDishWithIngredients(dishId)
    }
}
