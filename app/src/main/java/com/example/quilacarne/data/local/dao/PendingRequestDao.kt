package com.example.quilacarne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.quilacarne.data.local.entities.PendingRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingRequestDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(request: PendingRequestEntity)

    @Query("SELECT * FROM pending_requests WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 1")
    suspend fun getNextPending(): PendingRequestEntity?

    @Query("DELETE FROM pending_requests WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE pending_requests " +
            "SET last_attempt_at = :time, attempt_count = attempt_count + 1 " +
            "WHERE id = :id"
    )
    suspend fun markAttempt(id: String, time: Long)

    @Query("SELECT COUNT(*) FROM pending_requests WHERE status = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT * FROM pending_requests WHERE status = 'PENDING' ORDER BY created_at ASC")
    fun observePendingRequests(): Flow<List<PendingRequestEntity>>

    @Query(
        "SELECT * FROM pending_requests " +
            "WHERE status = 'PENDING' AND entity_type = :entityType " +
            "ORDER BY created_at ASC"
    )
    suspend fun getPendingByEntityType(entityType: String): List<PendingRequestEntity>
}
