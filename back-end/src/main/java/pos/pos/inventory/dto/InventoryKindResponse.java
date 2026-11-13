package pos.pos.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A kind as the app lists it. Built-in kinds have their code (INGREDIENT, KITCHEN …) and no id or name (the app names
 * them); the restaurant's own kinds have code CUSTOM, their id and name. UNCATEGORIZED is always last and can't be removed.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryKindResponse {
    private String code;
    private UUID id;
    private String name;
    private boolean builtIn;
    // How many items (or places) have this kind now.
    private long count;
}
