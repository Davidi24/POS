package com.saporini.mobile_desktop.orders

import com.saporini.mobile_desktop.auth.data.dto.CurrentUserResponse
import com.saporini.mobile_desktop.pos.orders.data.dto.*
import com.saporini.mobile_desktop.pos.orders.domain.model.*
import com.saporini.mobile_desktop.pos.orders.domain.repository.OrderRepository
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy

internal val orderJson = Json { ignoreUnknownKeys = true }
internal fun orderFixture(id: String = "order-1", branch: String = "branch-1"): String = """
{
  "id":"$id", "restaurantId":"restaurant-1", "branchId":"$branch",
  "orderNumber":"ORD-001", "currency":"EUR", "orderType":"DINE_IN", "source":"POS",
  "status":"OPEN", "fulfillmentStatus":"PENDING", "paymentStatus":"UNPAID",
  "subtotal":9007199254740993.25, "discountTotal":0.00, "taxTotal":0.00,
  "serviceChargeTotal":0.00, "total":9007199254740993.25,
  "openedAt":"2026-09-22T12:00:00Z", "closedAt":null,
  "createdAt":"2026-09-22T12:00:00Z", "updatedAt":"2026-09-22T12:00:00Z",
  "tableId":null, "customerName":null, "lineItems":null, "discounts":null, "events":null
}
""".trimIndent()
internal fun order(id: String = "order-1", branch: String = "branch-1"): Order =
    orderJson.decodeFromString<OrderResponseDto>(orderFixture(id, branch)).toDomain()
internal fun summary(id: String = "order-1"): OrderSummary =
    orderJson.decodeFromString<OrderSummaryResponseDto>(orderFixture(id)).toDomain()
internal fun user(id: String = "user-1", branch: String = "branch-1", permissions: List<String> = listOf("ORDER_READ", "ORDER_CREATE", "ORDER_UPDATE")) = CurrentUserResponse(
    id = id, restaurantId = "restaurant-1", defaultBranchId = branch,
    email = "test@example.com", username = "test", firstName = "Test", lastName = "User",
    isActive = true, emailVerified = true, phoneVerified = true, roles = emptyList(), permissions = permissions
)
internal fun unsupportedRepository(): OrderRepository = Proxy.newProxyInstance(
    OrderRepository::class.java.classLoader, arrayOf(OrderRepository::class.java)
) { _, method, _ -> error("Unexpected repository operation: ${method.name}") } as OrderRepository
