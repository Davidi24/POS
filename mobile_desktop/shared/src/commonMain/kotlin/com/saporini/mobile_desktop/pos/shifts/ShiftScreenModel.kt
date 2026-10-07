package com.saporini.mobile_desktop.pos.shifts

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.time.Clock

internal data class ShiftState(
    val date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    val monthView: Boolean = false, val boardFrom: LocalDate? = null, val boardTo: LocalDate? = null,
    val board: ShiftBoard? = null, val loading: Boolean = true, val busy: Boolean = false,
    // My shift (POS): my pay for the week shown; null until loaded or when it couldn't be read.
    val pay: PayReport? = null,
    val error: String? = null, val notice: String? = null, val userId: String? = null,
    val permissions: Set<String> = emptySet(), val ready: Boolean = false
) {
    val zone get() = board?.timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.currentSystemDefault()
    val canManage get() = "SHIFT_MANAGE" in permissions
    val canSelf get() = "SHIFT_SELF" in permissions
    val canRead get() = "SHIFT_READ" in permissions || canManage
    val weekStart get() = date.minus(DatePeriod(days = date.dayOfWeek.ordinal))
}
internal class ShiftScreenModel(private val repository: ShiftRepository, private val session: SessionManager) : ScreenModel {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(ShiftState())
    val state = mutable.asStateFlow()
    private var admin = false
    private var restaurant: String? = null
    private var branch: String? = null
    private var generation = 0
    private var refreshJob: Job? = null
    private var observer: Job? = null
    fun start(management: Boolean) {
        if (observer != null) return
        admin = management
        observer = work.launch {
            session.currentUser.collectLatest { user ->
                generation++; refreshJob?.cancel()
                restaurant = user?.restaurantId; branch = user?.defaultBranchId
                mutable.value = ShiftState(userId = user?.id, permissions = user?.permissions.orEmpty().toSet(), ready = restaurant != null && branch != null)
                refresh()
            }
        }
    }
    fun date(date: LocalDate, monthView: Boolean = mutable.value.monthView) {
        if (mutable.value.busy) return
        val old = mutable.value
        val nextFrom = if (monthView) LocalDate(date.year, date.month, 1) else date.minus(DatePeriod(days = date.dayOfWeek.ordinal))
        val nextTo = if (monthView) nextFrom.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)) else nextFrom.plus(DatePeriod(days = weekSpan()))
        mutable.update { it.copy(date = date, monthView = monthView) }
        if (old.boardFrom != nextFrom || old.boardTo != nextTo || old.board == null) {
            generation++
            // Pay belongs to the selected date range too. Clear it before loading a new range so an API failure
            // cannot leave the previous week's wages and tips visible under the newly selected dates.
            mutable.update { it.copy(board = null, boardFrom = null, boardTo = null, pay = null) }
            refresh()
        }
    }
    // My shift also loads the following week, so the next shift shows even when it isn't this week.
    private fun weekSpan() = if (admin) 6 else 13
        fun clearError() { mutable.update { it.copy(error = null, notice = null) } }
    fun refresh() {
        if (mutable.value.busy) return
        refreshJob?.cancel()
        refreshJob = work.launch { load(generation) }
    }
    private suspend fun load(token: Int) {
        val r = restaurant; val b = branch
        if (r == null || b == null) { mutable.update { it.copy(loading = false, error = "Choose a restaurant and branch to view shifts.") }; return }
        val s = mutable.value
        if ((admin && !s.canRead) || (!admin && !s.canSelf)) { mutable.update { it.copy(loading = false, error = "You do not have access to this shift view. Sign in again if your permissions recently changed.") }; return }
        mutable.update { it.copy(loading = true) }
        try {
            val from = if (s.monthView) LocalDate(s.date.year, s.date.month, 1) else s.weekStart
            val to = if (s.monthView) from.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)) else from.plus(DatePeriod(days = weekSpan()))
            val board = repository.board(r, b, from.toString(), to.toString(), !admin)
            // My pay for the week on screen. Pay is extra: the shifts still show when it can't be read.
            val pay = if (admin) null else runCatching { repository.pay(r, b, from.toString(), from.plus(DatePeriod(days = 6)).toString(), mine = true) }
                .onFailure { if (it is CancellationException) throw it }.getOrNull()
            if (token == generation) mutable.update { it.copy(board = board, boardFrom = from, boardTo = to, loading = false, error = null, pay = pay) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (token == generation) mutable.update { it.copy(loading = false, error = friendly(e)) } }
    }
    private fun mutate(message: String, onSuccess: () -> Unit, action: suspend (String, String) -> Unit) {
        if (mutable.value.busy) return
        refreshJob?.cancel()
        val r = restaurant ?: return; val b = branch ?: return; val token = generation
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        work.launch {
            try {
                action(r, b)
                if (token == generation) { mutable.update { it.copy(notice = message) }; onSuccess(); load(token) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (token == generation) {
                    if (e is ApiException && e.status == 409) load(token)
                    mutable.update { it.copy(error = friendly(e)) }
                }
            } finally { if (token == generation) mutable.update { it.copy(busy = false) } }
        }
    }
    fun clockIn(id: String?, done: () -> Unit) = mutate("You are clocked in.", done) { r, b -> repository.clockIn(r, b, id) }
    fun action(item: ShiftItem, action: String, reason: String?, done: () -> Unit) = mutate("Shift updated.", done) { r, b -> repository.action(r, b, item.id, action, ShiftAction(item.version, reason)) }
    fun startBreak(item: ShiftItem, type: String, done: () -> Unit) = mutate("Your break has started.", done) { r, b -> repository.startBreak(r, b, item.id, ShiftBreakRequest(item.version, type)) }
    fun schedule(id: String?, request: ShiftSchedule, done: () -> Unit) = mutate("Shift saved.", done) { r, b -> repository.schedule(r, b, id, request) }
    fun correct(item: ShiftItem, request: ShiftCorrection, done: () -> Unit) = mutate("Attendance corrected. The change has been recorded.", done) { r, b -> repository.correct(r, b, item.id, request) }
    override fun onDispose() { work.cancel() }
    private fun friendly(e: Exception) = if (e is ApiException) e.message else "Couldn't connect to shifts. Check your connection and try again."
}
