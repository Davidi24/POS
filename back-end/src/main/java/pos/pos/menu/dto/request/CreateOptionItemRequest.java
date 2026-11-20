package pos.pos.menu.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOptionItemRequest {

    @Size(max = 50, message = "code must be at most 50 characters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @Digits(integer = 12, fraction = 2, message = "priceDelta must have at most 12 digits and 2 decimals")
    private BigDecimal priceDelta;

    private Boolean available;

    @Min(value = 0, message = "displayOrder must be greater than or equal to 0")
    private Integer displayOrder;

    private UUID inventoryRecipeId;

    @DecimalMin(value = "0.000", inclusive = false, message = "inventoryRecipeQuantity must be greater than zero")
    @Digits(integer = 9, fraction = 3, message = "inventoryRecipeQuantity must have at most 9 digits and 3 decimals")
    private BigDecimal inventoryRecipeQuantity;
}
