package com.example.quilacarne.data.sync.coordinator

internal data class OperationalSyncTarget(
    val key: String,
    val includeOperational: Boolean,
    val includeMenu: Boolean
) {
    companion object {
        val OPERATIONAL_ONLY = OperationalSyncTarget(
            key = "operational",
            includeOperational = true,
            includeMenu = false
        )

        fun fromRoute(route: String?): OperationalSyncTarget? {
            val normalizedRoute = route
                ?.substringBefore("?")
                ?.trim()
                ?: return null

            return when {
                normalizedRoute == "tables" -> OPERATIONAL_ONLY.copy(key = "tables")
                normalizedRoute.startsWith("table/") -> OPERATIONAL_ONLY.copy(key = "table-detail")
                normalizedRoute.startsWith("report_client/") -> OPERATIONAL_ONLY.copy(key = "report-client")
                normalizedRoute == "menu" -> OperationalSyncTarget(
                    key = "menu",
                    includeOperational = false,
                    includeMenu = true
                )
                normalizedRoute.startsWith("dish_detail/") -> OperationalSyncTarget(
                    key = "dish-detail",
                    includeOperational = false,
                    includeMenu = true
                )
                normalizedRoute.startsWith("order_add/") -> OperationalSyncTarget(
                    key = "order-add",
                    includeOperational = true,
                    includeMenu = true
                )
                else -> null
            }
        }
    }
}
