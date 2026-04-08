package com.example.quilacarne.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "quilacarne_db"
            )
                .fallbackToDestructiveMigration()
                .build()

            INSTANCE = instance
            instance
        }
    }
}