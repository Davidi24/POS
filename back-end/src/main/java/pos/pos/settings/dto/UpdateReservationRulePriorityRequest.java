package pos.pos.settings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class UpdateReservationRulePriorityRequest {

    @NotNull(message = "priority is required")
    @Min(value = 0, message = "priority must not be negative")
    private Integer priority;
}
