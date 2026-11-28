package pos.pos.menu.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

// The event night an event menu belongs to; its days are the menu's days.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuEventSummaryResponse {
    private UUID id;
    private String name;
    private String icon;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;
    private Boolean specialMenuOnly;
}
