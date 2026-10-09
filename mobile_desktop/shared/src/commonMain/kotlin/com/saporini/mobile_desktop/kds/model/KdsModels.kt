package com.saporini.mobile_desktop.kds.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable data class KdsModifier(val name: String, val quantity: Int = 1, val notes: String? = null)
@Serializable data class KdsTicketItem(
    val id: String, val orderLineItemId: String? = null, val menuItemId: String? = null,
    val itemNameSnapshot: String = "", val quantity: Int = 0, val status: String = "UNKNOWN",
    val priority: String = "NORMAL", val seatLabel: String? = null, val notes: String? = null,
    val variantNameSnapshot: String? = null, val optionsPerUnit: Boolean = true,
    val modifiers: List<KdsModifier> = emptyList(), val firedAt: String? = null,
    val readyAt: String? = null, val completedAt: String? = null,
    val createdAt: String? = null, val updatedAt: String? = null
)
@Serializable data class KdsTicket(
    val id: String, val restaurantId: String, val branchId: String, val stationId: String,
    val ticketNumber: String = "", val orderId: String, val orderNumber: String = "",
    val stationCode: String? = null, val stationName: String? = null, val deviceId: String? = null,
    val tableId: String? = null, val tableNumber: String? = null, val tableName: String? = null,
    val customerId: String? = null, val customerName: String? = null, val guestCount: Int? = null,
    val status: String = "UNKNOWN", val priority: String = "NORMAL", val courseName: String? = null,
    val notes: String? = null, val voidReason: String? = null, val firedAt: String? = null,
    val startedAt: String? = null, val readyAt: String? = null, val completedAt: String? = null,
    val dueAt: String? = null, val createdAt: String? = null, val updatedAt: String? = null,
    val createdBy: String? = null, val updatedBy: String? = null, val items: List<KdsTicketItem> = emptyList(),
    // The booking's occasion for the kitchen, e.g. "🎂 Birthday · Cake from us, Candles · 30 candles at dessert".
    val occasion: String? = null
) {
    val terminal get() = status in setOf("COMPLETED", "CANCELLED")
    // A due time is a service deadline, not a reason to hide already-fired food.
    fun waitingForFire(now: Instant) = !terminal && status == "PENDING" && (priority == "HOLD_FIRE" || dueAt.asInstant()?.let { it > now } == true)
    fun allows(action: KdsAction, itemId: String? = null): Boolean {
        if (terminal) return false
        val targets = if (itemId == null) items else items.filter { it.id == itemId }
        if (targets.isEmpty()) return false
        if (action == KdsAction.COMPLETE && itemId == null && targets.any { it.status !in setOf("READY", "EXPO_READY", "COMPLETED", "CANCELLED") }) return false
        return targets.any { action.allows(it.status) }
    }
}
@Serializable data class KdsBoard(
    val stationId: String, val stationCode: String? = null, val stationName: String = "",
    val screenLabel: String? = null, val deviceId: String? = null, val deviceCode: String? = null,
    val deviceName: String? = null, val stationType: String = "PREP", val displayOrder: Int = 0,
    val active: Boolean = true, val activeTicketCount: Int = 0, val readyTicketCount: Int = 0,
    val completedTicketCount: Int = 0, val tickets: List<KdsTicket> = emptyList()
)
@Serializable data class KdsTicketPage(
    val items: List<KdsTicket> = emptyList(), val page: Int = 0, val size: Int = 30,
    val totalElements: Long = 0, val totalPages: Int = 0, val hasNext: Boolean = false
)
@Serializable data class KdsRouting(
    val menuItemId: String, val id: String? = null, val menuItemName: String? = null,
    val displayOrder: Int = 0, val priority: String = "NORMAL", val courseLabel: String? = null,
    val active: Boolean = true
)
@Serializable data class KdsStation(
    val id: String, val restaurantId: String, val branchId: String, val code: String = "",
    val name: String, val stationType: String = "PREP", val displayOrder: Int = 0,
    val active: Boolean = true, val acceptsScheduledOrders: Boolean = true,
    val deviceId: String? = null, val deviceCode: String? = null, val deviceName: String? = null,
    val screenLabel: String? = null, val notes: String? = null, val routings: List<KdsRouting> = emptyList()
)
@Serializable data class KdsDevice(
    val deviceId: String, val deviceCode: String = "", val deviceName: String = "",
    val status: String? = null, val active: Boolean = true, val online: Boolean = false,
    val assignedStationId: String? = null, val assignedStationCode: String? = null,
    val assignedStationName: String? = null
)
@Serializable data class KdsRoutingInput(val menuItemId: String, val displayOrder: Int = 0, val priority: String = "NORMAL", val courseLabel: String? = null, val active: Boolean = true)
@Serializable data class KdsStationInput(
    val name: String, val stationType: String, val code: String? = null,
    val displayOrder: Int = 0, val active: Boolean = true, val acceptsScheduledOrders: Boolean = true,
    val screenLabel: String? = null, val notes: String? = null, val deviceId: String? = null,
    val routings: List<KdsRoutingInput> = emptyList()
) {
    fun problem(): String? = when {
        name.isBlank() || name.length > 150 -> "Enter a station name of up to 150 characters."
        stationType !in setOf("PREP", "GRILL", "FRY", "GARDE_MANGER", "BAR", "DESSERT", "EXPO", "PACKING") -> "Choose a station type."
        displayOrder < 0 || routings.any { it.displayOrder < 0 } -> "Display order cannot be negative."
        (code?.length ?: 0) > 80 || (screenLabel?.length ?: 0) > 80 -> "Station code and label must be 80 characters or fewer."
        routings.any { it.menuItemId.isBlank() || it.priority !in setOf("NORMAL", "RUSH", "VIP", "HOLD_FIRE") } -> "Check the station's dish routing."
        routings.map { it.menuItemId }.distinct().size != routings.size -> "Each dish can only be routed once per station."
        else -> null
    }
}
// When the waiters' Kitchen Status marks an order as taking long and ready food as waiting (Admin Hub settings).
@Serializable data class KdsPosTiming(val slowAfterMinutes: Int = 20, val readyWaitingMinutes: Int = 5)
@Serializable data class KdsActionInput(val note: String? = null, val reason: String? = null)
enum class KdsAction(val path: String) {
    FIRE("fire"), START("start"), READY("ready"), COMPLETE("complete");
    fun allows(status: String) = when (this) {
        FIRE -> status == "PENDING"
        START -> status in setOf("PENDING", "FIRED")
        READY -> status in setOf("PENDING", "FIRED", "IN_PROGRESS")
        COMPLETE -> status in setOf("READY", "EXPO_READY")
    }
}
data class KdsScope(val userId: String, val restaurantId: String, val branchId: String)
data class KdsHistoryFilter(val from: Instant? = null, val to: Instant? = null, val status: String? = null) {
    fun problem(): String? = when {
        from != null && to != null && from >= to -> "The end must be after the start."
        status != null && status !in setOf("COMPLETED", "CANCELLED") -> "History contains completed and cancelled tickets."
        else -> null
    }
}
enum class KdsLiveEvent { CONNECTED, CHANGED, DISCONNECTED }

fun KdsStation.toInput() = KdsStationInput(name, stationType, code, displayOrder, active, acceptsScheduledOrders, screenLabel, notes, deviceId,
    routings.map { KdsRoutingInput(it.menuItemId, it.displayOrder, it.priority, it.courseLabel, it.active) })
