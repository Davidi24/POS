package com.saporini.mobile_desktop.admin.settings

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mobile_desktop.shared.generated.resources.Res
import mobile_desktop.shared.generated.resources.overview_next_hours
import mobile_desktop.shared.generated.resources.overview_reservations
import mobile_desktop.shared.generated.resources.settings_notifications
import mobile_desktop.shared.generated.resources.settings_orders_kitchen
import mobile_desktop.shared.generated.resources.settings_payments
import mobile_desktop.shared.generated.resources.settings_tables
import mobile_desktop.shared.generated.resources.workspace_fraud
import mobile_desktop.shared.generated.resources.workspace_pos
import mobile_desktop.shared.generated.resources.workspace_restaurants
import org.jetbrains.compose.resources.DrawableResource

// ---- Where each value lives on the server ----

// One settings object on the server. A page loads every source its fields use, and saves only what changed.
internal enum class SettingsSource { GENERAL, ORDER_RULES, RECEIPT, RESERVATION_RULE }

// A group of fields sent together to one endpoint (the server expects all of them in the body).
internal class SaveGroup(val source: SettingsSource, val keys: List<String>, val save: suspend SettingsApi.(SettingsScope, JsonObject) -> JsonObject)

internal data class SettingsScope(val restaurantId: String, val branchId: String?)

internal suspend fun SettingsApi.loadSource(source: SettingsSource, scope: SettingsScope): JsonObject = when (source) {
    SettingsSource.GENERAL -> getObject(scope.restaurantId, "")
    SettingsSource.ORDER_RULES -> getObject(scope.restaurantId, "/order-rules")
    SettingsSource.RECEIPT -> getObject(scope.restaurantId, "/receipt")
    SettingsSource.RESERVATION_RULE -> {
        // The rule bookings use: active, for this branch or the whole restaurant, first by priority. None yet: the
        // agreed defaults, created on the first save.
        val rules = getArray(scope.restaurantId, "/reservation-rules").map { it.jsonObject }
        rules.filter { it.bool("active") == true }
            .filter { rule -> rule.text("branchId").let { it == null || it == scope.branchId } }
            .minByOrNull { it.int("priority") ?: Int.MAX_VALUE }
            ?: DefaultReservationRule
    }
}

private val DefaultReservationRule = buildJsonObject {
    put("id", JsonNull)
    put("ruleName", JsonPrimitive("Default"))
    put("priority", JsonPrimitive(100))
    put("active", JsonPrimitive(true))
    put("advanceBookingDays", JsonPrimitive(60))
    put("minPartySize", JsonPrimitive(1))
    put("maxPartySize", JsonPrimitive(20))
    put("defaultDurationMinutes", JsonPrimitive(120))
    put("bufferMinutes", JsonPrimitive(5))
    put("allowOnlineReservations", JsonPrimitive(true))
    put("requireDeposit", JsonPrimitive(false))
    put("depositType", JsonNull)
    put("depositValue", JsonNull)
    put("autoConfirmReservations", JsonPrimitive(true))
    put("cancellationWindowHours", JsonPrimitive(24))
}

private val ReservationRuleKeys = listOf(
    "branchId", "ruleName", "priority", "active", "effectiveFrom", "effectiveTo", "advanceBookingDays", "minPartySize",
    "maxPartySize", "defaultDurationMinutes", "bufferMinutes", "allowOnlineReservations", "requireDeposit", "depositType",
    "depositValue", "autoConfirmReservations", "cancellationWindowHours"
)

private fun patchGroup(path: String, vararg keys: String) =
    SaveGroup(SettingsSource.GENERAL, keys.toList()) { scope, body -> patch(scope.restaurantId, path, body) }

internal val SaveGroups: List<SaveGroup> = listOf(
    patchGroup(
        "/billing", "serviceChargeEnabled", "orderTaxRate", "orderTaxInclusive", "serviceChargeType", "serviceChargeValue",
        "cashRoundingEnabled", "cashRoundingIncrement", "allowSplitBills", "requireCustomerForInvoice"
    ),
    patchGroup("/order-channels", "allowOpenTickets", "enableQrOrdering", "enableTakeaway", "enableDelivery"),
    patchGroup("/pre-orders", "preOrdersEnabled", "preOrderLeadMinutes"),
    patchGroup("/sequence-prefixes", "orderSequencePrefix", "invoiceSequencePrefix"),
    patchGroup("/kitchen-status", "kitchenSlowAfterMinutes", "kitchenReadyWaitingMinutes"),
    patchGroup("/shifts", "clockInEarlyMinutes"),
    patchGroup("/payments", "tipsEnabled", "tipSuggestionsText", "maxTipPercent", "autoClosePaidOrders", "refundWindowDays"),
    patchGroup(
        "/fraud-checks", "fraudDiscountPercent", "fraudRefundAmount", "fraudVoidsPerDay", "fraudTipPercent",
        "fraudCashRefundsPerDay", "fraudDisabledRules"
    ),
    patchGroup(
        "/reservation-policy", "largeGroupFrom", "largeGroupExtraMinutes", "approvalGroupSize", "holdMinutes",
        "holdWarningMinutes", "checkInOpensMinutes", "confirmReminderTime", "sameDayConfirmMinutes",
        "attendanceCallMinutes", "reopenWindowMinutes", "undoSeatMinutes", "runningLateMaxMinutes", "lateAfterMinutes",
        "guestReminderHours", "noShowWarningFrom", "depositFromGuests", "cardFeePercent", "cardFeeFixed"
    ),
    SaveGroup(
        SettingsSource.ORDER_RULES,
        listOf(
            "autoFireToKitchen", "allowItemVoid", "allowDiscountWithoutManager", "allowBackdatedOrders", "requireReasonForVoid",
            "requireReasonForDiscount", "mergeOrdersEnabled", "transferOrdersEnabled", "reopenClosedOrdersEnabled"
        )
    ) { scope, body -> put(scope.restaurantId, "/order-rules", body) },
    SaveGroup(
        SettingsSource.RECEIPT,
        listOf(
            "autoPrintCustomerReceipt", "autoPrintKitchenTicket", "receiptCopies", "showLogo", "showTaxBreakdown",
            "showServerName", "showTableName", "showOrderNumber", "showQrCode", "printVoidedItems", "footerNote"
        )
    ) { scope, body -> put(scope.restaurantId, "/receipt", body) },
    SaveGroup(SettingsSource.RESERVATION_RULE, ReservationRuleKeys) { scope, body ->
        val id = body.text("id")
        // The deposit is a fixed amount per booking (the server still wants its kind).
        val fixed = if (body.bool("requireDeposit") == true) mapOf("depositType" to JsonPrimitive("FIXED_AMOUNT")) else emptyMap()
        val request = JsonObject(body.filterKeys { it in ReservationRuleKeys } + fixed)
        if (id == null) post(scope.restaurantId, "/reservation-rules", request)
        else put(scope.restaurantId, "/reservation-rules/$id", request)
    }
)

// ---- Fields ----

internal sealed interface SettingField {
    val source: SettingsSource
    val key: String
    val label: String
    val hint: String?
    // Shown only when this is true for the current values (e.g. the deposit amount only with a deposit on).
    val shownWhen: ((SettingsValues) -> Boolean)?
}

internal data class ToggleField(
    override val source: SettingsSource, override val key: String, override val label: String,
    override val hint: String? = null, override val shownWhen: ((SettingsValues) -> Boolean)? = null
) : SettingField

internal data class NumberField(
    override val source: SettingsSource, override val key: String, override val label: String,
    val unit: String, val min: Int, val max: Int, val step: Int = 1,
    override val hint: String? = null, override val shownWhen: ((SettingsValues) -> Boolean)? = null
) : SettingField

internal data class DecimalField(
    override val source: SettingsSource, override val key: String, override val label: String, val suffix: String,
    override val hint: String? = null, override val shownWhen: ((SettingsValues) -> Boolean)? = null
) : SettingField

internal data class TextSettingField(
    override val source: SettingsSource, override val key: String, override val label: String, val maxLength: Int,
    val placeholder: String = "", val multiline: Boolean = false,
    override val hint: String? = null, override val shownWhen: ((SettingsValues) -> Boolean)? = null
) : SettingField

internal data class ChoiceField(
    override val source: SettingsSource, override val key: String, override val label: String,
    val options: List<Pair<String, String>>,
    override val hint: String? = null, override val shownWhen: ((SettingsValues) -> Boolean)? = null
) : SettingField

// A time of day, "HH:mm".
internal data class TimeField(
    override val source: SettingsSource, override val key: String, override val label: String,
    override val hint: String? = null, override val shownWhen: ((SettingsValues) -> Boolean)? = null
) : SettingField

internal data class SettingsSection(val title: String, val fields: List<SettingField>, val note: String? = null)

// ---- Categories (the grid) ----

internal enum class SettingsCategory(val title: String, val description: String, val image: DrawableResource, val available: Boolean = true) {
    DEVICES("Devices & printing", "Printers, receipts and where each ticket prints", Res.drawable.workspace_pos),
    TABLES("Tables", "Joining and moving tables, QR ordering", Res.drawable.settings_tables),
    RESERVATIONS("Reservations", "Booking length, late guests, confirmations and groups", Res.drawable.overview_reservations),
    ORDERS("Orders & kitchen", "Kitchen, order changes, order types and pre-orders", Res.drawable.settings_orders_kitchen),
    PAYMENTS("Payments & receipts", "Tax, service charge, paying and what receipts show", Res.drawable.settings_payments),
    SHIFTS("Shifts", "Clock-in, breaks and schedules", Res.drawable.overview_next_hours),
    FRAUD("Fraud checks", "When discounts, refunds and removals are flagged for review", Res.drawable.workspace_fraud),
    NOTIFICATIONS("Notifications", "Who is told about what, and when", Res.drawable.settings_notifications),
    ONLINE_BOOKING("Online booking", "Your website booking page. Coming later", Res.drawable.workspace_restaurants, available = false)
}

private val G = SettingsSource.GENERAL
private val OR = SettingsSource.ORDER_RULES
private val RC = SettingsSource.RECEIPT
private val RR = SettingsSource.RESERVATION_RULE
private val AmountTypes = listOf("PERCENTAGE" to "Percentage", "FIXED_AMOUNT" to "Fixed amount")

// The sections of each category's page. Categories with none yet show a "to be decided" page.
internal fun SettingsCategory.sections(): List<SettingsSection> = when (this) {
    SettingsCategory.RESERVATIONS -> listOf(
        SettingsSection(
            "Booking length", listOf(
                NumberField(RR, "defaultDurationMinutes", "Booking length", "min", 30, 480, 15, "How long a table is booked for"),
                NumberField(G, "largeGroupFrom", "Longer bookings from", "guests", 1, 100, 1, "Groups this size or bigger get extra time"),
                NumberField(G, "largeGroupExtraMinutes", "Extra time for those groups", "min", 0, 240, 5),
                NumberField(RR, "bufferMinutes", "Cleaning time between bookings", "min", 0, 60, 5, "The table stays free this long after a booking ends")
            )
        ),
        SettingsSection(
            "Who can book", listOf(
                NumberField(RR, "minPartySize", "Smallest booking", "guests", 1, 50, 1),
                NumberField(RR, "maxPartySize", "Largest booking", "guests", 1, 200, 1),
                NumberField(RR, "advanceBookingDays", "Guests can book up to", "days ahead", 0, 365, 1),
                NumberField(G, "approvalGroupSize", "Staff approval from", "guests", 1, 200, 1, "Bigger bookings wait as a request until staff approve them")
            )
        ),
        SettingsSection(
            "Online bookings", listOf(
                ToggleField(RR, "allowOnlineReservations", "Allow online bookings", "From the website, once it's live"),
                ToggleField(RR, "autoConfirmReservations", "Confirm instantly when a table is free", "Otherwise they wait for staff as a request")
            )
        ),
        SettingsSection(
            "Arrival and late guests", listOf(
                NumberField(G, "checkInOpensMinutes", "Check-in opens", "min before", 0, 1440, 15, "\"Guest arrived\" can be pressed from then"),
                NumberField(G, "lateAfterMinutes", "Show a guest as late after", "min", 0, 240, 5, "After the booking time"),
                NumberField(G, "holdWarningMinutes", "Show \"Hold ends soon\" on the table after", "min", 0, 240, 5, "After the booking time; must be before the hold ends"),
                NumberField(G, "holdMinutes", "Hold the table for", "min", 5, 240, 5, "After the booking time. Then it becomes a no-show"),
                NumberField(G, "runningLateMaxMinutes", "\"I'm running late\" can add up to", "min", 0, 240, 5, "Extends the hold only; the booking still ends on time")
            )
        ),
        SettingsSection(
            "Confirming attendance", listOf(
                NumberField(G, "guestReminderHours", "Ask guests \"Still coming?\"", "h before", 1, 168, 1, "By email or text, with Confirm and Cancel"),
                TimeField(G, "confirmReminderTime", "Remind managers the day before at", "About bookings not confirmed yet"),
                NumberField(G, "sameDayConfirmMinutes", "Same-day bookings: confirm at least", "min before", 0, 1440, 15),
                NumberField(G, "attendanceCallMinutes", "No reply: ask staff to call", "min before", 0, 1440, 15, "Bookings are never cancelled for not replying")
            )
        ),
        SettingsSection(
            "No-shows", listOf(
                NumberField(G, "noShowWarningFrom", "Warn staff about a guest from", "no-shows", 1, 20, 1, "Their new bookings show a warning. A manager can clear it")
            )
        ),
        SettingsSection(
            "Corrections", listOf(
                NumberField(G, "reopenWindowMinutes", "Staff can reopen a cancelled or no-show booking within", "min", 0, 1440, 15, "After that, a manager with a reason"),
                NumberField(G, "undoSeatMinutes", "Staff can undo seating within", "min", 0, 240, 5, "If no orders or payments are on it; after that, a manager")
            )
        ),
        SettingsSection(
            "Deposit for big groups", listOf(
                ToggleField(RR, "requireDeposit", "Ask big groups for a deposit", "Off for most restaurants"),
                NumberField(G, "depositFromGuests", "Deposit from", "guests", 1, 200, 1, shownWhen = { it.bool(RR, "requireDeposit") }),
                // A fixed amount per booking: when a table is booked there's no bill yet to take a percentage of.
                DecimalField(RR, "depositValue", "Deposit per booking", "", "Paid when booking", shownWhen = { it.bool(RR, "requireDeposit") }),
                NumberField(RR, "cancellationWindowHours", "Refund the deposit if cancelled at least", "h before", 0, 720, 1, "Minus the card fee. Kept on a no-show", shownWhen = { it.bool(RR, "requireDeposit") })
            )
        ),
        SettingsSection(
            "Refunds", listOf(
                DecimalField(G, "cardFeePercent", "Card fee kept on a refund", "%", "What the card company charges; taken off refunds of deposits, extras and pre-orders"),
                DecimalField(G, "cardFeeFixed", "Plus per payment", "", "A fixed part of the card fee, e.g. 0.25")
            )
        )
    )
    SettingsCategory.ORDERS -> listOf(
        SettingsSection(
            "Kitchen", listOf(
                ToggleField(OR, "autoFireToKitchen", "Send items to the kitchen automatically", "Otherwise staff send them")
            )
        ),
        SettingsSection(
            "Kitchen Status for waiters", listOf(
                NumberField(G, "kitchenSlowAfterMinutes", "An order is taking long after", "min", 1, 240, 1, "It turns red on the waiters' Kitchen Status"),
                NumberField(G, "kitchenReadyWaitingMinutes", "Ready food is waiting too long after", "min", 1, 120, 1, "Waiters see it in red until they pick it up")
            )
        ),
        SettingsSection(
            "Changing orders", listOf(
                ToggleField(OR, "allowItemVoid", "Allow removing items from an order"),
                ToggleField(OR, "requireReasonForVoid", "Ask for a reason when removing", shownWhen = { it.bool(OR, "allowItemVoid") }),
                ToggleField(OR, "allowDiscountWithoutManager", "Allow discounts without a manager"),
                ToggleField(OR, "requireReasonForDiscount", "Ask for a reason for discounts"),
                ToggleField(OR, "reopenClosedOrdersEnabled", "Allow reopening closed orders"),
                ToggleField(OR, "allowBackdatedOrders", "Allow orders with an earlier date")
            )
        ),
        SettingsSection(
            "Order types", listOf(
                ToggleField(G, "enableTakeaway", "Takeaway"),
                ToggleField(G, "enableDelivery", "Delivery"),
                ToggleField(G, "allowOpenTickets", "Open tabs", "Orders not linked to a table")
            )
        ),
        SettingsSection(
            "Pre-orders with bookings", listOf(
                ToggleField(G, "preOrdersEnabled", "Guests can pre-order food when booking"),
                NumberField(G, "preOrderLeadMinutes", "Send pre-orders to the kitchen", "min before", 0, 240, 5, shownWhen = { it.bool(G, "preOrdersEnabled") })
            )
        ),
        SettingsSection(
            "Numbering", listOf(
                TextSettingField(G, "orderSequencePrefix", "Order number starts with", 20, "ORD")
            )
        )
    )
    SettingsCategory.TABLES -> listOf(
        SettingsSection(
            "Tables", listOf(
                ToggleField(OR, "mergeOrdersEnabled", "Allow joining tables", "Their guests and orders become one"),
                ToggleField(OR, "transferOrdersEnabled", "Allow moving guests to another table"),
                ToggleField(G, "enableQrOrdering", "QR ordering at the table", "Guests scan and order from their phone")
            )
        )
    )
    SettingsCategory.PAYMENTS -> listOf(
        SettingsSection(
            "Tax", listOf(
                DecimalField(G, "orderTaxRate", "Tax rate", "%"),
                ToggleField(G, "orderTaxInclusive", "Menu prices already include tax")
            )
        ),
        SettingsSection(
            "Service charge", listOf(
                ToggleField(G, "serviceChargeEnabled", "Add a service charge"),
                ChoiceField(G, "serviceChargeType", "Service charge", AmountTypes, shownWhen = { it.bool(G, "serviceChargeEnabled") }),
                DecimalField(G, "serviceChargeValue", "Amount", "", shownWhen = { it.bool(G, "serviceChargeEnabled") })
            )
        ),
        SettingsSection(
            "Paying", listOf(
                ToggleField(G, "allowSplitBills", "Allow split bills"),
                ToggleField(G, "cashRoundingEnabled", "Round cash payments"),
                DecimalField(G, "cashRoundingIncrement", "Round to the nearest", "", shownWhen = { it.bool(G, "cashRoundingEnabled") }),
                ToggleField(G, "requireCustomerForInvoice", "Invoices need the customer's details"),
                TextSettingField(G, "invoiceSequencePrefix", "Invoice number starts with", 20, "INV")
            )
        ),
        SettingsSection(
            "Tips", listOf(
                ToggleField(G, "tipsEnabled", "Guests can leave a tip"),
                TextSettingField(G, "tipSuggestionsText", "Tip buttons", 23, "5,10,15",
                    hint = "Percents of the bill, separated by commas (up to 6)", shownWhen = { it.bool(G, "tipsEnabled") }),
                NumberField(G, "maxTipPercent", "Biggest tip allowed", "% of the payment", 1, 200, 5,
                    "A bigger tip is refused, so a typo can't charge a guest too much", shownWhen = { it.bool(G, "tipsEnabled") })
            )
        ),
        SettingsSection(
            "After paying", listOf(
                ToggleField(G, "autoClosePaidOrders", "Close the order once it's paid", "Otherwise staff close it themselves"),
                NumberField(G, "refundWindowDays", "Refunds possible for", "days", 0, 365, 1, "After the payment. 0 means no limit")
            )
        ),
        SettingsSection(
            "Receipts", listOf(
                ToggleField(RC, "autoPrintCustomerReceipt", "Print the receipt automatically"),
                ToggleField(RC, "autoPrintKitchenTicket", "Print kitchen tickets automatically"),
                NumberField(RC, "receiptCopies", "Copies", "", 1, 5, 1),
                ToggleField(RC, "showLogo", "Show the logo"),
                ToggleField(RC, "showTaxBreakdown", "Show the tax breakdown"),
                ToggleField(RC, "showServerName", "Show the waiter's name"),
                ToggleField(RC, "showTableName", "Show the table"),
                ToggleField(RC, "showOrderNumber", "Show the order number"),
                ToggleField(RC, "showQrCode", "Show a QR code"),
                ToggleField(RC, "printVoidedItems", "Print removed items"),
                TextSettingField(RC, "footerNote", "Message at the bottom", 1000, "Thank you for your visit!", multiline = true)
            )
        )
    )
    SettingsCategory.SHIFTS -> listOf(
        SettingsSection(
            "Clocking in", listOf(
                NumberField(G, "clockInEarlyMinutes", "Staff can clock in for a shift from", "min before", 0, 720, 15, "Before that, clocking in starts a shift that isn't planned")
            ),
            note = "Hourly wages are set per person in Shifts → Hours & pay."
        )
    )
    SettingsCategory.FRAUD -> listOf(
        SettingsSection(
            "Flag for review", listOf(
                NumberField(G, "fraudDiscountPercent", "Discounts from", "% of the bill", 1, 100, 5),
                DecimalField(G, "fraudRefundAmount", "Refunds from", "", "Any single refund this big or bigger"),
                NumberField(G, "fraudVoidsPerDay", "Removals by one person from", "a day", 1, 1000, 1, "Items or whole orders removed"),
                NumberField(G, "fraudCashRefundsPerDay", "Cash refunds by one person from", "a day", 1, 1000, 1),
                NumberField(G, "fraudTipPercent", "Tips from", "% of the payment", 1, 500, 5)
            ),
            note = "Each check can be switched off in Fraud Detection → Rules."
        )
    )
    SettingsCategory.DEVICES, SettingsCategory.NOTIFICATIONS, SettingsCategory.ONLINE_BOOKING -> emptyList()
}

// What must be fixed before a page can be saved, or null when it's fine.
internal fun SettingsCategory.problem(values: SettingsValues): String? {
    fun number(source: SettingsSource, key: String) = values.text(source, key)?.toDoubleOrNull()
    return when (this) {
        SettingsCategory.RESERVATIONS -> {
            val smallest = number(RR, "minPartySize")
            val largest = number(RR, "maxPartySize")
            val hold = number(G, "holdMinutes")
            val warning = number(G, "holdWarningMinutes")
            val late = number(G, "lateAfterMinutes")
            val deposit = number(RR, "depositValue")
            val feePercent = number(G, "cardFeePercent")
            val feeFixed = number(G, "cardFeeFixed")
            when {
                smallest != null && largest != null && smallest > largest -> "The smallest booking can't be bigger than the largest."
                hold != null && warning != null && warning >= hold -> "\"Hold ends soon\" must show before the hold ends."
                hold != null && late != null && late >= hold -> "A guest must show as late before the hold ends."
                values.bool(RR, "requireDeposit") && (deposit == null || deposit <= 0.0) -> "Enter the deposit amount."
                feePercent != null && (feePercent < 0.0 || feePercent > 20.0) -> "The card fee must be between 0 and 20%."
                feeFixed != null && (feeFixed < 0.0 || feeFixed > 10.0) -> "The fixed card fee must be between 0 and 10."
                else -> null
            }
        }
        SettingsCategory.PAYMENTS -> {
            val maxTip = number(G, "maxTipPercent")
            val text = values.text(G, "tipSuggestionsText").orEmpty().trim()
            val tips = if (text.isEmpty()) emptyList() else text.split(',').map { it.trim().toIntOrNull() }
            when {
                values.bool(G, "tipsEnabled") && (tips.any { it == null } || tips.size > 6) ->
                    "Tip buttons are up to 6 whole percents, like 5,10,15."
                values.bool(G, "tipsEnabled") && tips.any { it != null && (it < 1 || it > 100) } -> "A tip button must be between 1 and 100%."
                values.bool(G, "tipsEnabled") && maxTip != null && tips.any { it != null && it > maxTip } ->
                    "A tip button can't be above the biggest tip allowed."
                else -> null
            }
        }
        SettingsCategory.FRAUD -> {
            val refund = number(G, "fraudRefundAmount")
            if (refund != null && (refund < 0.0 || refund > 1_000_000.0)) "The refund amount must be between 0 and 1,000,000." else null
        }
        else -> null
    }
}

// ---- Values being edited ----

// The loaded objects per source, plus the user's edits on top.
internal data class SettingsValues(val loaded: Map<SettingsSource, JsonObject>, val edits: Map<SettingsSource, Map<String, kotlinx.serialization.json.JsonElement>>) {
    fun current(source: SettingsSource): JsonObject? = loaded[source]?.let { JsonObject(it + edits[source].orEmpty()) }
    fun value(source: SettingsSource, key: String) = current(source)?.get(key)
    fun bool(source: SettingsSource, key: String) = (value(source, key) as? JsonPrimitive)?.booleanOrNull == true
    fun text(source: SettingsSource, key: String) = (value(source, key) as? JsonPrimitive)?.contentOrNull
    fun set(source: SettingsSource, key: String, element: kotlinx.serialization.json.JsonElement): SettingsValues {
        val original = loaded[source]?.get(key)
        val sourceEdits = edits[source].orEmpty().toMutableMap()
        if (original == element) sourceEdits.remove(key) else sourceEdits[key] = element
        return copy(edits = edits + (source to sourceEdits))
    }
    val changed: Boolean get() = edits.values.any { it.isNotEmpty() }
}

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
