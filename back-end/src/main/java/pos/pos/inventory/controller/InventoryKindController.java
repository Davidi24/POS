package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventoryKindRequest;
import pos.pos.inventory.dto.InventoryKindResponse;
import pos.pos.inventory.enums.InventoryKindScope;
import pos.pos.inventory.service.InventoryKindService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants/{restaurantId}/inventory/kinds")
@RequiredArgsConstructor
public class InventoryKindController {

    private final InventoryKindService inventoryKindService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "The kinds of stock items (for=ITEM) or places (for=PLACE), with how many each has")
    public ResponseEntity<List<InventoryKindResponse>> listKinds(
            @PathVariable UUID restaurantId,
            @RequestParam("for") InventoryKindScope scope,
            Authentication authentication
    ) {
        return ResponseEntity.ok(inventoryKindService.listKinds(authentication, restaurantId, scope));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Add one of the restaurant's own kinds")
    public ResponseEntity<InventoryKindResponse> createKind(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody InventoryKindRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryKindService.createKind(authentication, restaurantId, request));
    }

    @DeleteMapping("/{ref}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Remove a kind (a built-in code or the id of an own kind); what it held goes to Uncategorized")
    public ResponseEntity<Void> deleteKind(
            @PathVariable UUID restaurantId,
            @PathVariable String ref,
            @RequestParam("for") InventoryKindScope scope,
            Authentication authentication
    ) {
        inventoryKindService.deleteKind(authentication, restaurantId, scope, ref);
        return ResponseEntity.noContent().build();
    }
}
