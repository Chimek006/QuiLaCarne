package com.example.quilacarne.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

@Entity(tableName = "table_status")
data class TableStatusEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "token") val token: String,
    @ColumnInfo(name = "name_pl") val namePl: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null
)