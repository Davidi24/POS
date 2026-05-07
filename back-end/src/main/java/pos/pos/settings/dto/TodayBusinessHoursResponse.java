package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayBusinessHoursResponse {

    private UUID restaurantId;
    private UUID branchId;
    private LocalDate date;
    private Integer dayOfWeek;
    private Boolean specialHoursApplied;
    private Boolean closed;
    private Boolean overnight;
    private LocalTime openTime;
    private LocalTime closeTime;
    private BusinessHourResponse businessHour;
    private SpecialHourResponse specialHour;
}
