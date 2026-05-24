package com.example.quilacarne.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_requests",
    indices = [
        Index(value = ["request_id"], unique = true),
        Index(value = ["status", "created_at"])
    ]
)
data class PendingRequestEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    val method: String,
    val path: String,
    @ColumnInfo(name = "query_json") val queryJson: String? = null,
    @ColumnInfo(name = "body_json") val bodyJson: String? = null,
    val status: String = STATUS_PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_attempt_at") val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "entity_type") val entityType: String? = null,
    @ColumnInfo(name = "entity_local_id") val entityLocalId: String? = null,
    @ColumnInfo(name = "optimistic_action") val optimisticAction: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
    }
}
