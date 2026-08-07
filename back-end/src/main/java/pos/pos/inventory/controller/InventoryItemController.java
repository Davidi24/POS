package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryItemRequest;
import pos.pos.inventory.dto.InventoryItemResponse;
import pos.pos.inventory.service.InventoryItemService;

import java.util.List;
import java.util.UUID;

// REST controller for InventoryItem. This class only handles HTTP concerns —
// which method+path maps to which action, what status code comes back, who's
// allowed to call it at all (@PreAuthorize) — it never contains business logic
// itself. Every method just validates the incoming request shape and hands off
// to InventoryItemService, which does the actual work.
// All endpoints are nested under a restaurant (restaurantId in the path), since
// every inventory item belongs to exactly one restaurant.

@Tag(name = "Inventory Items")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/items")
@RequiredArgsConstructor
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Register a new inventory item")
    @ApiResponse(responseCode = "201", description = "Item created")
    public ResponseEntity<InventoryItemResponse> createItem(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryItemRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryItemService.createItem(authentication, restaurantId, request));
    }

    @PutMapping("/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace an inventory item")
    @ApiResponse(responseCode = "200", description = "Item updated")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<InventoryItemResponse> updateItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventoryItemRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryItemService.updateItem(authentication, restaurantId, itemId, request));
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one inventory item by id")
    @ApiResponse(responseCode = "200", description = "Item found")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<InventoryItemResponse> getItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryItemService.getItem(authentication, restaurantId, itemId));
    }

    @GetMapping("/by-barcode/{barcode}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Find an inventory item by barcode")
    @ApiResponse(responseCode = "200", description = "Item found")
    @ApiResponse(responseCode = "404", description = "No item registered with this barcode")
    public ResponseEntity<InventoryItemResponse> getItemByBarcode(
            @PathVariable UUID restaurantId,
            @PathVariable String barcode,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryItemService.getItemByBarcode(authentication, restaurantId, barcode));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Search inventory items by name")
    public ResponseEntity<List<InventoryItemResponse>> searchItems(
            @PathVariable UUID restaurantId,
            @RequestParam String keyword,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryItemService.searchItems(authentication, restaurantId, keyword));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List active inventory items")
    public ResponseEntity<List<InventoryItemResponse>> listActiveItems(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryItemService.listActiveItems(authentication, restaurantId));
    }

    @DeleteMapping("/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Deactivate an inventory item (soft, not a real delete)")
    @ApiResponse(responseCode = "204", description = "Item deactivated")
    @ApiResponse(responseCode = "404", description = "Item not found")
    public ResponseEntity<Void> deactivateItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            Authentication authentication
    ) {
        inventoryItemService.deactivateItem(authentication, restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }
}
