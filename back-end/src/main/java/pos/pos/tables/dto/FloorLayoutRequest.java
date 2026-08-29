package pos.pos.tables.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorLayoutRequest {

    @NotBlank(message = "floorName is required")
    @Size(
            max = 50,
            message = "floorName must be at most 50 characters"
    )
    private String floorName;

    private BigDecimal planOffsetX;

    private BigDecimal planOffsetY;

    @DecimalMin(
            value = "0.25",
            message = "planScale must be at least 0.25"
    )
    @DecimalMax(
            value = "4.00",
            message = "planScale must not exceed 4.00"
    )
    private BigDecimal planScale;
}