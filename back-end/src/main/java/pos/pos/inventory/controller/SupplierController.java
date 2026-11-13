package pos.pos.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.SupplierItemRequest;
import pos.pos.inventory.dto.SupplierPlaceRequest;
import pos.pos.inventory.dto.SupplierRequest;
import pos.pos.inventory.dto.SupplierResponse;
import pos.pos.inventory.service.SupplierService;

import java.util.List;
import java.util.UUID;

// Inventory workspace → Suppliers: the restaurant's suppliers, their places and the items bought from them.
@Tag(name = "Suppliers")
@RestController
@RequestMapping("/restaurants/{restaurantId}/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    @Operation(summary = "Every supplier with their places and the items bought from them")
    public ResponseEntity<List<SupplierResponse>> list(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(supplierService.list(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Add a supplier")
    public ResponseEntity<SupplierResponse> create(@PathVariable UUID restaurantId, @Valid @RequestBody SupplierRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.create(authentication, restaurantId, request));
    }

    @PutMapping("/{supplierId}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Change a supplier's details (or switch them off with active false)")
    public ResponseEntity<SupplierResponse> update(@PathVariable UUID restaurantId, @PathVariable UUID supplierId,
                                                   @Valid @RequestBody SupplierRequest request, Authentication authentication) {
        return ResponseEntity.ok(supplierService.update(authentication, restaurantId, supplierId, request));
    }

    @PostMapping("/{supplierId}/places")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Add one of the supplier's places")
    public ResponseEntity<SupplierResponse> addPlace(@PathVariable UUID restaurantId, @PathVariable UUID supplierId,
                                                     @Valid @RequestBody SupplierPlaceRequest request, Authentication authentication) {
        return ResponseEntity.ok(supplierService.savePlace(authentication, restaurantId, supplierId, null, request));
    }

    @PutMapping("/{supplierId}/places/{placeId}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Change one of the supplier's places")
    public ResponseEntity<SupplierResponse> updatePlace(@PathVariable UUID restaurantId, @PathVariable UUID supplierId, @PathVariable UUID placeId,
                                                        @Valid @RequestBody SupplierPlaceRequest request, Authentication authentication) {
        return ResponseEntity.ok(supplierService.savePlace(authentication, restaurantId, supplierId, placeId, request));
    }

    @DeleteMapping("/{supplierId}/places/{placeId}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Remove one of the supplier's places")
    public ResponseEntity<SupplierResponse> removePlace(@PathVariable UUID restaurantId, @PathVariable UUID supplierId, @PathVariable UUID placeId,
                                                        Authentication authentication) {
        return ResponseEntity.ok(supplierService.removePlace(authentication, restaurantId, supplierId, placeId));
    }

    @PutMapping("/{supplierId}/items/{itemId}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Link a stock item to the supplier, or change their code and price for it")
    public ResponseEntity<SupplierResponse> saveItem(@PathVariable UUID restaurantId, @PathVariable UUID supplierId, @PathVariable UUID itemId,
                                                     @Valid @RequestBody SupplierItemRequest request, Authentication authentication) {
        return ResponseEntity.ok(supplierService.saveItem(authentication, restaurantId, supplierId, itemId, request));
    }

    @DeleteMapping("/{supplierId}/items/{itemId}")
    @PreAuthorize("hasAuthority('INVENTORY_UPDATE')")
    @Operation(summary = "Stop buying a stock item from the supplier")
    public ResponseEntity<SupplierResponse> removeItem(@PathVariable UUID restaurantId, @PathVariable UUID supplierId, @PathVariable UUID itemId,
                                                       Authentication authentication) {
        return ResponseEntity.ok(supplierService.removeItem(authentication, restaurantId, supplierId, itemId));
    }
}
