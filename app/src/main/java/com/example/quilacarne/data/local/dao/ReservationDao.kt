package com.example.quilacarne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.quilacarne.data.local.entities.ReservationEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ReservationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reservations: List<ReservationEntity>)

    @Query("SELECT * FROM reservations ORDER BY start_epoch_millis ASC")
    fun getReservationsFlow(): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM reservations WHERE table_id = :tableId ORDER BY start_epoch_millis ASC")
    fun getReservationsForTableFlow(tableId: UUID): Flow<List<ReservationEntity>>

    @Query(
        "SELECT * FROM reservations " +
            "WHERE table_id = :tableId AND is_active = 1 " +
            "AND start_epoch_millis <= :nowMillis AND end_epoch_millis > :nowMillis " +
            "ORDER BY start_epoch_millis ASC LIMIT 1"
    )
    suspend fun getCurrentReservationForTable(tableId: UUID, nowMillis: Long): ReservationEntity?

    @Query(
        "SELECT * FROM reservations " +
            "WHERE table_id = :tableId AND is_active = 1 " +
            "AND start_epoch_millis > :nowMillis " +
            "ORDER BY start_epoch_millis ASC LIMIT 1"
    )
    suspend fun getUpcomingReservationForTable(tableId: UUID, nowMillis: Long): ReservationEntity?

    @Query("DELETE FROM reservations WHERE id NOT IN (:reservationIds)")
    suspend fun deleteReservationsExcept(reservationIds: List<UUID>)

    @Query("DELETE FROM reservations")
    suspend fun clearAll()
}
