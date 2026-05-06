package com.example.quilacarne.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.quilacarne.data.local.dao.*
import com.example.quilacarne.data.local.entities.*
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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tableStatusDao(): TableStatusDao
    abstract fun dishCategoryDao(): DishCategoryDao
    abstract fun dishDao(): DishDao
    abstract fun orderDao(): OrderDao

    abstract fun ingredientDao(): IngredientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = SecurityUtil.getDatabasePassword(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quilacarne_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}