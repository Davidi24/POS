package pos.pos.payment.dto;

import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Where an order's bill stands. {@code balanceDue} is what is left to pay; {@code cashBalanceDue} is the same rounded
 * the way cash is rounded at this restaurant (equal to it when cash rounding is off).
 */
public record OrderPaymentSummaryResponse(
        UUID orderId,
        String orderNumber,
        String currency,
        OrderStatus orderStatus,
        OrderPaymentStatus paymentStatus,
        BigDecimal orderTotal,
        BigDecimal prepaidTotal,
        BigDecimal paidTotal,
        BigDecimal tipTotal,
        BigDecimal refundedTotal,
        BigDecimal balanceDue,
        BigDecimal cashBalanceDue,
        boolean tipsEnabled,
        List<Integer> tipSuggestions,
        int maxTipPercent,
        boolean splitBillsAllowed,
        List<PaymentResponse> payments
) {
}
