package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryKindScope;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryKindRequest {

    @NotNull(message = "appliesTo is required")
    private InventoryKindScope appliesTo;

    @NotBlank(message = "name is required")
    @Size(max = 60, message = "name must be at most 60 characters")
    private String name;
}
