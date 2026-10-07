@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.saporini.mobile_desktop.pos.payment

import cafe.adriel.voyager.core.model.ScreenModel
import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.pos.payment.data.OrderPaymentSummaryDto
import com.saporini.mobile_desktop.pos.payment.data.PaymentDto
import com.saporini.mobile_desktop.pos.payment.data.PaymentRepository
import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import com.saporini.mobile_desktop.pos.payment.data.RefundPaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.data.TakePaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.data.VoidPaymentRequestDto
import com.saporini.mobile_desktop.pos.reservations.domain.model.moneyCents
import com.saporini.mobile_desktop.pos.shifts.centsToDecimal
import com.saporini.mobile_desktop.pos.shifts.toCents
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
import kotlinx.datetime.toLocalDateTime

/** Why the last action failed, phrased for the till. [mayHaveWorked] means: check before trying again. */
data class PaymentFailure(val message: String, val kind: Kind, val mayHaveWorked: Boolean = false) {
    enum class Kind { VALIDATION, PERMISSION, NOT_FOUND, CONFLICT, CONNECTION, SERVER, SESSION }
}

/** A refund or cancel being written for one payment. */
data class PaymentCorrection(
    val paymentId: String,
    val cancel: Boolean,
    val amountText: String = "",
    val reason: String = ""
)

data class PaymentUiState(
    val restaurantId: String? = null,
    val userId: String? = null,
    val permissions: Set<String> = emptySet(),
    val orderId: String? = null,
    val summary: OrderPaymentSummaryDto? = null,
    val draft: PaymentDraft = PaymentDraft(),
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val error: PaymentFailure? = null,
    // The key of an attempt whose answer never arrived: trying again reuses it, so the server can't charge twice.
    val pendingKey: String? = null,
    val lastPayment: PaymentDto? = null,
    val completed: Boolean = false,
    val correction: PaymentCorrection? = null,
    val receipt: ReceiptDto? = null,
    val loadingReceipt: Boolean = false
) {
    val canTake: Boolean get() = "ORDER_CLOSE" in permissions
    val canRefund: Boolean get() = "PAYMENT_REFUND" in permissions
    val canCancel: Boolean get() = "ORDER_VOID" in permissions
    val quote: PaymentQuote? get() = summary?.let { quote(it, draft) }
    val tipChoices: List<Int> get() = summary?.takeIf { it.tipsEnabled }?.tipSuggestions.orEmpty()
    val quickCash: List<Long> get() = quote?.takeIf { draft.method == PayMethod.CASH }?.let { quickCashTenders(it.amountCents ?: 0) }.orEmpty()
    val canSubmit: Boolean get() = canTake && !submitting && !loading && summary != null && quote?.canSubmit == true
}

/**
 * The payment screen's state for one order: what is still owed, the payment being entered (method, amount, tip,
 * cash handed over), taking it, and a manager's refunds and cancels. The server stays the source of truth; every
 * answer replaces the bill shown.
 */
class PaymentScreenModel(
    private val repository: PaymentRepository,
    session: SessionManager,
    private val newKey: () -> String = { kotlin.uuid.Uuid.random().toString() },
    private val today: (String?) -> Boolean = { paidAt -> paidAt != null && isToday(paidAt) }
) : ScreenModel {

    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(PaymentUiState())
    val state: StateFlow<PaymentUiState> = mutable.asStateFlow()
    private val writes = Mutex()
    private var revision = 0L
    private var loadJob: Job? = null

    init {
        work.launch {
            session.currentUser.collectLatest { user ->
                revision++
                val restaurantId = user?.takeIf { it.isActive }?.restaurantId
                mutable.value = PaymentUiState(
                    restaurantId = restaurantId,
                    userId = user?.id,
                    permissions = user?.permissions.orEmpty().toSet()
                )
            }
        }
    }

    /** Opens the bill of [orderId] (or clears the screen with null). */
    fun open(orderId: String?) {
        revision++
        loadJob?.cancel()
        mutable.update {
            PaymentUiState(restaurantId = it.restaurantId, userId = it.userId, permissions = it.permissions, orderId = orderId)
        }
        if (orderId != null) refresh()
    }

    fun refresh() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val orderId = current.orderId ?: return
        val token = revision
        loadJob?.cancel()
        loadJob = work.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val summary = repository.summary(restaurantId, orderId)
                if (token != revision) return@launch
                require(summary.orderId == orderId) { "The server answered for another order" }
                // A payment whose answer never arrived keeps its warning: the fresh bill shows whether it went through.
                // Keep explicit server refusals visible (for example, card payments are disabled until a provider
                // is configured). Refreshing the bill must not erase the reason the payment was rejected.
                mutable.update {
                    it.copy(summary = summary,
                        error = it.error?.takeIf { e -> e.mayHaveWorked || e.kind == PaymentFailure.Kind.CONFLICT },
                        completed = isSettled(summary))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = failure(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loading = false) }
            }
        }
    }

    // ---- Entering the payment ----

    fun method(method: PayMethod) = edit {
        // Typed amounts stay; only the suggestion follows the method (cash may be rounded).
        it.copy(method = method, tenderedText = if (method == PayMethod.CASH) it.tenderedText else "",
            cardLast4 = if (method.card) it.cardLast4 else "")
    }

    fun amount(text: String) = edit { it.copy(amountText = text.take(16)) }

    fun tipPercent(percent: Int?) = edit { it.copy(tipPercent = percent, tipText = "") }

    fun tipAmount(text: String) = edit { it.copy(tipText = text.take(16), tipPercent = null) }

    fun tendered(text: String) = edit { it.copy(tenderedText = text.take(16)) }

    fun tenderedCents(cents: Long) = edit { it.copy(tenderedText = centsToDecimal(cents).value) }

    fun cardLast4(text: String) = edit { it.copy(cardLast4 = text.filter(Char::isDigit).take(4)) }

    fun notes(text: String) = edit { it.copy(notes = text.take(MAX_NOTES)) }

    fun closeWhenPaid(close: Boolean) = edit { it.copy(closeWhenPaid = close) }

    /** Pays an equal share of what is left: [people] guests splitting the bill. */
    fun splitEvenly(people: Int) {
        val summary = state.value.summary ?: return
        if (people < 1) return
        val due = suggestedAmountCents(summary, state.value.draft.method)
        val share = (due + people - 1) / people
        edit { it.copy(amountText = centsToDecimal(share).value) }
    }

    private fun edit(change: (PaymentDraft) -> PaymentDraft) {
        mutable.update {
            // A new entry is a new attempt, so it gets a new request key.
            it.copy(draft = change(it.draft), error = it.error?.takeIf { failure -> failure.kind != PaymentFailure.Kind.VALIDATION },
                pendingKey = null)
        }
    }

    fun take() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val orderId = current.orderId ?: return
        val summary = current.summary ?: return
        if (!current.canTake) {
            mutable.update { it.copy(error = PaymentFailure("You can't take payments", PaymentFailure.Kind.PERMISSION)) }
            return
        }
        val quote = quote(summary, current.draft)
        if (quote.problem != null) {
            mutable.update { it.copy(error = PaymentFailure(quote.problem, PaymentFailure.Kind.VALIDATION)) }
            return
        }
        val draft = current.draft
        val request = TakePaymentRequestDto(
            method = draft.method.api,
            amount = centsToDecimal(quote.amountCents ?: 0),
            tipAmount = quote.tipCents?.takeIf { it > 0 }?.let(::centsToDecimal),
            tenderedAmount = quote.tenderedCents?.let(::centsToDecimal),
            cardLast4 = draft.cardLast4.trim().takeIf { it.isNotEmpty() },
            notes = draft.notes.trim().takeIf { it.isNotEmpty() },
            closeOrderWhenPaid = draft.closeWhenPaid
        )
        if (current.submitting) return
        val key = current.pendingKey ?: newKey()
        val token = revision
        // Marked busy before anything runs, so a double tap can't send the same payment twice.
        mutable.update { it.copy(submitting = true, pendingKey = key, error = null) }
        work.launch {
            writes.withLock {
                try {
                    val response = repository.take(restaurantId, orderId, request, key)
                    if (token != revision) return@withLock
                    mutable.update {
                        it.copy(summary = response.summary, lastPayment = response.payment, pendingKey = null,
                            draft = PaymentDraft(method = it.draft.method, closeWhenPaid = it.draft.closeWhenPaid),
                            completed = response.orderClosed || isSettled(response.summary))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != revision) return@withLock
                    val failure = failure(e, write = true)
                    // Only an answer that never arrived keeps the key; a clear "no" is a fresh start next time.
                    mutable.update { it.copy(error = failure, pendingKey = if (failure.mayHaveWorked) key else null) }
                    if (failure.kind == PaymentFailure.Kind.CONFLICT || failure.mayHaveWorked) refresh()
                } finally {
                    if (token == revision) mutable.update { it.copy(submitting = false) }
                }
            }
        }
    }

    // ---- Refunds and cancels (managers) ----

    fun actionsFor(payment: PaymentDto): PaymentActions = paymentActions(payment, state.value.permissions, today(payment.paidAt))

    fun startRefund(paymentId: String) = startCorrection(paymentId, cancel = false)

    fun startCancel(paymentId: String) = startCorrection(paymentId, cancel = true)

    private fun startCorrection(paymentId: String, cancel: Boolean) {
        val payment = state.value.summary?.payments?.firstOrNull { it.id == paymentId } ?: return
        val actions = actionsFor(payment)
        if ((cancel && !actions.canCancel) || (!cancel && !actions.canRefund)) {
            mutable.update {
                it.copy(error = PaymentFailure(
                    if (cancel) "Only a manager can cancel a payment, on the day it was taken, before any refund"
                    else "Only a manager can refund, while money is left on the payment",
                    PaymentFailure.Kind.PERMISSION))
            }
            return
        }
        val suggested = if (cancel) "" else centsToDecimal(actions.refundableCents).value
        mutable.update { it.copy(correction = PaymentCorrection(paymentId, cancel, suggested), pendingKey = null, error = null) }
    }

    fun correctionAmount(text: String) = mutable.update { state ->
        state.copy(correction = state.correction?.copy(amountText = text.take(16)), pendingKey = null)
    }

    fun correctionReason(text: String) = mutable.update { state ->
        state.copy(correction = state.correction?.copy(reason = text.take(MAX_REASON)), pendingKey = null)
    }

    fun dismissCorrection() = mutable.update { it.copy(correction = null, pendingKey = null) }

    /** The problem with the refund or cancel being written, or null when it can be sent. */
    fun correctionProblem(): String? {
        val current = state.value
        val correction = current.correction ?: return "Choose a payment"
        val payment = current.summary?.payments?.firstOrNull { it.id == correction.paymentId } ?: return "That payment is gone"
        return if (correction.cancel) reasonProblem(correction.reason) else refundProblem(payment, correction.amountText, correction.reason)
    }

    fun confirmCorrection() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val orderId = current.orderId ?: return
        val correction = current.correction ?: return
        correctionProblem()?.let { problem ->
            mutable.update { it.copy(error = PaymentFailure(problem, PaymentFailure.Kind.VALIDATION)) }
            return
        }
        if (current.submitting) return
        val key = current.pendingKey ?: newKey()
        val token = revision
        mutable.update { it.copy(submitting = true, pendingKey = key, error = null) }
        work.launch {
            writes.withLock {
                try {
                    val summary = if (correction.cancel) {
                        repository.cancel(restaurantId, orderId, correction.paymentId, VoidPaymentRequestDto(correction.reason.trim()), key)
                    } else {
                        val cents = moneyCents(correction.amountText) ?: 0
                        repository.refund(restaurantId, orderId, correction.paymentId,
                            RefundPaymentRequestDto(centsToDecimal(cents), correction.reason.trim()), key)
                    }
                    if (token != revision) return@withLock
                    mutable.update { it.copy(summary = summary, correction = null, pendingKey = null, completed = isSettled(summary)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (token != revision) return@withLock
                    val failure = failure(e, write = true)
                    mutable.update { it.copy(error = failure, pendingKey = if (failure.mayHaveWorked) key else null) }
                    if (failure.kind == PaymentFailure.Kind.CONFLICT || failure.mayHaveWorked) refresh()
                } finally {
                    if (token == revision) mutable.update { it.copy(submitting = false) }
                }
            }
        }
    }

    // ---- Receipt ----

    fun loadReceipt() {
        val current = state.value
        val restaurantId = current.restaurantId ?: return
        val orderId = current.orderId ?: return
        val token = revision
        work.launch {
            mutable.update { it.copy(loadingReceipt = true) }
            try {
                val receipt = repository.receipt(restaurantId, orderId)
                if (token == revision) mutable.update { it.copy(receipt = receipt, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == revision) mutable.update { it.copy(error = failure(e, write = false)) }
            } finally {
                if (token == revision) mutable.update { it.copy(loadingReceipt = false) }
            }
        }
    }

    fun clearError() = mutable.update { it.copy(error = null) }

    override fun onDispose() {
        revision++
        work.cancel()
    }

    companion object {
        internal fun isSettled(summary: OrderPaymentSummaryDto): Boolean =
            summary.orderStatus == "CLOSED" && (summary.balanceDue.toCents() ?: 0) <= 0

        internal fun failure(error: Exception, write: Boolean): PaymentFailure = when {
            error is ApiException && error.status == 400 -> PaymentFailure(error.message, PaymentFailure.Kind.VALIDATION)
            error is ApiException && error.status == 401 -> PaymentFailure("Sign in again to continue", PaymentFailure.Kind.SESSION)
            error is ApiException && error.status == 403 -> PaymentFailure(error.message.ifBlank { "You aren't allowed to do this" }, PaymentFailure.Kind.PERMISSION)
            error is ApiException && error.status == 404 -> PaymentFailure("This order or payment no longer exists", PaymentFailure.Kind.NOT_FOUND)
            error is ApiException && error.status == 409 -> PaymentFailure(error.message, PaymentFailure.Kind.CONFLICT)
            error is ApiException && error.status == 413 -> PaymentFailure("The request is too large", PaymentFailure.Kind.VALIDATION)
            error is ApiException -> PaymentFailure("The server had a problem. Check the bill before trying again.", PaymentFailure.Kind.SERVER, mayHaveWorked = write)
            else -> PaymentFailure(
                if (write) "No answer from the server. Check the bill: trying again won't charge twice."
                else "Could not load the bill. Check the connection and try again.",
                PaymentFailure.Kind.CONNECTION, mayHaveWorked = write)
        }
    }
}

/** True when [instant] (ISO-8601) falls on today's date in the restaurant's time zone (the server's rule too). */
internal fun isToday(instant: String): Boolean = try {
    val zone = com.saporini.mobile_desktop.pos.reservations.ui.RestaurantTime.zone
    kotlin.time.Instant.parse(instant).toLocalDateTime(zone).date == kotlin.time.Clock.System.now().toLocalDateTime(zone).date
} catch (_: Exception) {
    false
}
