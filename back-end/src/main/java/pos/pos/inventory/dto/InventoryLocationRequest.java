package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.inventory.enums.InventoryLocationType;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryLocationRequest {

    private UUID branchId;

    @Size(max = 80, message = "code must be at most 80 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotNull(message = "locationType is required")
    private InventoryLocationType locationType;

    @Pattern(regexp = "^(legacy_store|legacy_kitchen|legacy_bar|legacy_cold|legacy_storage|legacy_warehouse|legacy_transit|legacy_category|bakery|bar|transit|cleaning|dry_store|freezer|kitchen|packaging|prep_station|reach_in_fridge|beverage_fridge|walk_in_fridge|warehouse|wine)$",
            message = "iconCode is not a supported place icon")
    private String iconCode;

    // Which of the restaurant's own kinds, when locationType is CUSTOM.
    private UUID kindId;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;

    private Boolean active;
}
