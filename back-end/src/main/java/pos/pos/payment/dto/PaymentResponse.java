package pos.pos.payment.dto;

import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code netAmount} is what the restaurant keeps (amount + tip + surcharge - refunds); {@code refundableAmount} is
 * what can still be given back.
 */
public record PaymentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        UUID branchId,
        UUID shiftId,
        String referenceNumber,
        String receiptNumber,
        PaymentMethod method,
        PaymentStatus status,
        BigDecimal amount,
        BigDecimal tipAmount,
        BigDecimal surchargeAmount,
        BigDecimal refundedAmount,
        BigDecimal netAmount,
        BigDecimal refundableAmount,
        BigDecimal tenderedAmount,
        BigDecimal changeAmount,
        String currency,
        String cardBrand,
        String cardLast4,
        String externalReference,
        String notes,
        OffsetDateTime paidAt,
        UUID takenBy,
        String takenByName,
        OffsetDateTime voidedAt,
        UUID voidedBy,
        String voidReason,
        List<PaymentTransactionResponse> transactions
) {
}
