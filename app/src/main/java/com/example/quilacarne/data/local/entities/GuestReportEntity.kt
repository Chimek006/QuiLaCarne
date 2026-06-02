package com.example.quilacarne.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "guest_reports",
    foreignKeys = [
        ForeignKey(entity = UsersEntity::class, parentColumns = ["id"], childColumns = ["reporter_id"]),
        ForeignKey(entity = GuestReportStatusEntity::class, parentColumns = ["id"], childColumns = ["status_id"])
    ]
)
data class GuestReportEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "reporter_id") val reporterId: UUID?,
    @ColumnInfo(name = "status_id") val statusId: UUID?,
    val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)
