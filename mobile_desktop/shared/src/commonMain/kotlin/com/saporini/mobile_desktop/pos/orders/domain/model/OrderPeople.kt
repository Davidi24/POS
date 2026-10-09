package com.saporini.mobile_desktop.pos.orders.domain.model
import kotlinx.serialization.Serializable
@Serializable data class OrderCustomerChoice(val id: String, val fullName: String, val active: Boolean = true)
@Serializable data class OrderReservationChoice(val id: String, val reservationCode: String, val customerId: String? = null, val customerName: String? = null, val contactName: String? = null, val partySize: Int = 1, val status: String)
