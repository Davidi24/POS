package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// One paid (or to-pay) part of a booking: a deposit, an extra or the food pre-order, and what a cancel would do.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoneyLineResponse {
    // A payment id, or "pre-order-<id>".
    private String id;
    // DEPOSIT, EXTRA or PRE_ORDER.
    private String kind;
    private String description;
    private BigDecimal amount;
    private String currency;
    // PENDING, PAID, REFUNDED, KEPT or CANCELLED.
    private String status;
    private OffsetDateTime refundDeadline;
    private BigDecimal refundedAmount;
    // What the guest would get back if they cancelled right now.
    private BigDecimal refundIfCancelledNow;
    // e.g. "€49.10 back if cancelled now: food pre-order €50.00 minus the card fee (€0.90)".
    private String explanation;
}
