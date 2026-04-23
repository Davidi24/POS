package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.dto.UpsertRestaurantBrandingRequest;
import pos.pos.restaurant.service.RestaurantBrandingService;

import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branding")
@RequiredArgsConstructor
public class RestaurantBrandingController {

    private final RestaurantBrandingService restaurantBrandingService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Get restaurant branding")
    public ResponseEntity<RestaurantBrandingResponse> getBranding(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantBrandingService.getBranding(authentication, restaurantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Create or update restaurant branding")
    public ResponseEntity<RestaurantBrandingResponse> upsertBranding(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpsertRestaurantBrandingRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantBrandingService.upsertBranding(authentication, restaurantId, request));
    }
}
