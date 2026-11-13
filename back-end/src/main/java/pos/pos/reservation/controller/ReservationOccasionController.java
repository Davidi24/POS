package pos.pos.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.ReservationOccasionDto;
import pos.pos.reservation.dto.RestaurantEventDto;
import pos.pos.reservation.dto.SaveReservationOccasionsRequest;
import pos.pos.reservation.service.ReservationOccasionService;
import pos.pos.reservation.service.RestaurantEventService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Occasions and event nights: read by staff taking bookings, changed in the Admin Hub settings.
@Tag(name = "Occasions and events")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class ReservationOccasionController {

    private final ReservationOccasionService reservationOccasionService;
    private final RestaurantEventService restaurantEventService;

    @GetMapping("/reservation-occasions")
    @PreAuthorize("hasAnyAuthority('RESERVATION_READ', 'SETTINGS_READ')")
    @Operation(summary = "Occasions a booking can have, with their icons and options")
    public ResponseEntity<List<ReservationOccasionDto>> getOccasions(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(reservationOccasionService.getOccasions(authentication, restaurantId));
    }

    @PutMapping("/reservation-occasions")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Save the occasions (the whole list, in order)")
    public ResponseEntity<List<ReservationOccasionDto>> saveOccasions(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody SaveReservationOccasionsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationOccasionService.saveOccasions(authentication, restaurantId, request.getOccasions()));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyAuthority('RESERVATION_READ', 'SETTINGS_READ', 'MENUS_READ')")
    @Operation(summary = "Event nights still to come (and the last month's)")
    public ResponseEntity<List<RestaurantEventDto>> getEvents(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(restaurantEventService.getEvents(authentication, restaurantId));
    }

    @GetMapping("/events/on")
    @PreAuthorize("hasAnyAuthority('RESERVATION_READ', 'SETTINGS_READ', 'MENUS_READ')")
    @Operation(summary = "The event on a day, if any")
    public ResponseEntity<RestaurantEventDto> getEventOn(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        RestaurantEventDto event = restaurantEventService.getEventOn(authentication, restaurantId, date);
        return event == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(event);
    }

    @PostMapping("/events")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Add an event night")
    public ResponseEntity<RestaurantEventDto> createEvent(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody RestaurantEventDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantEventService.createEvent(authentication, restaurantId, request));
    }

    @PutMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Change an event night")
    public ResponseEntity<RestaurantEventDto> updateEvent(
            @PathVariable UUID restaurantId,
            @PathVariable UUID eventId,
            @Valid @RequestBody RestaurantEventDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(restaurantEventService.updateEvent(authentication, restaurantId, eventId, request));
    }

    @DeleteMapping("/events/{eventId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Remove an event night")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID restaurantId, @PathVariable UUID eventId, Authentication authentication) {
        restaurantEventService.deleteEvent(authentication, restaurantId, eventId);
        return ResponseEntity.noContent().build();
    }
}
