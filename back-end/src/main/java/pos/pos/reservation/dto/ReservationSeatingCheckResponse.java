package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

// For late guests: they keep their original end time. Do they still fit at their tables until then, and which other
// tables are free for the rest of their visit if not.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeatingCheckResponse {

    // Minutes from now (or from the booking time, if that's later) until the booking ends.
    private Integer minutesLeft;
    // The next booking at one of its tables, e.g. "Next booking at this table at 21:15".
    private OffsetDateTime nextBookingStart;
    private String nextBookingName;
    private java.util.UUID nextBookingId;
    private Integer nextBookingGuests;
    // The whole booking's length, so late guests moved elsewhere get all of it from now (user, 2026-11-02).
    private Integer bookingMinutes;
    // Its tables stay free until the booking ends, cleaning time included.
    private Boolean fitsAtTables;
    // Other tables free from now for the whole booking length, best first.
    private List<ReservationAvailabilityOptionResponse> alternatives;
}
