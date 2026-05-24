package com.example.quilacarne.data.repository.sync

import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import java.util.UUID

internal data class TablesSyncSnapshot(
    val statuses: List<TableStatusEntity> = emptyList(),
    val tables: List<RestaurantTableEntity> = emptyList(),
    val tableTokens: List<Pair<UUID, String>> = emptyList()
)
