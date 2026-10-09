package com.saporini.mobile_desktop.pos.orders.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class OrderPageResponseDto(
    val items: List<OrderSummaryResponseDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean = false
)
