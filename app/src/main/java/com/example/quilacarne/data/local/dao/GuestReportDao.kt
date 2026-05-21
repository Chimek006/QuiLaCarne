package com.example.quilacarne.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.quilacarne.data.local.entities.GuestReportEntity
import com.example.quilacarne.data.local.entities.GuestReportStatusEntity
import java.util.UUID

@Dao
interface GuestReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<GuestReportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatuses(statuses: List<GuestReportStatusEntity>)

    @Query("UPDATE guest_reports SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :reportId")
    suspend fun markReportDeleted(reportId: UUID, deletedAt: String)
}
