package pos.pos.kds.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsStationType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertKdsStationRequest {

    @Size(max = 80, message = "code must be at most 80 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotNull(message = "stationType is required")
    private KdsStationType stationType;

    @Min(value = 0, message = "displayOrder must not be negative")
    private Integer displayOrder;

    private Boolean active;

    private Boolean acceptsScheduledOrders;

    @Size(max = 80, message = "screenLabel must be at most 80 characters")
    private String screenLabel;

    private String notes;

    private UUID deviceId;

    @Valid
    private List<KdsStationRoutingRequest> routings;
}
