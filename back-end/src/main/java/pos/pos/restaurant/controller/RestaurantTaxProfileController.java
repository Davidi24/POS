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
import pos.pos.restaurant.dto.RestaurantTaxProfileResponse;
import pos.pos.restaurant.dto.UpsertRestaurantTaxProfileRequest;
import pos.pos.restaurant.service.RestaurantTaxProfileService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/tax-profiles")
@RequiredArgsConstructor
public class RestaurantTaxProfileController {

    private final RestaurantTaxProfileService restaurantTaxProfileService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List restaurant tax profiles")
    public ResponseEntity<List<RestaurantTaxProfileResponse>> getTaxProfiles(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantTaxProfileService.getTaxProfiles(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create a restaurant tax profile")
    public ResponseEntity<RestaurantTaxProfileResponse> createTaxProfile(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpsertRestaurantTaxProfileRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantTaxProfileService.createTaxProfile(authentication, restaurantId, request));
    }

    @PutMapping("/{taxProfileId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a restaurant tax profile")
    public ResponseEntity<RestaurantTaxProfileResponse> updateTaxProfile(
            @PathVariable UUID restaurantId,
            @PathVariable UUID taxProfileId,
            @Valid @RequestBody UpsertRestaurantTaxProfileRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                restaurantTaxProfileService.updateTaxProfile(authentication, restaurantId, taxProfileId, request)
        );
    }

    @PatchMapping("/{taxProfileId}/default")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Mark a restaurant tax profile as default")
    public ResponseEntity<RestaurantTaxProfileResponse> makeDefault(
            @PathVariable UUID restaurantId,
            @PathVariable UUID taxProfileId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                restaurantTaxProfileService.makeDefault(authentication, restaurantId, taxProfileId)
        );
    }

    @DeleteMapping("/{taxProfileId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Delete a restaurant tax profile")
    public ResponseEntity<Void> deleteTaxProfile(
            @PathVariable UUID restaurantId,
            @PathVariable UUID taxProfileId,
            Authentication authentication
    ) {
        restaurantTaxProfileService.deleteTaxProfile(authentication, restaurantId, taxProfileId);
        return ResponseEntity.noContent().build();
    }
}
