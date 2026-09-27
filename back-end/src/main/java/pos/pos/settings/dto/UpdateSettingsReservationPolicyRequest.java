package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

// Admin Hub → Settings → Reservations: the times and limits of the booking rules.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsReservationPolicyRequest {

    @NotNull(message = "largeGroupFrom is required")
    @Min(value = 1, message = "largeGroupFrom must be at least 1")
    @Max(value = 100, message = "largeGroupFrom must be at most 100")
    private Integer largeGroupFrom;

    @NotNull(message = "largeGroupExtraMinutes is required")
    @Min(value = 0, message = "largeGroupExtraMinutes must not be negative")
    @Max(value = 240, message = "largeGroupExtraMinutes must be at most 240")
    private Integer largeGroupExtraMinutes;

    @NotNull(message = "approvalGroupSize is required")
    @Min(value = 1, message = "approvalGroupSize must be at least 1")
    @Max(value = 200, message = "approvalGroupSize must be at most 200")
    private Integer approvalGroupSize;

    @NotNull(message = "holdMinutes is required")
    @Min(value = 5, message = "holdMinutes must be at least 5")
    @Max(value = 240, message = "holdMinutes must be at most 240")
    private Integer holdMinutes;

    @NotNull(message = "holdWarningMinutes is required")
    @Min(value = 0, message = "holdWarningMinutes must not be negative")
    @Max(value = 240, message = "holdWarningMinutes must be at most 240")
    private Integer holdWarningMinutes;

    @NotNull(message = "checkInOpensMinutes is required")
    @Min(value = 0, message = "checkInOpensMinutes must not be negative")
    @Max(value = 1440, message = "checkInOpensMinutes must be at most 1440")
    private Integer checkInOpensMinutes;

    @NotNull(message = "confirmReminderTime is required")
    private LocalTime confirmReminderTime;

    @NotNull(message = "sameDayConfirmMinutes is required")
    @Min(value = 0, message = "sameDayConfirmMinutes must not be negative")
    @Max(value = 1440, message = "sameDayConfirmMinutes must be at most 1440")
    private Integer sameDayConfirmMinutes;

    @NotNull(message = "attendanceCallMinutes is required")
    @Min(value = 0, message = "attendanceCallMinutes must not be negative")
    @Max(value = 1440, message = "attendanceCallMinutes must be at most 1440")
    private Integer attendanceCallMinutes;

    @NotNull(message = "reopenWindowMinutes is required")
    @Min(value = 0, message = "reopenWindowMinutes must not be negative")
    @Max(value = 1440, message = "reopenWindowMinutes must be at most 1440")
    private Integer reopenWindowMinutes;

    @NotNull(message = "undoSeatMinutes is required")
    @Min(value = 0, message = "undoSeatMinutes must not be negative")
    @Max(value = 240, message = "undoSeatMinutes must be at most 240")
    private Integer undoSeatMinutes;

    @NotNull(message = "runningLateMaxMinutes is required")
    @Min(value = 0, message = "runningLateMaxMinutes must not be negative")
    @Max(value = 240, message = "runningLateMaxMinutes must be at most 240")
    private Integer runningLateMaxMinutes;

    @NotNull(message = "lateAfterMinutes is required")
    @Min(value = 0, message = "lateAfterMinutes must not be negative")
    @Max(value = 240, message = "lateAfterMinutes must be at most 240")
    private Integer lateAfterMinutes;

    @NotNull(message = "guestReminderHours is required")
    @Min(value = 1, message = "guestReminderHours must be at least 1")
    @Max(value = 168, message = "guestReminderHours must be at most 168")
    private Integer guestReminderHours;

    @NotNull(message = "noShowWarningFrom is required")
    @Min(value = 1, message = "noShowWarningFrom must be at least 1")
    @Max(value = 20, message = "noShowWarningFrom must be at most 20")
    private Integer noShowWarningFrom;

    @NotNull(message = "depositFromGuests is required")
    @Min(value = 1, message = "depositFromGuests must be at least 1")
    @Max(value = 200, message = "depositFromGuests must be at most 200")
    private Integer depositFromGuests;
}
