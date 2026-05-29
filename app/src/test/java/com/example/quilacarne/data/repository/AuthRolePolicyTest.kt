package com.example.quilacarne.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRolePolicyTest {
    @Test
    fun waiterRolesAreAllowed() {
        assertEquals(true, AuthRolePolicy.isWaiter(listOf("ROLE_WAITER")))
        assertEquals(true, AuthRolePolicy.isWaiter(listOf("waiter")))
        assertEquals(true, AuthRolePolicy.isWaiter("ROLE_MANAGER,ROLE_WAITER"))
    }

    @Test
    fun nonWaiterRolesAreRejected() {
        assertEquals(false, AuthRolePolicy.isWaiter(listOf("ROLE_MANAGER")))
        assertEquals(false, AuthRolePolicy.isWaiter(listOf("ROLE_CLIENT")))
        assertEquals(false, AuthRolePolicy.isWaiter(emptyList()))
    }

    @Test
    fun roleStringNormalizesDistinctRoles() {
        assertEquals(
            "ROLE_MANAGER,ROLE_WAITER",
            AuthRolePolicy.roleString(listOf("ROLE_MANAGER", "role_manager", "ROLE_WAITER"))
        )
        assertEquals("UNKNOWN", AuthRolePolicy.roleString(emptyList()))
    }
}
