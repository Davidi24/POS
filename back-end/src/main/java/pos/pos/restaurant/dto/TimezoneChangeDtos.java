package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Moving a restaurant to another time zone: what it means for bookings, orders and shifts, then the change itself. */
public final class TimezoneChangeDtos {

    private TimezoneChangeDtos() {
    }

    public record PreviewRequest(
            @NotBlank(message = "timezone is required") @Size(max = 100, message = "timezone must be at most 100 characters") String timezone
    ) {
    }

    public record ChangeRequest(
            @NotBlank(message = "timezone is required") @Size(max = 100, message = "timezone must be at most 100 characters") String timezone,
            // true: bookings still to come keep the clock time the guest was told (20:00 stays 20:00).
            // false: they keep their moment, so their clock time moves by the difference.
            Boolean keepBookingTimes,
            // Must be true when bookings are still to come, after the super admin read the preview.
            Boolean confirmed,
            @Size(max = 64) String previewToken
    ) {
    }

    /** How one upcoming booking reads before and after ("Sat 14 Oct 20:00"). */
    public record BookingExample(String code, String name, String before, String afterKeepingMoment, String afterKeepingClock) {
    }

    public record Preview(
            String currentTimezone,
            String newTimezone,
            // Minutes the new zone is ahead of the old one right now (negative when behind).
            int differenceMinutes,
            long upcomingBookings,
            long openOrders,
            long openShifts,
            boolean needsConfirmation,
            List<BookingExample> examples,
            List<String> notes,
            String previewToken
    ) {
    }
}
