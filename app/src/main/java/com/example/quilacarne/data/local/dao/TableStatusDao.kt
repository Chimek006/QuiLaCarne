package com.example.quilacarne.data.local.dao

import androidx.room.*
import com.example.quilacarne.data.local.entities.TableStatusEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface TableStatusDao {
    @Query("SELECT * FROM table_status WHERE deleted_at IS NULL")
    fun getAllStatuses(): Flow<List<TableStatusEntity>>

    @Query("SELECT * FROM table_status WHERE deleted_at IS NULL")
    suspend fun getAllStatusesOnce(): List<TableStatusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(statuses: List<TableStatusEntity>)
}
