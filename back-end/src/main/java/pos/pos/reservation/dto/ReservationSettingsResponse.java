package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// What the staff app needs to book the way the server does, starting with the restaurant's time zone.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSettingsResponse {

    private UUID branchId;
    private String timezone;

    // The active reservation rule; all null when the restaurant hasn't set one.
    private String ruleName;
    private Integer defaultDurationMinutes;
    private Integer bufferMinutes;
    private Integer minPartySize;
    private Integer maxPartySize;
    private Integer advanceBookingDays;
}
