package com.example.quilacarne.data.local.dao

import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.DishCategoryEntity
import com.example.quilacarne.data.local.entities.DishEntity
import com.example.quilacarne.data.local.entities.OrderEntity
import com.example.quilacarne.data.local.entities.OrderItemEntity
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import com.example.quilacarne.data.local.entities.UsersEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var tableId: UUID
    private lateinit var waiterId: UUID
    private lateinit var dishId: UUID

    @Before
    fun setUp() = runTest {
        db = TestDatabaseFactory.create()
        val statusId = UUID.randomUUID()
        tableId = UUID.randomUUID()
        waiterId = UUID.randomUUID()
        dishId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()

        db.tableStatusDao().insertAll(
            listOf(
                TableStatusEntity(
                    id = statusId,
                    token = "OCCUPIED",
                    namePl = "Zajety",
                    nameEn = "Occupied",
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
        db.restaurantTableDao().insertTables(
            listOf(
                RestaurantTableEntity(
                    id = tableId,
                    tableNumber = 1,
                    capacity = 4,
                    statusId = statusId,
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
        db.userDao().insertUser(
            UsersEntity(
                id = waiterId,
                username = "waiter",
                password = "password",
                isActive = true,
                role = "waiter",
                createdAt = "created",
                updatedAt = "updated"
            )
        )
        db.dishCategoryDao().insertAll(
            listOf(
                DishCategoryEntity(
                    id = categoryId,
                    namePl = "Pizza",
                    nameEn = "Pizza",
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
        db.dishDao().insertDishes(
            listOf(
                DishEntity(
                    id = dishId,
                    categoryId = categoryId,
                    name = "Margherita",
                    price = 3200,
                    isAvailable = true,
                    imageUrl = null,
                    createdAt = "created",
                    updatedAt = "updated"
                )
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertOrderAndGetActiveOrderForTable() = runTest {
        val order = order(totalPrice = 3200)

        db.orderDao().insertOrder(order)

        assertEquals(order, db.orderDao().getActiveOrderForTable(tableId).first())
        assertEquals(order, db.orderDao().getActiveOrderForTableOnce(tableId))
    }

    @Test
    fun updateOrderWaiterTotalAndMoveOrder() = runTest {
        val order = order(totalPrice = 3200, waiterId = null)
        val newTable = RestaurantTableEntity(
            id = UUID.randomUUID(),
            tableNumber = 2,
            capacity = 4,
            statusId = null,
            createdAt = "created",
            updatedAt = "updated"
        )
        db.restaurantTableDao().insertTables(listOf(newTable))
        db.orderDao().insertOrder(order)

        db.orderDao().updateOrderWaiter(order.id, waiterId, "waiter-updated")
        db.orderDao().updateOrderTotal(order.id, 6400, "price-updated")
        val movedCount = db.orderDao().moveOrderToTable(order.id, newTable.id, "table-updated")

        val updated = db.orderDao().getActiveOrderForTableOnce(newTable.id)
        assertEquals(1, movedCount)
        assertEquals(waiterId, updated?.waiterId)
        assertEquals(6400, updated?.totalPrice)
    }

    @Test
    fun insertAndUpdateOrderItems() = runTest {
        val order = order(totalPrice = 3200)
        val itemId = UUID.randomUUID()
        val item = OrderItemEntity(
            id = itemId,
            orderId = order.id,
            productId = dishId,
            quantity = 1,
            priceAtTimeOfOrder = 3200,
            createdAt = "created",
            updatedAt = "created"
        )
        db.orderDao().insertOrder(order)
        db.orderDao().insertOrderItem(item)

        db.orderDao().updateOrderItemQuantity(itemId, quantity = 3, updatedAt = "updated")

        val updated = db.orderDao().getItemForOrderAndDish(order.id, dishId)
        assertEquals(3, updated?.quantity)
        assertEquals(listOf(updated), db.orderDao().getItemsForOrderOnce(order.id))
    }

    @Test
    fun deleteOrderItemRemovesItFromOrder() = runTest {
        val order = order(totalPrice = 3200)
        val item = OrderItemEntity(
            id = UUID.randomUUID(),
            orderId = order.id,
            productId = dishId,
            quantity = 1,
            priceAtTimeOfOrder = 3200,
            createdAt = "created",
            updatedAt = "created"
        )
        db.orderDao().insertOrderWithItems(order, listOf(item))

        db.orderDao().deleteOrderItem(item.id)

        assertEquals(emptyList<OrderItemEntity>(), db.orderDao().getItemsForOrderOnce(order.id))
    }

    private fun order(
        totalPrice: Int,
        waiterId: UUID? = this.waiterId
    ): OrderEntity {
        return OrderEntity(
            id = UUID.randomUUID(),
            tableId = tableId,
            waiterId = waiterId,
            statusId = null,
            totalPrice = totalPrice,
            createdAt = "created",
            updatedAt = "updated"
        )
    }
}
