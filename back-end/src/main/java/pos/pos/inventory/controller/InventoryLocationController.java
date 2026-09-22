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
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryLocationRequest;
import pos.pos.inventory.dto.InventoryLocationResponse;
import pos.pos.inventory.service.InventoryLocationService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Inventory Locations")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/locations")
@RequiredArgsConstructor
public class InventoryLocationController {

    private final InventoryLocationService inventoryLocationService;

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Register a new inventory location")
    @ApiResponse(responseCode = "201", description = "Location created")
    public ResponseEntity<InventoryLocationResponse> createLocation(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryLocationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryLocationService.createLocation(authentication, restaurantId, request));
    }

    @PutMapping("/{locationId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace an inventory location")
    @ApiResponse(responseCode = "200", description = "Location updated")
    @ApiResponse(responseCode = "404", description = "Location not found")
    public ResponseEntity<InventoryLocationResponse> updateLocation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID locationId,
            @Valid @RequestBody InventoryLocationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLocationService.updateLocation(authentication, restaurantId, locationId, request));
    }

    @GetMapping("/{locationId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one inventory location by id")
    @ApiResponse(responseCode = "200", description = "Location found")
    @ApiResponse(responseCode = "404", description = "Location not found")
    public ResponseEntity<InventoryLocationResponse> getLocation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID locationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLocationService.getLocation(authentication, restaurantId, locationId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List active inventory locations")
    public ResponseEntity<List<InventoryLocationResponse>> listActiveLocations(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLocationService.listActiveLocations(authentication, restaurantId));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List all inventory locations, including inactive")
    public ResponseEntity<List<InventoryLocationResponse>> listAllLocations(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryLocationService.listAllLocations(authentication, restaurantId));
    }

    @DeleteMapping("/{locationId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Deactivate an inventory location (soft, not a real delete)")
    @ApiResponse(responseCode = "204", description = "Location deactivated")
    @ApiResponse(responseCode = "404", description = "Location not found")
    public ResponseEntity<Void> deactivateLocation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID locationId,
            Authentication authentication
    ) {
        inventoryLocationService.deactivateLocation(authentication, restaurantId, locationId);
        return ResponseEntity.noContent().build();
    }
}
