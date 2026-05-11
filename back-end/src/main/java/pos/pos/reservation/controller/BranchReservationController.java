package pos.pos.reservation.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.dto.ReservationAvailabilitySearchRequest;
import pos.pos.reservation.dto.ReservationCapacityResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationSummaryResponse;
import pos.pos.reservation.dto.ReservationValidationRequest;
import pos.pos.reservation.dto.ReservationValidationResponse;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.service.ReservationService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Reservations")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/reservations")
@RequiredArgsConstructor
public class BranchReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch reservations")
    public ResponseEntity<List<ReservationResponse>> getBranchReservations(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) UUID customerId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getBranchReservations(
                authentication,
                restaurantId,
                branchId,
                from,
                to,
                status,
                customerId
        ));
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get branch reservation calendar")
    public ResponseEntity<List<ReservationResponse>> getBranchReservationCalendar(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getBranchReservationCalendar(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/today")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List today's branch reservations")
    public ResponseEntity<List<ReservationResponse>> getTodayReservations(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getTodayReservations(authentication, restaurantId, branchId));
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List upcoming branch reservations")
    public ResponseEntity<List<ReservationResponse>> getUpcomingReservations(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getUpcomingReservations(authentication, restaurantId, branchId, limit));
    }

    @PostMapping("/availability/search")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Search reservation table combinations")
    public ResponseEntity<List<ReservationAvailabilityOptionResponse>> searchAvailability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody ReservationAvailabilitySearchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.searchAvailability(authentication, restaurantId, branchId, request));
    }

    @PostMapping("/availability/recommend")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Recommend reservation table combinations")
    public ResponseEntity<List<ReservationAvailabilityOptionResponse>> recommendAvailability(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody ReservationAvailabilitySearchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.recommendAvailability(authentication, restaurantId, branchId, request));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get reservation summary for a branch")
    public ResponseEntity<ReservationSummaryResponse> getReservationSummary(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getReservationSummary(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/capacity")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get reservation capacity overview for a branch")
    public ResponseEntity<ReservationCapacityResponse> getReservationCapacity(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) Integer partySize,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getReservationCapacity(authentication, restaurantId, branchId, from, to, partySize));
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Validate a requested reservation window and table selection")
    public ResponseEntity<ReservationValidationResponse> validateReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody ReservationValidationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.validateReservation(authentication, restaurantId, branchId, request));
    }
}
