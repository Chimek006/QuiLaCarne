package com.example.quilacarne.data.local.dao

import com.example.quilacarne.data.local.AppDatabase
import com.example.quilacarne.data.local.entities.RestaurantTableEntity
import com.example.quilacarne.data.local.entities.TableStatusEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestaurantTableDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var availableStatusId: UUID
    private lateinit var cleaningStatusId: UUID

    @Before
    fun setUp() = runTest {
        db = TestDatabaseFactory.create()
        availableStatusId = UUID.randomUUID()
        cleaningStatusId = UUID.randomUUID()
        db.tableStatusDao().insertAll(
            listOf(
                status(availableStatusId, "AVAILABLE"),
                status(cleaningStatusId, "CLEANING")
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetTableByIdRoundTrip() = runTest {
        val table = table(tableNumber = 2, statusId = availableStatusId)

        db.restaurantTableDao().insertTables(listOf(table))

        assertEquals(table, db.restaurantTableDao().getTableById(table.id).first())
        assertEquals(listOf(table), db.restaurantTableDao().getAllTablesFlow().first())
    }

    @Test
    fun updateStatusChangesOnlyRequestedTable() = runTest {
        val tableOne = table(tableNumber = 1, statusId = availableStatusId)
        val tableTwo = table(tableNumber = 2, statusId = availableStatusId)
        db.restaurantTableDao().insertTables(listOf(tableOne, tableTwo))

        db.restaurantTableDao().updateStatus(tableOne.id, cleaningStatusId, "updated")

        assertEquals(cleaningStatusId, db.restaurantTableDao().getTableByIdOnce(tableOne.id)?.statusId)
        assertEquals(availableStatusId, db.restaurantTableDao().getTableByIdOnce(tableTwo.id)?.statusId)
    }

    @Test
    fun deleteTablesExceptRemovesMissingTables() = runTest {
        val kept = table(tableNumber = 1, statusId = availableStatusId)
        val removed = table(tableNumber = 2, statusId = availableStatusId)
        db.restaurantTableDao().insertTables(listOf(kept, removed))

        db.restaurantTableDao().deleteTablesExcept(listOf(kept.id))

        assertEquals(listOf(kept), db.restaurantTableDao().getAllTablesOnce())
        assertNull(db.restaurantTableDao().getTableByIdOnce(removed.id))
    }

    private fun status(id: UUID, token: String): TableStatusEntity {
        return TableStatusEntity(
            id = id,
            token = token,
            namePl = token,
            nameEn = token,
            createdAt = "created",
            updatedAt = "updated"
        )
    }

    private fun table(tableNumber: Int, statusId: UUID): RestaurantTableEntity {
        return RestaurantTableEntity(
            id = UUID.randomUUID(),
            tableNumber = tableNumber,
            capacity = 4,
            statusId = statusId,
            createdAt = "created",
            updatedAt = "created"
        )
    }
}
