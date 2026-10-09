package com.saporini.mobile_desktop.gallery

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import com.saporini.mobile_desktop.pos.payment.PayMethod
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
import com.saporini.mobile_desktop.pos.payment.ui.TakePaymentContent
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private fun d(text: String) = OrderDecimal(text)

private class GalleryPayments : PaymentRepository {
    private val earlier = PaymentDto("p1", "o1", "1042", referenceNumber = "PAY-1", method = "CARD", status = "CAPTURED", amount = d("30.00"),
        tipAmount = d("3.00"), refundableAmount = d("30.00"), currency = "EUR", cardLast4 = "4242", paidAt = (Clock.System.now() - 12.minutes).toString(),
        takenByName = "Giulia Rossi")
    var summary = OrderPaymentSummaryDto("o1", "1042", "EUR", "OPEN", "PARTIALLY_PAID", d("86.50"), paidTotal = d("30.00"), tipTotal = d("3.00"),
        balanceDue = d("56.50"), cashBalanceDue = d("56.50"), tipSuggestions = listOf(5, 10, 15), payments = listOf(earlier))
    override suspend fun summary(restaurantId: String, orderId: String) = summary
    override suspend fun take(restaurantId: String, orderId: String, request: TakePaymentRequestDto, requestKey: String): TakePaymentResponseDto {
        val payment = earlier.copy(id = "p2", method = request.method, amount = request.amount, tipAmount = request.tipAmount ?: d("0"),
            tenderedAmount = request.tenderedAmount, changeAmount = d("3.50"), cardLast4 = null, takenByName = "Davide Keci")
        summary = summary.copy(orderStatus = "CLOSED", paymentStatus = "PAID", paidTotal = d("86.50"), balanceDue = d("0"), cashBalanceDue = d("0"),
            payments = summary.payments + payment)
        return TakePaymentResponseDto(payment, summary, orderClosed = true)
    }
    override suspend fun refund(restaurantId: String, orderId: String, paymentId: String, request: RefundPaymentRequestDto, requestKey: String) = summary
    override suspend fun cancel(restaurantId: String, orderId: String, paymentId: String, request: VoidPaymentRequestDto, requestKey: String) = summary
    override suspend fun receipt(restaurantId: String, orderId: String) = ReceiptDto(
        restaurantName = "Trattoria Saporini", addressLines = listOf("Via Roma 12", "00184 Roma"), orderNumber = "1042", tableNumber = "A6",
        serverName = "Giulia Rossi", guestCount = 4, openedAt = (Clock.System.now() - 75.minutes).toString(), currency = "EUR",
        lines = listOf(
            ReceiptDto.Line("Margherita", null, 2, d("9.00"), lineTotal = d("18.00")),
            ReceiptDto.Line("Tagliatelle al ragù", "Large", 1, d("16.00"), lineTotal = d("16.00")),
            ReceiptDto.Line("Bistecca", null, 1, d("28.00"), listOf("Medium rare", "Rosemary potatoes"), d("28.00")),
            ReceiptDto.Line("Tiramisù", null, 2, d("7.00"), lineTotal = d("14.00")),
            ReceiptDto.Line("Peroni 33 cl", null, 1, d("5.00"), lineTotal = d("5.00"), removed = true)
        ),
        subtotal = d("76.00"), serviceChargeTotal = d("7.60"), tax = ReceiptDto.Tax(d("10.00"), true, d("7.91")), total = d("86.50"),
        discounts = listOf(ReceiptDto.Discount("Regular guest", d("2.90"))), paidTotal = d("30.00"), balanceDue = d("56.50"),
        payments = listOf(ReceiptDto.Payment("CARD", null, d("30.00"), d("3.00"), cardLast4 = "4242", status = "CAPTURED")),
        footerNote = "Grazie e arrivederci!"
    )
    override suspend fun payment(restaurantId: String, paymentId: String) = earlier
    override suspend fun branchPayments(restaurantId: String, branchId: String, filter: PaymentListFilter, page: Int, size: Int) = PaymentPageDto()
}

class PaymentGalleryTest {
    @Test
    fun payment() = gallery {
        val model = PaymentScreenModel(GalleryPayments(), gallerySession())
        settle(); model.open("o1"); model.loadReceipt(); settle()
        fun shot(name: String, width: Int = 1440, height: Int = 1100, required: List<String> = emptyList()) = render(name, width, height, required) {
            val state by model.state.collectAsState()
            TakePaymentContent(state, model, {})
        }
        shot("payment-card", required = listOf("Collect payment", "Left to pay", "Margherita", "Take €56.50 by card"))
        model.method(PayMethod.CASH); model.tipPercent(10); model.tenderedCents(7000); settle()
        shot("payment-cash", required = listOf("Give back"))
        shot("payment-phone", 420, 1800)
        model.take(); settle()
        shot("payment-done", required = listOf("Paid in full"))
        model.onDispose()
    }
}
