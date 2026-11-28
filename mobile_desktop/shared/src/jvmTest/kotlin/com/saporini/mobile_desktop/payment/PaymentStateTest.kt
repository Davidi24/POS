@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.saporini.mobile_desktop.payment

import com.saporini.mobile_desktop.core.network.ApiException
import com.saporini.mobile_desktop.core.session.SessionManager
import com.saporini.mobile_desktop.orders.user
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.payment.PayMethod
import com.saporini.mobile_desktop.pos.payment.PaymentDraft
import com.saporini.mobile_desktop.pos.payment.PaymentFailure
import com.saporini.mobile_desktop.pos.payment.PaymentScreenModel
import com.saporini.mobile_desktop.pos.payment.data.OrderPaymentSummaryDto
import com.saporini.mobile_desktop.pos.payment.data.PaymentDto
import com.saporini.mobile_desktop.pos.payment.data.PaymentListFilter
import com.saporini.mobile_desktop.pos.payment.data.PaymentPageDto
import com.saporini.mobile_desktop.pos.payment.data.PaymentRepository
import com.saporini.mobile_desktop.pos.payment.data.ReceiptDto
import com.saporini.mobile_desktop.pos.payment.data.RefundPaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.data.TakePaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.data.TakePaymentResponseDto
import com.saporini.mobile_desktop.pos.payment.data.VoidPaymentRequestDto
import com.saporini.mobile_desktop.pos.payment.paymentActions
import com.saporini.mobile_desktop.pos.payment.quickCashTenders
import com.saporini.mobile_desktop.pos.payment.quote
import com.saporini.mobile_desktop.pos.payment.reasonProblem
import com.saporini.mobile_desktop.pos.payment.refundProblem
import com.saporini.mobile_desktop.pos.payment.tipFromPercent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun d(value: String) = OrderDecimal(value)

internal fun billSummary(
    orderId: String = "order-1",
    balance: String = "25.00",
    cashBalance: String = balance,
    status: String = "OPEN",
    tips: Boolean = true,
    maxTip: Int = 50,
    split: Boolean = true,
    payments: List<PaymentDto> = emptyList()
) = OrderPaymentSummaryDto(
    orderId = orderId, orderNumber = "ORD-1", currency = "EUR", orderStatus = status, paymentStatus = "UNPAID",
    orderTotal = d("25.00"), balanceDue = d(balance), cashBalanceDue = d(cashBalance), tipsEnabled = tips,
    tipSuggestions = listOf(5, 10, 15), maxTipPercent = maxTip, splitBillsAllowed = split, payments = payments
)

internal fun paymentDto(
    id: String = "pay-1",
    status: String = "CAPTURED",
    amount: String = "25.00",
    refunded: String = "0.00",
    refundable: String = "25.00",
    paidAt: String = "2026-10-06T10:00:00Z"
) = PaymentDto(
    id = id, orderId = "order-1", referenceNumber = "PAY-ABC", method = "CARD", status = status, amount = d(amount),
    refundedAmount = d(refunded), refundableAmount = d(refundable), currency = "EUR", paidAt = paidAt
)

private class FakePayments : PaymentRepository {
    var summary = billSummary()
    var takeCalls = mutableListOf<Pair<TakePaymentRequestDto, String>>()
    var refundCalls = mutableListOf<Pair<RefundPaymentRequestDto, String>>()
    var cancelCalls = mutableListOf<Pair<VoidPaymentRequestDto, String>>()
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    var summaryCalls = 0

    override suspend fun summary(restaurantId: String, orderId: String): OrderPaymentSummaryDto {
        summaryCalls++
        gate?.let { withContext(NonCancellable) { it.await() } }
        return summary.copy(orderId = orderId)
    }

    override suspend fun take(restaurantId: String, orderId: String, request: TakePaymentRequestDto, requestKey: String): TakePaymentResponseDto {
        takeCalls += request to requestKey
        gate?.let { withContext(NonCancellable) { it.await() } }
        failure?.let { throw it }
        val paid = summary.copy(balanceDue = d("0.00"), cashBalanceDue = d("0.00"), orderStatus = "CLOSED", paymentStatus = "PAID",
            payments = listOf(paymentDto()))
        summary = paid
        return TakePaymentResponseDto(paymentDto(), paid, orderClosed = true)
    }

    override suspend fun refund(restaurantId: String, orderId: String, paymentId: String, request: RefundPaymentRequestDto, requestKey: String): OrderPaymentSummaryDto {
        refundCalls += request to requestKey
        failure?.let { throw it }
        return summary.copy(paymentStatus = "PARTIALLY_REFUNDED")
    }

    override suspend fun cancel(restaurantId: String, orderId: String, paymentId: String, request: VoidPaymentRequestDto, requestKey: String): OrderPaymentSummaryDto {
        cancelCalls += request to requestKey
        failure?.let { throw it }
        return summary.copy(orderStatus = "OPEN", balanceDue = d("25.00"), paymentStatus = "UNPAID")
    }

    override suspend fun receipt(restaurantId: String, orderId: String) = ReceiptDto(currency = "EUR", orderNumber = "ORD-1")
    override suspend fun payment(restaurantId: String, paymentId: String) = paymentDto()
    override suspend fun branchPayments(restaurantId: String, branchId: String, filter: PaymentListFilter, page: Int, size: Int) = PaymentPageDto()
}

class PaymentRulesTest {

    @Test
    fun suggestionIsTheWholeBillAndCashIsRounded() {
        val summary = billSummary(balance = "10.02", cashBalance = "10.00")
        assertEquals(1002, quote(summary, PaymentDraft(PayMethod.CARD)).amountCents)
        val cash = quote(summary, PaymentDraft(PayMethod.CASH))
        assertEquals(1000, cash.amountCents)
        assertNull(cash.problem)
        assertEquals(0, cash.leftAfterCents)
    }

    @Test
    fun amountsOverTheBillOrMalformedAreExplained() {
        val summary = billSummary()
        assertEquals("That's more than is left to pay", quote(summary, PaymentDraft(amountText = "25.01")).problem)
        assertEquals("Enter an amount like 10 or 12.50", quote(summary, PaymentDraft(amountText = "ten")).problem)
        assertEquals("Enter an amount to pay", quote(summary, PaymentDraft(amountText = "0")).problem)
        assertEquals("Enter an amount to pay", quote(summary, PaymentDraft(amountText = "-5")).problem)
        assertNull(quote(summary, PaymentDraft(amountText = "12,50")).problem)
        assertEquals(1250, quote(summary, PaymentDraft(amountText = "12,50")).leftAfterCents)
        assertEquals("Nothing is left to pay", quote(billSummary(balance = "0.00"), PaymentDraft()).problem)
        assertEquals("This order was cancelled", quote(billSummary(status = "CANCELLED"), PaymentDraft()).problem)
    }

    @Test
    fun tipsFollowTheRestaurantRules() {
        val summary = billSummary()
        val ten = quote(summary, PaymentDraft(tipPercent = 10))
        assertEquals(250, ten.tipCents)
        assertNull(ten.problem)
        assertEquals("The tip is more than 50% of the payment", quote(summary, PaymentDraft(tipText = "12.51")).problem)
        assertNull(quote(summary, PaymentDraft(tipText = "12.50")).problem)
        assertEquals("Tips are switched off", quote(billSummary(tips = false), PaymentDraft(tipText = "1")).problem)
        assertEquals("A tip can't be negative", quote(summary, PaymentDraft(tipText = "-1")).problem)
        assertEquals(13, tipFromPercent(125, 10))
    }

    @Test
    fun cashHandedOverGivesChangeOnlyForCash() {
        val summary = billSummary()
        val change = quote(summary, PaymentDraft(PayMethod.CASH, tenderedText = "50"))
        assertEquals(2500, change.changeCents)
        assertNull(change.problem)
        assertEquals("The cash handed over is less than the amount and tip",
            quote(summary, PaymentDraft(PayMethod.CASH, tipText = "1", tenderedText = "25.99")).problem)
        assertEquals("Only cash has an amount handed over", quote(summary, PaymentDraft(PayMethod.CARD, tenderedText = "50")).problem)
        assertEquals("Card digits only go with card payments", quote(summary, PaymentDraft(PayMethod.CASH, cardLast4 = "4242")).problem)
        assertEquals("Card digits are the last 4 numbers", quote(summary, PaymentDraft(PayMethod.CARD, cardLast4 = "42")).problem)
        assertEquals("Notes can be at most 1000 characters", quote(summary, PaymentDraft(notes = "x".repeat(1001))).problem)
    }

    @Test
    fun withoutSplitBillsOnlyTheWholeBillIsTaken() {
        val summary = billSummary(balance = "10.02", cashBalance = "10.00", split = false)
        assertEquals("This restaurant takes the whole bill at once", quote(summary, PaymentDraft(amountText = "5")).problem)
        assertNull(quote(summary, PaymentDraft(PayMethod.CASH, amountText = "10.00")).problem)
        assertNull(quote(summary, PaymentDraft(PayMethod.CARD)).problem)
    }

    @Test
    fun quickCashOffersRoundSums() {
        assertEquals(listOf(2350L, 2400L, 2500L, 3000L, 4000L), quickCashTenders(2350))
        assertEquals(listOf(1000L, 2000L, 5000L, 10000L), quickCashTenders(1000))
        assertTrue(quickCashTenders(0).isEmpty())
    }

    @Test
    fun refundsAndCancelsNeedPermissionAndMoneyLeft() {
        val payment = paymentDto(refundable = "25.00")
        val manager = setOf("PAYMENT_REFUND", "ORDER_VOID")
        assertTrue(paymentActions(payment, manager, takenToday = true).canRefund)
        assertTrue(paymentActions(payment, manager, takenToday = true).canCancel)
        assertFalse(paymentActions(payment, manager, takenToday = false).canCancel)
        assertFalse(paymentActions(payment, setOf("ORDER_CLOSE"), takenToday = true).canRefund)
        val partly = paymentDto(status = "PARTIALLY_REFUNDED", refunded = "5.00", refundable = "20.00")
        assertTrue(paymentActions(partly, manager, true).canRefund)
        assertFalse(paymentActions(partly, manager, true).canCancel)
        assertFalse(paymentActions(paymentDto(status = "REFUNDED", refundable = "0.00"), manager, true).canRefund)
        assertFalse(paymentActions(paymentDto(status = "VOIDED"), manager, true).canRefund)

        assertNull(refundProblem(payment, "25", "Cold food"))
        assertEquals("At most 25.00 can still be given back", refundProblem(payment, "25.01", "Cold food"))
        assertEquals("Enter an amount to give back", refundProblem(payment, "0", "Cold food"))
        assertEquals("Write a reason (at least 3 characters)", refundProblem(payment, "5", " a "))
        assertEquals("The reason can be at most 500 characters", reasonProblem("r".repeat(501)))
    }
}

class PaymentScreenModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var keys = 0

    @BeforeTest fun before() = Dispatchers.setMain(dispatcher)
    @AfterTest fun after() = Dispatchers.resetMain()

    private suspend fun TestScope.check(
        repo: FakePayments = FakePayments(),
        permissions: List<String> = listOf("ORDER_READ", "ORDER_CLOSE"),
        today: Boolean = true,
        block: suspend TestScope.(PaymentScreenModel, SessionManager) -> Unit
    ) {
        val session = SessionManager().apply { signIn(user(permissions = permissions)) }
        val model = PaymentScreenModel(repo, session, newKey = { "key-${++keys}-0123456789abcdef" }, today = { today })
        try {
            runCurrent()
            block(model, session)
        } finally {
            model.onDispose()
            runCurrent()
        }
    }

    @Test
    fun openingLoadsTheBillAndPayingClosesIt() = runTest(dispatcher) {
        val repo = FakePayments()
        check(repo) { m, _ ->
            m.open("order-1"); runCurrent()
            assertEquals("25.00", m.state.value.summary?.balanceDue?.value)
            assertTrue(m.state.value.canSubmit)
            m.tipPercent(10)
            m.take(); runCurrent()
            assertEquals(1, repo.takeCalls.size)
            val (request, key) = repo.takeCalls.single()
            assertEquals("25.00", request.amount.value)
            assertEquals("2.50", request.tipAmount?.value)
            assertEquals("CARD", request.method)
            assertEquals("key-1-0123456789abcdef", key)
            assertTrue(m.state.value.completed)
            assertNull(m.state.value.pendingKey)
            assertNotNull(m.state.value.lastPayment)
            assertEquals(PaymentDraft(), m.state.value.draft)
        }
    }

    @Test
    fun aDoubleTapSendsOnePayment() = runTest(dispatcher) {
        val repo = FakePayments().apply { gate = CompletableDeferred() }
        check(repo) { m, _ ->
            m.open("order-1")
            repo.gate!!.complete(Unit); runCurrent()
            repo.gate = CompletableDeferred()
            m.take(); m.take(); m.take()
            runCurrent()
            repo.gate!!.complete(Unit); runCurrent()
            assertEquals(1, repo.takeCalls.size)
        }
    }

    @Test
    fun noAnswerKeepsTheKeySoRetryingCanNotChargeTwice() = runTest(dispatcher) {
        val repo = FakePayments().apply { failure = java.io.IOException("timeout") }
        check(repo) { m, _ ->
            m.open("order-1"); runCurrent()
            m.take(); runCurrent()
            val failure = m.state.value.error
            assertEquals(PaymentFailure.Kind.CONNECTION, failure?.kind)
            assertTrue(failure!!.mayHaveWorked)
            val kept = m.state.value.pendingKey
            assertNotNull(kept)
            repo.failure = null
            m.take(); runCurrent()
            assertEquals(listOf(kept, kept), repo.takeCalls.map { it.second })
            assertTrue(m.state.value.completed)
        }
    }

    @Test
    fun aClearRefusalStartsFreshAndChangingTheEntryMakesANewAttempt() = runTest(dispatcher) {
        val repo = FakePayments().apply { failure = ApiException(400, "The amount is more than what is left to pay") }
        check(repo) { m, _ ->
            m.open("order-1"); runCurrent()
            m.take(); runCurrent()
            assertEquals(PaymentFailure.Kind.VALIDATION, m.state.value.error?.kind)
            assertNull(m.state.value.pendingKey)
            repo.failure = java.io.IOException("offline")
            m.take(); runCurrent()
            assertNotNull(m.state.value.pendingKey)
            m.amount("10")
            assertNull(m.state.value.pendingKey)
            // Each attempt had its own key: a refused one is never replayed.
            assertEquals(listOf("key-1-0123456789abcdef", "key-2-0123456789abcdef"), repo.takeCalls.map { it.second })
        }
    }

    @Test
    fun providerUnavailableConflictStaysVisibleAfterBillRefresh() = runTest(dispatcher) {
        val message = "Only cash payments are available until a trusted live payment provider is connected"
        val repo = FakePayments().apply { failure = ApiException(409, message) }
        check(repo) { m, _ ->
            m.open("order-1"); runCurrent()
            m.amount("25.00")
            m.take(); runCurrent()

            assertEquals(message, m.state.value.error?.message)
            assertEquals(PaymentFailure.Kind.CONFLICT, m.state.value.error?.kind)
            assertFalse(m.state.value.error!!.mayHaveWorked)
            assertNull(m.state.value.pendingKey)
            assertEquals("25.00", m.state.value.draft.amountText)
            assertEquals(2, repo.summaryCalls) // opened once, then refreshed after the conflict
        }
    }

    @Test
    fun invalidEntriesAndMissingPermissionNeverReachTheServer() = runTest(dispatcher) {
        val repo = FakePayments()
        check(repo, permissions = listOf("ORDER_READ")) { m, _ ->
            m.open("order-1"); runCurrent()
            assertFalse(m.state.value.canSubmit)
            m.take(); runCurrent()
            assertEquals(PaymentFailure.Kind.PERMISSION, m.state.value.error?.kind)
        }
        check(repo) { m, _ ->
            m.open("order-1"); runCurrent()
            m.amount("99")
            m.take(); runCurrent()
            assertEquals(PaymentFailure.Kind.VALIDATION, m.state.value.error?.kind)
        }
        assertTrue(repo.takeCalls.isEmpty())
    }

    @Test
    fun splittingEvenlySuggestsShares() = runTest(dispatcher) {
        check { m, _ ->
            m.open("order-1"); runCurrent()
            m.splitEvenly(3)
            assertEquals("8.34", m.state.value.draft.amountText)
            m.splitEvenly(0)
            assertEquals("8.34", m.state.value.draft.amountText)
        }
    }

    @Test
    fun refundAndCancelNeedAManagerAndAReason() = runTest(dispatcher) {
        val repo = FakePayments().apply { summary = billSummary(status = "CLOSED", balance = "0.00", payments = listOf(paymentDto())) }
        check(repo, permissions = listOf("ORDER_READ", "ORDER_CLOSE")) { m, _ ->
            m.open("order-1"); runCurrent()
            m.startRefund("pay-1")
            assertNull(m.state.value.correction)
            assertEquals(PaymentFailure.Kind.PERMISSION, m.state.value.error?.kind)
        }
        check(repo, permissions = listOf("ORDER_READ", "PAYMENT_REFUND", "ORDER_VOID")) { m, _ ->
            m.open("order-1"); runCurrent()
            m.startRefund("pay-1")
            assertEquals("25.00", m.state.value.correction?.amountText)
            m.confirmCorrection(); runCurrent()
            assertEquals(PaymentFailure.Kind.VALIDATION, m.state.value.error?.kind)
            m.correctionAmount("5")
            m.correctionReason("Cold food")
            m.confirmCorrection(); runCurrent()
            assertEquals("5.00", repo.refundCalls.single().first.amount.value)
            assertEquals("Cold food", repo.refundCalls.single().first.reason)
            assertNull(m.state.value.correction)

            m.startCancel("pay-1")
            m.correctionReason("Wrong method")
            m.confirmCorrection(); runCurrent()
            assertEquals("Wrong method", repo.cancelCalls.single().first.reason)
            assertEquals("OPEN", m.state.value.summary?.orderStatus)
            assertFalse(m.state.value.completed)
        }
        check(repo, permissions = listOf("ORDER_READ", "PAYMENT_REFUND", "ORDER_VOID"), today = false) { m, _ ->
            m.open("order-1"); runCurrent()
            m.startCancel("pay-1")
            assertNull(m.state.value.correction)
        }
    }

    @Test
    fun aLateAnswerForAnotherOrderIsIgnored() = runTest(dispatcher) {
        val repo = FakePayments().apply { gate = CompletableDeferred() }
        check(repo) { m, _ ->
            m.open("order-1"); runCurrent()
            m.open("order-2"); runCurrent()
            repo.gate!!.complete(Unit); runCurrent()
            assertEquals("order-2", m.state.value.summary?.orderId)
            assertEquals("order-2", m.state.value.orderId)
        }
    }

    @Test
    fun signingOutClearsTheBill() = runTest(dispatcher) {
        check { m, session ->
            m.open("order-1"); runCurrent()
            assertNotNull(m.state.value.summary)
            session.signOut(); runCurrent()
            assertNull(m.state.value.summary)
            assertNull(m.state.value.restaurantId)
        }
    }

    @Test
    fun receiptLoads() = runTest(dispatcher) {
        check { m, _ ->
            m.open("order-1"); runCurrent()
            m.loadReceipt(); runCurrent()
            assertEquals("ORD-1", m.state.value.receipt?.orderNumber)
        }
    }

    @Test
    fun failuresAreSortedByWhatTheTillShouldDo() {
        assertEquals(PaymentFailure.Kind.SESSION, PaymentScreenModel.failure(ApiException(401, "x"), write = true).kind)
        assertEquals(PaymentFailure.Kind.NOT_FOUND, PaymentScreenModel.failure(ApiException(404, "x"), write = false).kind)
        assertFalse(PaymentScreenModel.failure(ApiException(409, "x"), write = true).mayHaveWorked)
        assertTrue(PaymentScreenModel.failure(ApiException(500, "x"), write = true).mayHaveWorked)
        assertFalse(PaymentScreenModel.failure(ApiException(500, "x"), write = false).mayHaveWorked)
        assertFalse(PaymentScreenModel.failure(ApiException(400, "x"), write = true).mayHaveWorked)
    }
}
