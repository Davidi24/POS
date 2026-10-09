package com.saporini.mobile_desktop.pos.reservations.preorder

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class PreOrderState(
    val restaurantId: String? = null,
    val branchId: String? = null,
    val canRead: Boolean = false,
    val canPlace: Boolean = false,
    val canCancel: Boolean = false,
    val canSend: Boolean = false,
    // One booking's pre-order.
    val reservationId: String? = null,
    val preOrder: PreOrderDto? = null,
    val editing: Boolean = false,
    val lines: List<PreOrderLine> = emptyList(),
    val notes: String = "",
    val confirmCancel: Boolean = false,
    val cancelReason: String = "",
    // The kitchen's list of upcoming pre-orders.
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val status: String? = "SCHEDULED",
    val upcoming: List<PreOrderDto> = emptyList(),
    val loading: Boolean = false,
    val loadingList: Boolean = false,
    val saving: Boolean = false,
    val notice: String? = null,
    val error: String? = null
) {
    val dishCount: Int get() = lines.sumOf { it.quantity }
}

/**
 * A booking's food pre-order (look, take or change it for the guest, cancel it, send it to the kitchen early) and the
 * branch's list of upcoming pre-orders. Prices always come from the server.
 */
class PreOrderScreenModel(
    private val repository: PreOrderRepository,
    session: SessionManager,
    private val today: () -> LocalDate = { kotlin.time.Clock.System.todayIn(RestaurantTime.zone) },
    private val zone: () -> TimeZone = { RestaurantTime.zone }
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(PreOrderState())
    val state: StateFlow<PreOrderState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var bookingRevision = 0L
    private var listRevision = 0L
    private var signIn = 0L
    private var listJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                bookingRevision++
                listRevision++
                signIn++
                listJob?.cancel()
                val permissions = user?.takeIf { it.isActive }?.permissions.orEmpty().toSet()
                val start = today()
                mutable.value = PreOrderState(
                    restaurantId = user?.takeIf { it.isActive }?.restaurantId,
                    branchId = user?.defaultBranchId,
                    canRead = "ORDER_READ" in permissions,
                    canPlace = "ORDER_CREATE" in permissions,
                    canCancel = "ORDER_CANCEL" in permissions,
                    canSend = "ORDER_UPDATE" in permissions,
                    from = start,
                    to = start.plus(DatePeriod(days = 6))
                )
            }
        }
    }

    // ---- One booking ----

    /** Shows [reservationId]'s pre-order (or none), dropping any unsaved changes for another booking. */
    fun open(reservationId: String?) {
        val token = ++bookingRevision
        mutable.update {
            it.copy(reservationId = reservationId, preOrder = null, editing = false, lines = emptyList(), notes = "", confirmCancel = false,
                cancelReason = "", error = null, notice = null)
        }
        if (reservationId != null) loadBooking(token)
    }

    fun refreshBooking() = loadBooking(bookingRevision)

    private fun loadBooking(token: Long) {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val reservationId = current.reservationId ?: return
        if (!current.canRead) return
        mutable.update { it.copy(loading = true) }
        work.launch {
            try {
                val preOrder = repository.forBooking(restaurantId, reservationId)
                if (token == bookingRevision) mutable.update { it.copy(preOrder = preOrder) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == bookingRevision) mutable.update { it.copy(error = message(e, write = false)) }
            } finally {
                if (token == bookingRevision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    /** Starts taking (or changing) the pre-order with the guest. */
    fun startEdit() {
        val current = state.value
        if (current.reservationId == null) return
        if (!current.canPlace) {
            mutable.update { it.copy(error = "You can't take pre-orders") }
            return
        }
        val preOrder = current.preOrder
        if (preOrder != null && preOrder.open.not() && preOrder.status != "CANCELLED") {
            mutable.update { it.copy(error = "This pre-order is already with the kitchen and can't change") }
            return
        }
        val keep = preOrder?.takeIf { it.open }
        mutable.update { it.copy(editing = true, lines = keep?.let(::linesOf).orEmpty(), notes = keep?.notes.orEmpty(), notice = null, error = null) }
    }

    /** Adds a dish; the same dish with the same choices and no note just counts one more. */
    fun addDish(menuItemId: String, name: String, variantId: String? = null, options: List<PreOrderOptionRequestDto> = emptyList()) =
        mutable.update { state ->
            if (!state.editing) return@update state
            val lines = state.lines
            val same = lines.indexOfFirst { it.menuItemId == menuItemId && it.variantId == variantId && it.options == options && it.notes.isBlank() }
            when {
                same >= 0 -> state.copy(lines = lines.mapIndexed { i, line ->
                    if (i == same) line.copy(quantity = (line.quantity + 1).coerceAtMost(MAX_DISH_QUANTITY)) else line
                })
                lines.size >= MAX_PRE_ORDER_DISHES -> state.copy(error = "At most $MAX_PRE_ORDER_DISHES dishes")
                else -> state.copy(lines = lines + PreOrderLine(menuItemId, name, variantId, options = options.take(MAX_DISH_OPTIONS)))
            }
        }

    /** Sets how many of a dish; zero takes it off. */
    fun quantity(index: Int, quantity: Int) = edit { lines ->
        if (index !in lines.indices) lines
        else if (quantity <= 0) lines.filterIndexed { i, _ -> i != index }
        else lines.mapIndexed { i, line -> if (i == index) line.copy(quantity = quantity.coerceAtMost(MAX_DISH_QUANTITY)) else line }
    }

    fun dishNotes(index: Int, text: String) = edit { lines ->
        lines.mapIndexed { i, line -> if (i == index) line.copy(notes = text.take(MAX_DISH_NOTES + 20)) else line }
    }

    fun notes(text: String) = mutable.update { if (it.editing) it.copy(notes = text.take(MAX_PRE_ORDER_NOTES + 20)) else it }

    private fun edit(change: (List<PreOrderLine>) -> List<PreOrderLine>) = mutable.update { state ->
        if (!state.editing) state else state.copy(lines = change(state.lines))
    }

    fun cancelEdit() = mutable.update { it.copy(editing = false, lines = emptyList(), notes = "") }

    fun save() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val reservationId = current.reservationId ?: return
        if (!current.editing) return
        preOrderProblem(current.lines, current.notes)?.let { problem ->
            mutable.update { it.copy(error = problem) }
            return
        }
        val request = preOrderRequest(current.lines, current.notes)
        write(if (current.preOrder?.open == true) "Pre-order changed" else "Pre-order taken") {
            val saved = repository.save(restaurantId, reservationId, request)
            mutable.update { if (it.reservationId == reservationId) it.copy(preOrder = saved, editing = false, lines = emptyList(), notes = "") else it }
            replaceInList(saved)
        }
    }

    fun askCancel() {
        val current = state.value
        if (current.preOrder?.open != true) return
        if (!current.canCancel) {
            mutable.update { it.copy(error = "Only a manager can cancel a pre-order") }
            return
        }
        mutable.update { it.copy(confirmCancel = true, cancelReason = "") }
    }

    fun cancelReason(text: String) = mutable.update { it.copy(cancelReason = text.take(MAX_PRE_ORDER_NOTES + 20)) }

    fun dismissCancel() = mutable.update { it.copy(confirmCancel = false) }

    fun confirmCancel() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val reservationId = current.reservationId ?: return
        if (!current.confirmCancel) return
        val reason = current.cancelReason.trim()
        if (reason.length > MAX_PRE_ORDER_NOTES) {
            mutable.update { it.copy(error = "The reason can be at most $MAX_PRE_ORDER_NOTES characters") }
            return
        }
        write("Pre-order cancelled; the guest gets their money back") {
            val saved = repository.cancel(restaurantId, reservationId, reason.ifEmpty { null })
            mutable.update { if (it.reservationId == reservationId) it.copy(preOrder = saved, confirmCancel = false) else it }
            replaceInList(saved)
        }
    }

    /** Sends the pre-order to the kitchen now instead of at its set time (e.g. the guests came early). */
    fun sendNow() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val reservationId = current.reservationId ?: return
        if (current.preOrder?.open != true) return
        if (!current.canSend) {
            mutable.update { it.copy(error = "You can't send pre-orders to the kitchen") }
            return
        }
        write("Sent to the kitchen") {
            val saved = repository.sendNow(restaurantId, reservationId)
            mutable.update { if (it.reservationId == reservationId) it.copy(preOrder = saved) else it }
            replaceInList(saved)
        }
    }

    // ---- The branch's upcoming pre-orders ----

    fun period(from: LocalDate, to: LocalDate): Boolean {
        val problem = when {
            to < from -> "The end can't be before the start"
            from.daysUntil(to) + 1 > MAX_PRE_ORDER_LIST_DAYS -> "Choose at most $MAX_PRE_ORDER_LIST_DAYS days"
            else -> null
        }
        if (problem != null) {
            mutable.update { it.copy(error = problem) }
            return false
        }
        mutable.update { it.copy(from = from, to = to, upcoming = emptyList()) }
        loadList()
        return true
    }

    fun statusFilter(status: String?) {
        if (status != null && status !in PRE_ORDER_STATUSES) return
        mutable.update { it.copy(status = status, upcoming = emptyList()) }
        loadList()
    }

    fun loadList() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val branchId = current.branchId ?: return
        val from = current.from ?: return
        val to = current.to ?: return
        if (!current.canRead) return
        val token = ++listRevision
        listJob?.cancel()
        listJob = work.launch {
            mutable.update { it.copy(loadingList = true) }
            try {
                // Whole restaurant days: from the start of the first to the start of the day after the last.
                val list = repository.forBranch(restaurantId, branchId, from.atStartOfDayIn(zone()).toString(),
                    to.plus(DatePeriod(days = 1)).atStartOfDayIn(zone()).toString(), current.status)
                if (token == listRevision) mutable.update { it.copy(upcoming = list.sortedBy { p -> p.reservationStart.orEmpty() }, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == listRevision) mutable.update { it.copy(error = message(e, write = false)) }
            } finally {
                if (token == listRevision) mutable.update { it.copy(loadingList = false) }
            }
        }
    }

    private fun replaceInList(saved: PreOrderDto) = mutable.update { state ->
        val matches = state.status == null || saved.status == state.status
        val list = state.upcoming.filterNot { it.id == saved.id }
        state.copy(upcoming = if (matches && state.upcoming.any { it.id == saved.id }) state.upcoming.map { if (it.id == saved.id) saved else it } else list)
    }

    // ---- Plumbing ----

    private fun write(notice: String, action: suspend () -> Unit) {
        if (state.value.saving) return
        val token = signIn
        mutable.update { it.copy(saving = true, error = null, notice = null) }
        work.launch {
            writes.withLock {
                try {
                    if (token != signIn) return@withLock
                    action()
                    if (token == signIn) mutable.update { it.copy(notice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != signIn) return@withLock
                    mutable.update { it.copy(error = message(e, write = true)) }
                    // The pre-order may have moved on (sent, cancelled) on another device.
                    if (e is ApiException && e.status in setOf(400, 404, 409)) loadBooking(bookingRevision)
                } finally {
                    mutable.update { it.copy(saving = false) }
                }
            }
        }
    }

    fun clearMessages() = mutable.update { it.copy(error = null, notice = null) }

    override fun onDispose() {
        bookingRevision++
        listRevision++
        work.cancel()
    }

    private fun message(e: Exception, write: Boolean): String = when {
        e is ApiException && e.status == 401 -> "Sign in again to continue."
        e is ApiException && e.status == 403 -> "You don't have permission to do this."
        e is ApiException && e.status in 400..499 -> e.message.ifBlank { "This can't be done." }
        e is ApiException -> if (write) "The server had a problem. Refresh to see whether the change was saved." else "The server had a problem. Try again."
        write -> "No answer from the server. Refresh to see whether the change was saved."
        else -> "Could not load. Check the connection and try again."
    }
}
