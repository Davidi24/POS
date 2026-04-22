package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.common.dto.PageResponse;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.service.RestaurantAdminService;

import java.util.UUID;

@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantAdminController {

    private final RestaurantAdminService restaurantAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "List restaurants with pagination and optional filters")
    public ResponseEntity<PageResponse<RestaurantResponse>> getRestaurants(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID ownerUserId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.getRestaurants(
                authentication,
                search,
                active,
                status,
                ownerUserId,
                page,
                size,
                sortBy,
                direction
        ));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_CREATE')")
    @Operation(summary = "Create a restaurant")
    public ResponseEntity<RestaurantResponse> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantAdminService.createRestaurant(authentication, request));
    }

    @GetMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Get one restaurant by id")
    public ResponseEntity<RestaurantResponse> getRestaurant(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.getRestaurant(authentication, restaurantId));
    }

    @PutMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update a restaurant")
    public ResponseEntity<RestaurantResponse> updateRestaurant(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateRestaurantRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.updateRestaurant(authentication, restaurantId, request));
    }

    @PatchMapping("/{restaurantId}/status")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Update restaurant status")
    public ResponseEntity<RestaurantResponse> updateRestaurantStatus(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateRestaurantStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantAdminService.updateRestaurantStatus(authentication, restaurantId, request));
    }

    @DeleteMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_DELETE')")
    @Operation(summary = "Soft delete a restaurant")
    public ResponseEntity<Void> deleteRestaurant(@PathVariable UUID restaurantId, Authentication authentication) {
        restaurantAdminService.deleteRestaurant(authentication, restaurantId);
        return ResponseEntity.noContent().build();
    }
}
