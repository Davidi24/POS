package pos.pos.preorder.enums;

// Online payment isn't built yet: every pre-order is recorded as PAID in full when placed.
public enum PreOrderPaymentStatus {
    PAID,
    REFUNDED,
    RETAINED
}
