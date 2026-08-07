package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryLevelResponse;
import pos.pos.inventory.service.InventoryLevelService;

import java.util.List;
import java.util.UUID;

// Read-only on purpose. InventoryLevel is the current stock balance for one (item, location)
// pair, computed from movements — there is no POST/PUT/DELETE here. Changing stock always
// goes through the future InventoryMovement endpoints, never through this controller directly.
@Tag(name = "Inventory Levels")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/levels")
@RequiredArgsConstructor
public class InventoryLevelController {

    private final InventoryLevelService inventoryLevelService;

    @GetMapping(params = "locationId")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List stock levels at one location")
    public ResponseEntity<List<InventoryLevelResponse>> listByLocation(
            @PathVariable UUID restaurantId,
            @RequestParam UUID locationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLevelService.listByLocation(authentication, restaurantId, locationId));
    }

    @GetMapping(params = "itemId")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List stock levels for one item, across all locations")
    public ResponseEntity<List<InventoryLevelResponse>> listByItem(
            @PathVariable UUID restaurantId,
            @RequestParam UUID itemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLevelService.listByItem(authentication, restaurantId, itemId));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List items below their reorder threshold")
    public ResponseEntity<List<InventoryLevelResponse>> listLowStock(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLevelService.listLowStock(authentication, restaurantId));
    }

    @GetMapping("/{locationId}/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get the stock level for one item at one location")
    @ApiResponse(responseCode = "200", description = "Level found")
    @ApiResponse(responseCode = "404", description = "No stock level recorded yet for this item at this location")
    public ResponseEntity<InventoryLevelResponse> getLevel(
            @PathVariable UUID restaurantId,
            @PathVariable UUID locationId,
            @PathVariable UUID itemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLevelService.getLevel(authentication, restaurantId, locationId, itemId));
    }
}
