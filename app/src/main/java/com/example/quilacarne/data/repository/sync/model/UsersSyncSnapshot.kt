package com.example.quilacarne.data.repository.sync.model

import com.example.quilacarne.data.local.entities.UsersEntity
import java.util.UUID

internal data class UsersSyncSnapshot(
    val users: List<UsersEntity> = emptyList(),
    val userTokens: List<Pair<UUID, String>> = emptyList()
)
