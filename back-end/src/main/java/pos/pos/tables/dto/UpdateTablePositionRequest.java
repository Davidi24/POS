package pos.pos.tables.dto;

import jakarta.validation.constraints.DecimalMin;
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
public class UpdateTablePositionRequest {

    @Size(max = 50, message = "floor must be at most 50 characters")
    private String floor;

    @DecimalMin(value = "0.0", inclusive = true, message = "positionX must not be negative")
    private BigDecimal positionX;

    @DecimalMin(value = "0.0", inclusive = true, message = "positionY must not be negative")
    private BigDecimal positionY;
}
