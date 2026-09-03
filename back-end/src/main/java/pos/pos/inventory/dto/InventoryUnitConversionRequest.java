package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUnitConversionRequest {

    @NotNull(message = "fromUnit is required")
    private InventoryUnit fromUnit;

    @NotNull(message = "toUnit is required")
    private InventoryUnit toUnit;

    @NotNull(message = "conversionFactor is required")
    @Positive(message = "conversionFactor must be greater than zero")
    private BigDecimal conversionFactor;
}
