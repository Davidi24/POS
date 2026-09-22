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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryAdjustmentRequest;
import pos.pos.inventory.dto.InventoryMovementResponse;
import pos.pos.inventory.dto.InventoryReceiveRequest;
import pos.pos.inventory.dto.InventoryReturnRequest;
import pos.pos.inventory.dto.InventoryTransferRequest;
import pos.pos.inventory.dto.InventoryWasteRequest;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.service.InventoryMovementService;

import java.util.List;
import java.util.UUID;

// Every POST here creates one or more InventoryMovement rows and always updates the matching
// InventoryLevel in the same transaction (see InventoryMovementService.applyMovement). There is
// no PUT and no DELETE in this controller on purpose — movements are immutable once created.
@Tag(name = "Inventory Movements")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory")
@RequiredArgsConstructor
public class InventoryMovementController {

    private final InventoryMovementService inventoryMovementService;

    @PostMapping("/receive")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Log a delivery (stock receipt)")
    @ApiResponse(responseCode = "201", description = "Receipt recorded")
    public ResponseEntity<InventoryMovementResponse> receive(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryReceiveRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryMovementService.receive(authentication, restaurantId, request));
    }

    @PostMapping("/waste")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Log wasted or spoiled stock")
    @ApiResponse(responseCode = "201", description = "Waste recorded")
    public ResponseEntity<InventoryMovementResponse> logWaste(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryWasteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryMovementService.logWaste(authentication, restaurantId, request));
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Move stock from one location to another")
    @ApiResponse(responseCode = "201", description = "Transfer recorded (a TRANSFER_OUT and a TRANSFER_IN movement)")
    public ResponseEntity<List<InventoryMovementResponse>> transfer(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryTransferRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryMovementService.transfer(authentication, restaurantId, request));
    }

    @PostMapping("/returns")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Log stock returned to the supplier")
    @ApiResponse(responseCode = "201", description = "Return recorded")
    public ResponseEntity<InventoryMovementResponse> logReturn(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryReturnRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryMovementService.logReturn(authentication, restaurantId, request));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Log a manual stock correction")
    @ApiResponse(responseCode = "201", description = "Adjustment recorded")
    public ResponseEntity<InventoryMovementResponse> adjust(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryAdjustmentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryMovementService.adjust(authentication, restaurantId, request));
    }

    @GetMapping("/movements/{movementId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one movement by id")
    @ApiResponse(responseCode = "200", description = "Movement found")
    @ApiResponse(responseCode = "404", description = "Movement not found")
    public ResponseEntity<InventoryMovementResponse> getMovement(
            @PathVariable UUID restaurantId,
            @PathVariable UUID movementId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryMovementService.getMovement(authentication, restaurantId, movementId));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List movement history, optionally filtered by order line, type, and/or item")
    public ResponseEntity<List<InventoryMovementResponse>> listMovements(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID orderLineItemId,
            @RequestParam(required = false) InventoryMovementType type,
            @RequestParam(required = false) UUID itemId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryMovementService.listMovements(authentication, restaurantId, orderLineItemId, type, itemId));
    }
}
