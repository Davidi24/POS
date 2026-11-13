package pos.pos.reservation.enums;

// Changes to a booking that aren't a status change, kept in its history.
public enum ReservationEventType {
    HOLD_EXTENDED,
    ARRIVED_GUESTS_CHANGED,
    TABLES_RELEASED,
    ATTENDANCE_CONFIRMED,
    // Staff were told to call a guest whose attendance isn't confirmed (sent once per booking).
    CALL_REMINDED,
    NO_SHOW_WARNING_CLEARED,
    REFUNDED,
    // The guest said they're running late (from their email link), once per booking.
    RUNNING_LATE,
    // The guest got the "Still coming?" email with Confirm / Cancel.
    GUEST_REMINDED,
    // No answer to the first one: asked again a few hours before (Settings → Reservations).
    GUEST_REMINDED_AGAIN,
    // Staff called and couldn't reach the guest. The booking stays; only a manager cancels it.
    ATTENDANCE_UNREACHABLE,
    // The booking needs a look (checked in, never seated; open from an earlier day): the chosen roles were told.
    REVIEW_NOTIFIED,
    // Settings → Tables → Special cases: moved, given more time, or nobody handled it.
    SPECIAL_CASE
}
