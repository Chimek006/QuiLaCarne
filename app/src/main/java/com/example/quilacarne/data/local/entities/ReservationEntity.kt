package com.example.quilacarne.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "reservations",
    foreignKeys = [
        ForeignKey(
            entity = RestaurantTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["table_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["table_id"]),
        Index(value = ["table_id", "start_epoch_millis", "end_epoch_millis"])
    ]
)
data class ReservationEntity(
    @PrimaryKey val id: UUID,
    val token: String,
    @ColumnInfo(name = "table_id") val tableId: UUID,
    @ColumnInfo(name = "table_token") val tableToken: String,
    @ColumnInfo(name = "user_token") val userToken: String?,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "end_time") val endTime: String,
    @ColumnInfo(name = "start_epoch_millis") val startEpochMillis: Long,
    @ColumnInfo(name = "end_epoch_millis") val endEpochMillis: Long,
    @ColumnInfo(name = "status_tokens") val statusTokens: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)
