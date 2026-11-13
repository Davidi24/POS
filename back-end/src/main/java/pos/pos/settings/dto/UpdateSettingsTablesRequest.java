package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsTablesRequest {

    @NotNull(message = "seatingExtraGuests is required")
    @Min(value = 0, message = "seatingExtraGuests must be at least 0")
    @Max(value = 20, message = "seatingExtraGuests must be at most 20")
    private Integer seatingExtraGuests;

    @NotNull(message = "seatingMostExtraGuests is required")
    @Min(value = 0, message = "seatingMostExtraGuests must be at least 0")
    @Max(value = 50, message = "seatingMostExtraGuests must be at most 50")
    private Integer seatingMostExtraGuests;

    @NotNull(message = "nextBookingWarningMinutes is required")
    @Min(value = 0, message = "nextBookingWarningMinutes must be at least 0")
    @Max(value = 240, message = "nextBookingWarningMinutes must be at most 240")
    private Integer nextBookingWarningMinutes;

    @NotNull(message = "joinBookingWarningMinutes is required")
    @Min(value = 0, message = "joinBookingWarningMinutes must be at least 0")
    @Max(value = 1440, message = "joinBookingWarningMinutes must be at most 1440")
    private Integer joinBookingWarningMinutes;

    @NotNull(message = "bookingComingMinutes is required")
    @Min(value = 0, message = "bookingComingMinutes must be at least 0")
    @Max(value = 1440, message = "bookingComingMinutes must be at most 1440")
    private Integer bookingComingMinutes;

    @NotNull(message = "specialCasePaidMinutes is required")
    @Min(value = 0, message = "specialCasePaidMinutes must be at least 0")
    @Max(value = 1440, message = "specialCasePaidMinutes must be at most 1440")
    private Integer specialCasePaidMinutes;

    @NotNull(message = "specialCaseSeatedMinutes is required")
    @Min(value = 0, message = "specialCaseSeatedMinutes must be at least 0")
    @Max(value = 1440, message = "specialCaseSeatedMinutes must be at most 1440")
    private Integer specialCaseSeatedMinutes;

    @NotNull(message = "specialCaseOrderMinutes is required")
    @Min(value = 0, message = "specialCaseOrderMinutes must be at least 0")
    @Max(value = 1440, message = "specialCaseOrderMinutes must be at most 1440")
    private Integer specialCaseOrderMinutes;

    @NotNull(message = "specialCaseWarningMinutes is required")
    @Min(value = 0, message = "specialCaseWarningMinutes must be at least 0")
    @Max(value = 240, message = "specialCaseWarningMinutes must be at most 240")
    private Integer specialCaseWarningMinutes;

    @NotNull(message = "specialCaseAutoMove is required")
    private Boolean specialCaseAutoMove;

    @NotNull(message = "specialCaseBookingNoAction is required")
    private Boolean specialCaseBookingNoAction;

    @NotNull(message = "specialCaseMoreTime is required")
    @jakarta.validation.constraints.Pattern(regexp = "^[1-9][0-9]{0,2}(,[1-9][0-9]{0,2}){0,4}$", message = "specialCaseMoreTime must be minutes separated by commas")
    @Size(max = 30, message = "specialCaseMoreTime can have at most 30 characters")
    private String specialCaseMoreTime;

    @NotNull(message = "specialCaseAlertRoles is required")
    @Size(max = 500, message = "specialCaseAlertRoles can have at most 500 characters")
    private String specialCaseAlertRoles;

    @NotNull(message = "seatingAlertRoles is required")
    @Size(max = 500, message = "seatingAlertRoles can have at most 500 characters")
    private String seatingAlertRoles;
}
