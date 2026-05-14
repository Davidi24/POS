package pos.pos.kds.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsPriority;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsStationRoutingRequest {

    @NotNull(message = "menuItemId is required")
    private UUID menuItemId;

    @Min(value = 0, message = "displayOrder must not be negative")
    private Integer displayOrder;

    private KdsPriority priority;

    private String courseLabel;

    private Boolean active;
}
