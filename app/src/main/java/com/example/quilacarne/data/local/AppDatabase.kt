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
        ReservationEntity::class,
        PendingRequestEntity::class
    ],
    version = 7,
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
    abstract fun orderStatusDao(): OrderStatusDao
    abstract fun guestReportDao(): GuestReportDao

    abstract fun restaurantTableDao(): RestaurantTableDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun pendingRequestDao(): PendingRequestDao

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
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_requests (
                        id TEXT NOT NULL,
                        request_id TEXT NOT NULL,
                        method TEXT NOT NULL,
                        path TEXT NOT NULL,
                        query_json TEXT,
                        body_json TEXT,
                        status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        last_attempt_at INTEGER,
                        attempt_count INTEGER NOT NULL,
                        entity_type TEXT,
                        entity_local_id TEXT,
                        optimistic_action TEXT,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_pending_requests_request_id " +
                        "ON pending_requests(request_id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pending_requests_status_created_at " +
                        "ON pending_requests(status, created_at)"
                )
            }
        }
    }
}
