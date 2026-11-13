package pos.pos.menu.dto.request;

// What a special menu is for. Booking extras: cakes, flowers… guests add to a booking, any day. Event: the menu of one
// event night (it takes the event's days and is never offered as booking extras).
public enum SpecialMenuKind {
    BOOKING_EXTRAS,
    EVENT
}
