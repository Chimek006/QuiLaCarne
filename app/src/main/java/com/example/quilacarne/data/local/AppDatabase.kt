package com.example.quilacarne.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.quilacarne.data.local.dao.*
import com.example.quilacarne.data.local.entities.*
import android.content.Context
import androidx.room.Room
import com.example.quilacarne.data.utils.SecurityUtil
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        UsersEntity::class,
        TableStatusEntity::class,
        OrderStatusEntity::class,
        DishCategoryEntity::class,
        GuestReportStatusEntity::class,
        RestaurantTableEntity::class,
        IngredientEntity::class,
        AllergenEntity::class,
        DishEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        GuestReportEntity::class,
        DishCompositionEntity::class,
        IngredientAllergenEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tableStatusDao(): TableStatusDao
    abstract fun dishCategoryDao(): DishCategoryDao
    abstract fun dishDao(): DishDao
    abstract fun orderDao(): OrderDao
}