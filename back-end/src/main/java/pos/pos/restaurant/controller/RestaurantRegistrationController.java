package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import pos.pos.restaurant.dto.RestaurantApplicationReceiptResponse;
import pos.pos.restaurant.dto.RestaurantApplicationResponse;
import pos.pos.restaurant.dto.RestaurantApplicationStatusRequest;
import pos.pos.restaurant.dto.RestaurantApplicationStatusResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.service.RestaurantRegistrationService;
import pos.pos.security.util.ClientInfoExtractor;

import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/registrations")
@RequiredArgsConstructor
public class RestaurantRegistrationController {

    private final RestaurantRegistrationService restaurantRegistrationService;
    private final ClientInfoExtractor clientInfoExtractor;

    @PostMapping
    @Operation(summary = "Apply to open a restaurant (public, rate-limited); the answer carries the private code for checking on it")
    public ResponseEntity<RestaurantApplicationReceiptResponse> registerRestaurant(
            @Valid @RequestBody RestaurantRegistrationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(restaurantRegistrationService.registerRestaurant(request, clientInfoExtractor.extractIp(servletRequest)));
    }

    @PostMapping("/status")
    @Operation(summary = "Where an application stands, with the rejection reason (public, rate-limited; needs the application code)")
    public ResponseEntity<RestaurantApplicationStatusResponse> getRegistrationStatus(
            @Valid @RequestBody RestaurantApplicationStatusRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(restaurantRegistrationService.getRegistrationStatus(
                request.code(), clientInfoExtractor.extractIp(servletRequest)));
    }

    @PatchMapping("/{restaurantId}/review")
    @PreAuthorize("hasAuthority('RESTAURANTS_CREATE')")
    @Operation(summary = "Approve or reject a waiting application (rejecting needs a reason the applicant sees)")
    public ResponseEntity<RestaurantApplicationResponse> reviewRegistration(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ReviewRestaurantRegistrationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                restaurantRegistrationService.reviewRegistration(authentication, restaurantId, request)
        );
    }
}
