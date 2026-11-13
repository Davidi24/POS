package pos.pos.restaurant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.TimezoneChangeDtos;
import pos.pos.restaurant.service.RestaurantTimezoneService;

import java.util.UUID;

@Tag(name = "Restaurants")
@RestController
@RequestMapping("/restaurants/{restaurantId}/timezone")
@RequiredArgsConstructor
public class RestaurantTimezoneController {

    private final RestaurantTimezoneService timezoneService;

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: what moving to another time zone means for bookings, orders and shifts")
    public ResponseEntity<TimezoneChangeDtos.Preview> preview(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody TimezoneChangeDtos.PreviewRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(timezoneService.preview(authentication, restaurantId, request));
    }

    @PostMapping("/change")
    @PreAuthorize("hasAuthority('RESTAURANTS_UPDATE')")
    @Operation(summary = "Super admins: move the restaurant to another time zone")
    public ResponseEntity<RestaurantResponse> change(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody TimezoneChangeDtos.ChangeRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(timezoneService.change(authentication, restaurantId, request));
    }
}
