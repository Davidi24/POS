package com.saporini.mobile_desktop.kds.model

import com.saporini.mobile_desktop.kds.data.KdsMenuEntry
import com.saporini.mobile_desktop.kds.ui.KdsSection
import kotlin.time.Instant

enum class KdsConnection { INACTIVE, CONNECTING, LIVE, RECONNECTING }
enum class KdsErrorKind { AUTHENTICATION, PERMISSION, NOT_FOUND, VALIDATION, CONFLICT, CONNECTION, SERVER }
data class KdsFailure(val kind: KdsErrorKind, val message: String, val uncertain: Boolean = false)
data class KdsTicketFilter(val query: String = "", val status: String? = null, val priority: String? = null, val course: String? = null)
data class KdsHistoryState(
    val filter: KdsHistoryFilter = KdsHistoryFilter(), val items: List<KdsTicket> = emptyList(),
    val loadedPages: Int = 0, val hasNext: Boolean = false, val totalElements: Long = 0,
    val loading: Boolean = false, val error: KdsFailure? = null
)
data class KdsMenuState(
    val items: List<KdsMenuEntry> = emptyList(), val loaded: Boolean = false,
    val query: String = "", val available: Boolean? = null, val menuId: String? = null,
    val loading: Boolean = false, val error: KdsFailure? = null
) {
    val visibleItems get() = items.filter { entry ->
        (available == null || entry.item.available == available) && (menuId == null || entry.menuId == menuId) &&
            (query.isBlank() || "${entry.item.name} ${entry.menuName} ${entry.sectionName}".contains(query.trim(), true))
    }
}
data class KdsAllDayItem(val menuItemId: String?, val name: String, val variant: String?, val modifiers: List<KdsModifier>,
    val notes: String?, val optionsPerUnit: Boolean, val pending: Int, val preparing: Int, val ready: Int)
data class KdsState(
    val now: Instant = kotlin.time.Clock.System.now(), val scope: KdsScope? = null, val permissions: Set<String> = emptySet(), val accessDenied: Boolean = false,
    val section: KdsSection = KdsSection.TICKETS, val stationId: String? = null, val deviceId: String? = null,
    val boards: List<KdsBoard> = emptyList(), val loaded: Boolean = false, val loading: Boolean = false,
    val stale: Boolean = true, val lastRefreshedAt: Instant? = null, val connection: KdsConnection = KdsConnection.INACTIVE,
    val filter: KdsTicketFilter = KdsTicketFilter(), val error: KdsFailure? = null, val actionError: KdsFailure? = null,
    val notice: String? = null, val reconciliationKey: String? = null, val busyKeys: Set<String> = emptySet(), val needsReconciliation: Boolean = false,
    val selectedTicketId: String? = null, val selectedTicket: KdsTicket? = null,
    val relatedTickets: List<KdsTicket> = emptyList(), val detailLoading: Boolean = false, val detailError: KdsFailure? = null,
    val history: KdsHistoryState = KdsHistoryState(), val menu: KdsMenuState = KdsMenuState(),
    val stations: List<KdsStation> = emptyList(), val devices: List<KdsDevice> = emptyList(),
    val stationLoading: Boolean = false, val stationError: KdsFailure? = null,
    val posTiming: KdsPosTiming = KdsPosTiming()
) {
    val canRead get() = scope != null && "KDS_READ" in permissions && !accessDenied
    val canUpdate get() = canRead && "KDS_UPDATE" in permissions
    // Waiters (order staff) can say the ready food was taken out, as well as kitchen staff.
    val canPickUp get() = canRead && ("KDS_UPDATE" in permissions || "ORDER_UPDATE" in permissions)
    val canReadMenu get() = scope != null && "MENUS_READ" in permissions
    val canUpdateMenu get() = canReadMenu && "MENUS_UPDATE" in permissions
    val canConfigure get() = scope != null && "SETTINGS_UPDATE" in permissions
    val needsStationSetup get() = loaded && boards.isEmpty() && stationId == null && deviceId == null
    val tickets get() = boards.flatMap { it.tickets }.distinctBy { it.id }
    private fun matches(ticket: KdsTicket): Boolean {
        val q = filter.query.trim()
        return (filter.status == null || ticket.status == filter.status) &&
            (filter.priority == null || ticket.priority == filter.priority) && (filter.course == null || ticket.courseName == filter.course) &&
            (q.isEmpty() || listOfNotNull(ticket.ticketNumber, ticket.orderNumber, ticket.tableName, ticket.tableNumber, ticket.customerName,
                ticket.notes, ticket.courseName, ticket.items.joinToString(" ") { "${it.itemNameSnapshot} ${it.variantNameSnapshot.orEmpty()} ${it.notes.orEmpty()} ${it.modifiers.joinToString { m -> m.name }}" }).any { it.contains(q, true) })
    }
    val visibleTickets get() = tickets.filter { !it.terminal && !it.waitingForFire(now) && matches(it) }.sortedWith(
        compareBy<KdsTicket> { when (it.priority) { "RUSH" -> 0; "VIP" -> 1; else -> 2 } }
            .thenBy { it.firedAt.asInstant() ?: it.createdAt.asInstant() ?: Instant.DISTANT_FUTURE }.thenBy { it.id })
    val upcomingTickets get() = tickets.filter { !it.terminal && (it.waitingForFire(now) || it.dueAt.asInstant()?.let { due -> due > now } == true) && matches(it) }.sortedWith(
        compareBy<KdsTicket> { it.dueAt.asInstant() ?: Instant.DISTANT_FUTURE }.thenBy { it.createdAt.asInstant() }.thenBy { it.id })
    val allDay get(): List<KdsAllDayItem> {
        data class Key(val id: String?, val name: String, val variant: String?, val modifiers: List<KdsModifier>, val notes: String?, val perUnit: Boolean)
        return visibleTickets.flatMap { it.items }.filter { it.status in setOf("PENDING", "FIRED", "IN_PROGRESS", "READY", "EXPO_READY") }
            .groupBy { Key(it.menuItemId, it.itemNameSnapshot, it.variantNameSnapshot, it.modifiers, it.notes, it.optionsPerUnit) }
            .map { (key, items) -> KdsAllDayItem(key.id, key.name, key.variant, key.modifiers, key.notes, key.perUnit,
                items.filter { it.status in setOf("PENDING", "FIRED") }.sumOf { it.quantity },
                items.filter { it.status == "IN_PROGRESS" }.sumOf { it.quantity },
                items.filter { it.status in setOf("READY", "EXPO_READY") }.sumOf { it.quantity }) }
            .sortedWith(compareBy<KdsAllDayItem> { it.name.lowercase() }.thenBy { it.variant })
    }
}
internal fun String?.asInstant(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
