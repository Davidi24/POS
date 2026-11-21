package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.common.dto.PageResponse;
import pos.pos.restaurant.dto.RestaurantApplicationResponse;
import pos.pos.restaurant.service.RestaurantRegistrationService;

import java.util.UUID;

/** Super admins' review queue of public restaurant applications (deciding is PATCH /restaurants/registrations/{id}/review). */
@Tag(name = "Restaurants")
@Validated
@RestController
@RequestMapping("/platform/restaurant-applications")
@RequiredArgsConstructor
public class RestaurantApplicationAdminController {

    private final RestaurantRegistrationService restaurantRegistrationService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: restaurant applications waiting for review (status=PENDING, oldest first), rejected or ALL")
    public ResponseEntity<PageResponse<RestaurantApplicationResponse>> getApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Size(max = 100, message = "search must be at most 100 characters") String search,
            @RequestParam(required = false) @Size(max = 10, message = "order must be at most 10 characters") String order,
            @RequestParam(required = false) @Size(max = 3, message = "currency must be at most 3 characters") String currency,
            @RequestParam(required = false) @Size(max = 100, message = "timezone must be at most 100 characters") String timezone,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be at least 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantRegistrationService.getApplications(authentication, status, search, order, currency, timezone, page, size));
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: the places (time zones) and currencies the applications of a status use, with counts, for the Filters")
    public ResponseEntity<RestaurantRegistrationService.ApplicationFilters> getApplicationFilters(
            @RequestParam(required = false) String status,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantRegistrationService.getApplicationFilters(authentication, status));
    }

    @GetMapping("/{restaurantId}")
    @PreAuthorize("hasAuthority('RESTAURANTS_READ')")
    @Operation(summary = "Super admins: one restaurant application with the applicant's details")
    public ResponseEntity<RestaurantApplicationResponse> getApplication(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(restaurantRegistrationService.getApplication(authentication, restaurantId));
    }
}
