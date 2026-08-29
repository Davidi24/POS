package pos.pos.tables.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableRequest {

    private UUID categoryId;

    @NotBlank(message = "tableNumber is required")
    @Size(max = 30, message = "tableNumber must be at most 30 characters")
    private String tableNumber;

    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotNull(message = "capacity is required")
    @Min(value = 1, message = "capacity must be greater than 0")
    private Integer capacity;

    @Size(max = 50, message = "floor must be at most 50 characters")
    private String floor;

    @DecimalMin(value = "0.0", inclusive = true, message = "positionX must not be negative")
    private BigDecimal positionX;

    @DecimalMin(value = "0.0", inclusive = true, message = "positionY must not be negative")
    private BigDecimal positionY;

    @DecimalMin(value = "0.0", inclusive = true, message = "rotationDegrees must not be negative")
    @DecimalMax(value = "359.999", inclusive = true, message = "rotationDegrees must be less than 360")
    private BigDecimal rotationDegrees;

    @DecimalMin(value = "0.25", inclusive = true, message = "layoutScale must be at least 0.25")
    @DecimalMax(value = "4.00", inclusive = true, message = "layoutScale must not exceed 4.00")
    private BigDecimal layoutScale;

    private TableShape shape;

    private TableStatus status;

    private Boolean active;

    @Size(max = 255, message = "qrCodeValue must be at most 255 characters")
    private String qrCodeValue;
}
