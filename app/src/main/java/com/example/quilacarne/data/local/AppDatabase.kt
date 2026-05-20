package com.example.quilacarne.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.quilacarne.data.local.dao.*
import com.example.quilacarne.data.local.entities.*
import com.example.quilacarne.utils.SecurityUtil
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
        IngredientAllergenEntity::class,
        ReservationEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun tableStatusDao(): TableStatusDao
    abstract fun dishCategoryDao(): DishCategoryDao
    abstract fun dishDao(): DishDao
    abstract fun orderDao(): OrderDao
    abstract fun reservationDao(): ReservationDao

    abstract fun restaurantTableDao(): RestaurantTableDao
    abstract fun ingredientDao(): IngredientDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    context.deleteDatabase("quilacarne_db-old")
                    context.deleteDatabase("quilacarne_db-v")
                } catch (e: Exception) {
                    Log.w("DB_CLEANUP", "Nie udało się wyczyścić starych baz")
                }

                val passphrase = SecurityUtil.getDatabasePassword(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quilacarne_db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE order_items " +
                        "ADD COLUMN status_tokens TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
