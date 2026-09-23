package com.saporini.mobile_desktop.pos.orders.data.dto

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderFulfillmentStatus
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderSource
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderStatus
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderType
import kotlinx.serialization.Serializable
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal

@Serializable
data class OrderSummaryResponseDto(
    val id: String,
    val restaurantId: String,
    val branchId: String,
    val orderNumber: String,
    val currency: String,
    val orderType: OrderType,
    val source: OrderSource,
    val status: OrderStatus,
    val fulfillmentStatus: OrderFulfillmentStatus,
    val total: OrderDecimal,
    val itemCount: Int? = null,
    val openedAt: String,
    val tableId: String? = null,
    val tableNumber: String? = null,
    val tableName: String? = null,
    val customerId: String? = null,
    val customerName: String? = null,
    val guestCount: Int? = null,
    val notes: String? = null,
    val closedAt: String? = null,
    val createdBy: String? = null
)
