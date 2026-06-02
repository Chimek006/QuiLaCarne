package com.example.quilacarne.ui.model

import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.ReservationEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity

internal data class TableDetailSnapshot(
    val table: RestaurantTableEntity?,
    val statuses: List<TableStatusEntity>,
    val orders: List<OrderEntity>,
    val users: List<UsersEntity>,
    val reservations: List<ReservationEntity>
)
