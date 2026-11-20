package pos.pos.tables.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class AutoArrangeTableLayoutRequest {

    @Size(max = 50, message = "floor must be at most 50 characters")
    private String floor;

    private Boolean onlyUnpositioned;

    @Min(value = 1, message = "maxColumns must be greater than 0")
    @Max(value = 100, message = "maxColumns must be at most 100")
    private Integer maxColumns;

    @DecimalMin(value = "0.0", inclusive = true, message = "startX must not be negative")
    @DecimalMax(value = "99999999.99", message = "startX is out of range")
    private BigDecimal startX;

    @DecimalMin(value = "0.0", inclusive = true, message = "startY must not be negative")
    @DecimalMax(value = "99999999.99", message = "startY is out of range")
    private BigDecimal startY;

    @DecimalMin(value = "0.0", inclusive = false, message = "horizontalSpacing must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "horizontalSpacing is out of range")
    private BigDecimal horizontalSpacing;

    @DecimalMin(value = "0.0", inclusive = false, message = "verticalSpacing must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "verticalSpacing is out of range")
    private BigDecimal verticalSpacing;
}
