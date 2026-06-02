package com.example.quilacarne.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "guest_report_status")
data class GuestReportStatusEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "name_pl") val namePl: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)
