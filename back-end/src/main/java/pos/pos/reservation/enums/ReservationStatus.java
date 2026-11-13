package pos.pos.reservation.enums;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    SEATED,
    COMPLETED,
    CANCELLED,
    NO_SHOW,
    // A request nobody answered before the booking time. Not the guest's fault, so never a no-show.
    EXPIRED,
    // Nobody acted and nobody seated the guests (Settings → Tables → Special cases). Nobody knows whether they came, so never a no-show.
    NO_ACTION
}
