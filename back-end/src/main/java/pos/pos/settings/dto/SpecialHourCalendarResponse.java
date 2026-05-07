package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialHourCalendarResponse {

    private UUID restaurantId;
    private UUID branchId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<SpecialHourResponse> items;
}
