package pos.pos.preorder.enums;

public enum PreOrderStatus {
    // Paid and waiting; the guest (or staff) can still change or cancel it.
    SCHEDULED,
    // Turned into a real order and fired to the kitchen; locked from here on.
    SENT,
    // Cancelled before it reached the kitchen; the payment is refunded.
    CANCELLED,
    // Reached the kitchen but the guests never came (or cancelled too late); the payment is kept.
    FORFEITED
}
