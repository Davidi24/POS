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
    REFUNDED
}
