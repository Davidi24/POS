package pos.pos.hours;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bookings that fall when a branch is closed by its hours, for after the hours change (user, 2026-11-21): the hours save at
 * once, then the bookings left in closed time are listed to move or cancel, and the people told about bookings hear of the
 * new ones. Made by the reservation module, so the hours don't depend on it.
 */
public interface ClosedTimeBookings {

    record Booking(UUID id, String code, OffsetDateTime start, OffsetDateTime end, int guests, String name, String phone,
                   String status, List<String> tables) {
    }

    /** Upcoming bookings (requests, confirmed, checked in) of the branch whose start is in closed time, soonest first. */
    List<Booking> inClosedTime(UUID branchId);

    /** Tells the people told about bookings that [bookings] now fall when the branch is closed (not [actorId]). */
    void alert(UUID branchId, List<Booking> bookings, UUID actorId);
}
