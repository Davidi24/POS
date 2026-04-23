package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.service.RestaurantRegistrationService;

import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/registrations")
@RequiredArgsConstructor
public class RestaurantRegistrationController {

    private final RestaurantRegistrationService restaurantRegistrationService;

    @PostMapping
    @Operation(summary = "Submit a restaurant registration for review")
    public ResponseEntity<RestaurantResponse> registerRestaurant(
            @Valid @RequestBody RestaurantRegistrationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantRegistrationService.registerRestaurant(request));
    }

    @PatchMapping("/{restaurantId}/review")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Approve or reject a pending restaurant registration")
    public ResponseEntity<RestaurantResponse> reviewRegistration(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ReviewRestaurantRegistrationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                restaurantRegistrationService.reviewRegistration(authentication, restaurantId, request)
        );
    }
}
