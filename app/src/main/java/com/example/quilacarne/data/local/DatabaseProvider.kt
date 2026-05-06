package com.example.quilacarne.data.local

import android.content.Context
import androidx.room.Room
import com.example.quilacarne.data.utils.SecurityUtil
import net.sqlcipher.database.SupportFactory

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {

            val passphrase = SecurityUtil.getDatabasePassword(context)
            android.util.Log.d("MOJE_HASLO", passphrase.decodeToString())
            val factory = SupportFactory(passphrase)

            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "quilacarne.db"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()

            INSTANCE = instance
            instance
        }
    }
}