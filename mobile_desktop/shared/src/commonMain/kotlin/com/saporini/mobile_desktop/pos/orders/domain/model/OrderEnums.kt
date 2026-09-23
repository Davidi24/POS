package com.saporini.mobile_desktop.pos.orders.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames

@Serializable
enum class OrderStatus {
    DRAFT,
    OPEN,
    CLOSED,
    CANCELLED,
    VOIDED
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class OrderFulfillmentStatus {
    // Decode older saved/replayed API responses without exposing legacy states.
    @JsonNames("CANCELLED")
    PENDING,
    IN_PREPARATION,
    READY,
    PARTIALLY_FULFILLED,
    @JsonNames("DELIVERED")
    FULFILLED
}

@Serializable
enum class OrderType {
    DINE_IN,
    TAKEAWAY,
    DELIVERY
}

@Serializable
enum class OrderSource {
    POS,
    WEB,
    MOBILE,
    QR_TABLE,
    KIOSK,
    PHONE,
    THIRD_PARTY
}

@Serializable
enum class OrderLineItemStatus {
    PENDING,
    FIRED,
    PREPARING,
    READY,
    FULFILLED,
    CANCELLED,
    VOIDED
}

@Serializable
enum class OrderDiscountType {
    PERCENTAGE,
    FIXED_AMOUNT,
    PROMOTION,
    LOYALTY,
    MANUAL,
    COMP
}

@Serializable
enum class OrderEventType {
    CREATED,
    UPDATED,
    STATUS_UPDATED,
    FULFILLMENT_UPDATED,
    PAYMENT_UPDATED,
    ITEM_ADDED,
    ITEM_UPDATED,
    ITEM_REMOVED,
    ITEM_VOIDED,
    DISCOUNT_APPLIED,
    DISCOUNT_REMOVED,
    SENT_TO_KITCHEN,
    READY,
    FULFILLED,
    TABLE_CHANGED,
    RESERVATION_LINKED,
    REOPENED,
    CLOSED,
    CANCELLED,
    VOIDED,
    NOTE_ADDED
}

@Serializable
enum class OrderPaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED,
    VOIDED
}
