package pos.pos.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.payment.enums.PaymentTransactionStatus;
import pos.pos.payment.enums.PaymentTransactionType;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_payment_transactions_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_transactions_transaction_type", columnList = "transaction_type"),
                @Index(name = "idx_payment_transactions_status", columnList = "status"),
                @Index(name = "idx_payment_transactions_processed_at", columnList = "processed_at"),
                @Index(name = "idx_payment_transactions_gateway_transaction_id", columnList = "gateway_transaction_id")
        }
)
@Check(constraints = """
        transaction_type IN ('AUTHORIZATION', 'CAPTURE', 'SALE', 'TIP_ADJUST', 'REFUND', 'VOID', 'REVERSAL')
        AND status IN ('PENDING', 'APPROVED', 'DECLINED', 'ERROR', 'CANCELLED')
        AND char_length(currency) = 3
        AND amount > 0
        AND processed_at IS NOT NULL
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class PaymentTransaction extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "payment_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_payment_transactions_payment")
    )
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private PaymentTransactionType transactionType = PaymentTransactionType.SALE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentTransactionStatus status = PaymentTransactionStatus.PENDING;

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId;

    @Column(name = "processor_reference", length = 100)
    private String processorReference;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "response_code", length = 40)
    private String responseCode;

    @Column(name = "response_message", length = 255)
    private String responseMessage;

    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "processed_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime processedAt;

    @Override
    protected void normalizeFields() {
        gatewayTransactionId = NormalizationUtils.normalize(gatewayTransactionId);
        processorReference = NormalizationUtils.normalize(processorReference);
        currency = NormalizationUtils.normalizeUpper(currency == null && payment != null ? payment.getCurrency() : currency);
        responseCode = NormalizationUtils.normalizeUpper(responseCode);
        responseMessage = NormalizationUtils.normalize(responseMessage);
        payload = NormalizationUtils.normalize(payload);
        amount = amount == null ? BigDecimal.ZERO : amount;
        if (processedAt == null) {
            processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    protected void validateState() {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("amount must be greater than zero");
        }

        if (currency == null || currency.length() != 3) {
            throw new IllegalStateException("currency must be a 3-letter code");
        }

        if (payment != null && payment.getCurrency() != null && !Objects.equals(payment.getCurrency(), currency)) {
            throw new IllegalStateException("payment transaction currency must match payment currency");
        }
    }
}
