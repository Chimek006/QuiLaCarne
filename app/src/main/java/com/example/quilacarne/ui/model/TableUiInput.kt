package com.example.quilacarne.ui.model

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity

internal data class TableUiInput(
    val tables: List<RestaurantTableEntity> = emptyList(),
    val statuses: List<TableStatusEntity> = emptyList(),
    val reservations: List<ReservationEntity> = emptyList(),
    val orders: List<OrderEntity> = emptyList(),
    val users: List<UsersEntity> = emptyList()
)
