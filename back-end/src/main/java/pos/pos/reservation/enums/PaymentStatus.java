package pos.pos.reservation.enums;

public enum PaymentStatus {
    // Waiting for the guest to pay (online, or through the payment link).
    PENDING,
    PAID,
    // Given back (all of it, or minus the card fee).
    REFUNDED,
    // Kept by the restaurant: cancelled too late, or a no-show. A goodwill refund can give part back.
    KEPT,
    // Never paid, and no longer needed.
    CANCELLED
}
