package pos.pos.inventory.dto;

import jakarta.validation.constraints.PositiveOrZero;
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
public class InventoryLevelReorderSettingsRequest {

    // Null clears the manager override, falling back to whatever the system calculates.
    @PositiveOrZero(message = "manualReorderPoint must not be negative")
    private BigDecimal manualReorderPoint;
}
