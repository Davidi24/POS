package pos.pos.order.enums;

// Why an order was closed without being paid.
public enum OrderUnpaidReason {
    // Staff wrote why, in unpaid_note.
    OTHER,
    // Nobody acted on the order for too long (Settings → Tables → Special cases); only the system sets it.
    NO_ACTION
}
