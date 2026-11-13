package pos.pos.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import pos.pos.settings.enums.InventoryCountPeriod;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettingsInventoryRequest {

    // Stock worth at least this much (at cost) counts as valuable on Inventory → Overview.
    @NotNull(message = "valuableStockFrom is required")
    @DecimalMin(value = "0", message = "valuableStockFrom must be at least 0")
    @DecimalMax(value = "1000000", message = "valuableStockFrom must be at most 1000000")
    @Digits(integer = 10, fraction = 2, message = "valuableStockFrom must have at most 2 decimals")
    private BigDecimal valuableStockFrom;

    @Min(value = 1, message = "inventoryCountFrequency must be at least 1")
    @Max(value = 24, message = "inventoryCountFrequency must be at most 24")
    private Integer inventoryCountFrequency;

    private InventoryCountPeriod inventoryCountPeriod;
}
