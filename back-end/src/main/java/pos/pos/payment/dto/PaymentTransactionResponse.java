package pos.pos.payment.dto;

import pos.pos.payment.enums.PaymentTransactionStatus;
import pos.pos.payment.enums.PaymentTransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        PaymentTransactionType type,
        PaymentTransactionStatus status,
        BigDecimal amount,
        String currency,
        String reason,
        UUID createdBy,
        String createdByName,
        OffsetDateTime processedAt
) {
}
