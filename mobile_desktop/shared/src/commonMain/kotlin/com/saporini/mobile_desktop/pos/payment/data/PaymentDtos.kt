package com.saporini.mobile_desktop.pos.payment.data

import com.saporini.mobile_desktop.pos.orders.domain.model.OrderDecimal
import kotlinx.serialization.Serializable

// The server's payment API (/restaurants/{r}/orders/{o}/payments, /receipt, /branches/{b}/payments).
// Money stays exact decimal text; arithmetic happens in whole cents.

@Serializable
data class PaymentTransactionDto(
    val id: String? = null,
    val type: String,
    val status: String,
    val amount: OrderDecimal,
    val currency: String,
    val reason: String? = null,
    val createdBy: String? = null,
    val createdByName: String? = null,
    val processedAt: String? = null
)

@Serializable
data class PaymentDto(
    val id: String,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val branchId: String? = null,
    val shiftId: String? = null,
    val referenceNumber: String,
    val receiptNumber: String? = null,
    val method: String,
    val status: String,
    val amount: OrderDecimal,
    val tipAmount: OrderDecimal = OrderDecimal.ZERO,
    val surchargeAmount: OrderDecimal = OrderDecimal.ZERO,
    val refundedAmount: OrderDecimal = OrderDecimal.ZERO,
    val netAmount: OrderDecimal = OrderDecimal.ZERO,
    val refundableAmount: OrderDecimal = OrderDecimal.ZERO,
    val tenderedAmount: OrderDecimal? = null,
    val changeAmount: OrderDecimal? = null,
    val currency: String,
    val cardBrand: String? = null,
    val cardLast4: String? = null,
    val externalReference: String? = null,
    val notes: String? = null,
    val paidAt: String? = null,
    val takenBy: String? = null,
    val takenByName: String? = null,
    val voidedAt: String? = null,
    val voidedBy: String? = null,
    val voidReason: String? = null,
    val transactions: List<PaymentTransactionDto> = emptyList()
)

@Serializable
data class OrderPaymentSummaryDto(
    val orderId: String,
    val orderNumber: String? = null,
    val currency: String,
    val orderStatus: String,
    val paymentStatus: String,
    val orderTotal: OrderDecimal,
    val prepaidTotal: OrderDecimal = OrderDecimal.ZERO,
    val paidTotal: OrderDecimal = OrderDecimal.ZERO,
    val tipTotal: OrderDecimal = OrderDecimal.ZERO,
    val refundedTotal: OrderDecimal = OrderDecimal.ZERO,
    val balanceDue: OrderDecimal,
    val cashBalanceDue: OrderDecimal,
    val tipsEnabled: Boolean = true,
    val tipSuggestions: List<Int> = emptyList(),
    val maxTipPercent: Int = 50,
    val splitBillsAllowed: Boolean = true,
    val payments: List<PaymentDto> = emptyList()
)

@Serializable
data class TakePaymentRequestDto(
    val method: String,
    val amount: OrderDecimal,
    val tipAmount: OrderDecimal? = null,
    val tenderedAmount: OrderDecimal? = null,
    val cardBrand: String? = null,
    val cardLast4: String? = null,
    val externalReference: String? = null,
    val gatewayName: String? = null,
    val notes: String? = null,
    val closeOrderWhenPaid: Boolean? = null
)

@Serializable
data class TakePaymentResponseDto(
    val payment: PaymentDto,
    val summary: OrderPaymentSummaryDto,
    val orderClosed: Boolean = false
)

@Serializable
data class RefundPaymentRequestDto(val amount: OrderDecimal, val reason: String)

@Serializable
data class VoidPaymentRequestDto(val reason: String)

@Serializable
data class PaymentPageDto(
    val items: List<PaymentDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false
)

@Serializable
data class ReceiptDto(
    val restaurantName: String? = null,
    val legalName: String? = null,
    val branchName: String? = null,
    val addressLines: List<String> = emptyList(),
    val phone: String? = null,
    val taxNumber: String? = null,
    val vatNumber: String? = null,
    val showLogo: Boolean = true,
    val orderNumber: String? = null,
    val tableNumber: String? = null,
    val serverName: String? = null,
    val guestCount: Int? = null,
    val openedAt: String? = null,
    val closedAt: String? = null,
    val printedAt: String? = null,
    val currency: String,
    val lines: List<Line> = emptyList(),
    val subtotal: OrderDecimal = OrderDecimal.ZERO,
    val discounts: List<Discount> = emptyList(),
    val discountTotal: OrderDecimal = OrderDecimal.ZERO,
    val serviceChargeTotal: OrderDecimal = OrderDecimal.ZERO,
    val tax: Tax? = null,
    val total: OrderDecimal = OrderDecimal.ZERO,
    val prepaidTotal: OrderDecimal = OrderDecimal.ZERO,
    val payments: List<Payment> = emptyList(),
    val paidTotal: OrderDecimal = OrderDecimal.ZERO,
    val tipTotal: OrderDecimal = OrderDecimal.ZERO,
    val balanceDue: OrderDecimal = OrderDecimal.ZERO,
    val footerNote: String? = null,
    val showQrCode: Boolean = false,
    val copies: Int = 1
) {
    @Serializable
    data class Line(
        val name: String,
        val variant: String? = null,
        val quantity: Int,
        val unitPrice: OrderDecimal,
        val options: List<String> = emptyList(),
        val lineTotal: OrderDecimal,
        val removed: Boolean = false,
        val notes: String? = null
    )

    @Serializable
    data class Discount(val name: String, val amount: OrderDecimal)

    @Serializable
    data class Tax(val rate: OrderDecimal, val inclusive: Boolean, val amount: OrderDecimal)

    @Serializable
    data class Payment(
        val method: String,
        val receiptNumber: String? = null,
        val amount: OrderDecimal,
        val tipAmount: OrderDecimal = OrderDecimal.ZERO,
        val refundedAmount: OrderDecimal = OrderDecimal.ZERO,
        val tenderedAmount: OrderDecimal? = null,
        val changeAmount: OrderDecimal? = null,
        val cardBrand: String? = null,
        val cardLast4: String? = null,
        val paidAt: String? = null,
        val status: String
    )
}

/** Filters for the branch payment list. */
data class PaymentListFilter(
    val from: String? = null,
    val to: String? = null,
    val method: String? = null,
    val status: String? = null,
    val staffId: String? = null,
    val search: String? = null
)
