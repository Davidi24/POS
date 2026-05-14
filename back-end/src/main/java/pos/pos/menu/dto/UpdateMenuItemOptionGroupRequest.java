package pos.pos.menu.dto;

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
public class UpdateMenuItemOptionGroupRequest {

    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;

    @Min(value = 0, message = "minSelectOverride must be greater than or equal to 0")
    private Integer minSelectOverride;

    @Min(value = 0, message = "maxSelectOverride must be greater than or equal to 0")
    private Integer maxSelectOverride;

    private Boolean requiredOverride;
}
