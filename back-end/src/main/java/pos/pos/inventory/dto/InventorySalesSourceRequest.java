package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InventorySalesSourceRequest {
    @NotNull(message = "locationId is required")
    private UUID locationId;
}
