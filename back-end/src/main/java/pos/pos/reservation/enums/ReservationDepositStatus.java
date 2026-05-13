package pos.pos.reservation.enums;

public enum ReservationDepositStatus {
    NOT_REQUIRED,
    PENDING,
    PAID,
    PARTIALLY_PAID,
    REFUNDED,
    FORFEITED, // Means the customer loses the deposit. Example: customer does not show up or cancels too late, so the restaurant keeps the deposit.
    WAIVED // Means the deposit was forgiven / not charged anymore, even though it was normally required. Example: deposit was required, but staff/admin decides not to ask for it for this customer.
}
