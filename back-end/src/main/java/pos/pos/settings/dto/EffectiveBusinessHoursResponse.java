package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveBusinessHoursResponse {

    private UUID restaurantId;
    private UUID branchId;
    private OffsetDateTime evaluatedAt;
    private List<BusinessHourResponse> weeklySchedule;
    private List<SpecialHourResponse> upcomingSpecialHours;
}
