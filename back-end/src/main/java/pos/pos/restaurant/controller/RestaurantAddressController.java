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
import pos.pos.restaurant.service.RestaurantAddressService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/addresses")
@RequiredArgsConstructor
public class RestaurantAddressController {

    private final RestaurantAddressService restaurantAddressService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List restaurant addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAddressService.getAddresses(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create a restaurant address")
    public ResponseEntity<AddressResponse> createAddress(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpsertAddressRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantAddressService.createAddress(authentication, restaurantId, request));
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a restaurant address")
    public ResponseEntity<AddressResponse> updateAddress(
            @PathVariable UUID restaurantId,
            @PathVariable UUID addressId,
            @Valid @RequestBody UpsertAddressRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                restaurantAddressService.updateAddress(authentication, restaurantId, addressId, request)
        );
    }

    @PatchMapping("/{addressId}/primary")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Mark a restaurant address as primary")
    public ResponseEntity<AddressResponse> makePrimary(
            @PathVariable UUID restaurantId,
            @PathVariable UUID addressId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAddressService.makePrimary(authentication, restaurantId, addressId));
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Delete a restaurant address")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable UUID restaurantId,
            @PathVariable UUID addressId,
            Authentication authentication
    ) {
        restaurantAddressService.deleteAddress(authentication, restaurantId, addressId);
        return ResponseEntity.noContent().build();
    }
}
