package com.example.quilacarne.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.AppDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val categories: StateFlow<List<DishCategoryEntity>> = db.dishCategoryDao()
        .getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selectedCategoryId = MutableStateFlow<UUID?>(null)
    val selectedCategoryId: StateFlow<UUID?> = _selectedCategoryId

    val dishes: StateFlow<List<DishEntity>> = combine(
        db.dishDao().getAvailableDishes(),
        _selectedCategoryId
    ) { allDishes, selectedId ->
        if (selectedId == null) {
            allDishes
        } else {
            allDishes.filter { it.categoryId == selectedId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(categoryId: UUID?) {
        _selectedCategoryId.value = categoryId
    }

    fun getDishDetails(dishId: UUID): Flow<com.example.quilacarne.data.local.relations.DishWithIngredients?> {
        return db.dishDao().getDishWithIngredients(dishId)
    }
}