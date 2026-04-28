package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.service.BranchAddressService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/addresses")
@RequiredArgsConstructor
public class BranchAddressController {

    private final BranchAddressService branchAddressService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List branch addresses")
    public ResponseEntity<List<AddressResponse>> getBranchAddresses(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(branchAddressService.getAddresses(authentication, restaurantId, branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create a branch address")
    public ResponseEntity<AddressResponse> createBranchAddress(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpsertAddressRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(branchAddressService.createAddress(authentication, restaurantId, branchId, request));
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a branch address")
    public ResponseEntity<AddressResponse> updateBranchAddress(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID addressId,
            @Valid @RequestBody UpsertAddressRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                branchAddressService.updateAddress(authentication, restaurantId, branchId, addressId, request)
        );
    }

    @PatchMapping("/{addressId}/primary")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Mark a branch address as primary")
    public ResponseEntity<AddressResponse> makePrimary(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID addressId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                branchAddressService.makePrimary(authentication, restaurantId, branchId, addressId)
        );
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Delete a branch address")
    public ResponseEntity<Void> deleteBranchAddress(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID addressId,
            Authentication authentication
    ) {
        branchAddressService.deleteAddress(authentication, restaurantId, branchId, addressId);
        return ResponseEntity.noContent().build();
    }
}
