package pos.pos.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.PublicReservationRequest;
import pos.pos.reservation.dto.PublicReservationResponse;
import pos.pos.reservation.dto.PublicTableLookupResponse;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.service.ReservationService;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "Public Reservations")
@Validated
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicReservationController {

    private final ReservationService reservationService;

    @GetMapping("/restaurants/{restaurantSlug}/branches/{branchCode}/reservations/availability")
    @Operation(summary = "Get public booking availability")
    public ResponseEntity<List<ReservationAvailabilityOptionResponse>> getPublicAvailability(
            @PathVariable String restaurantSlug,
            @PathVariable String branchCode,
            @RequestParam OffsetDateTime reservationStart,
            @RequestParam OffsetDateTime reservationEnd,
            @RequestParam Integer partySize,
            @RequestParam(required = false) Integer maxOptions
    ) {
        return ResponseEntity.ok(reservationService.getPublicAvailability(
                restaurantSlug,
                branchCode,
                reservationStart,
                reservationEnd,
                partySize,
                maxOptions
        ));
    }

    @PostMapping("/restaurants/{restaurantSlug}/branches/{branchCode}/reservations")
    @Operation(summary = "Create a public reservation")
    public ResponseEntity<PublicReservationResponse> createPublicReservation(
            @PathVariable String restaurantSlug,
            @PathVariable String branchCode,
            @Valid @RequestBody PublicReservationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createPublicReservation(restaurantSlug, branchCode, request));
    }

    @GetMapping("/reservations/{reservationCode}")
    @Operation(summary = "Get a public reservation by code")
    public ResponseEntity<PublicReservationResponse> getPublicReservation(@PathVariable String reservationCode) {
        return ResponseEntity.ok(reservationService.getPublicReservation(reservationCode));
    }

    @PostMapping("/reservations/{reservationCode}/cancel")
    @Operation(summary = "Cancel a public reservation by code")
    public ResponseEntity<PublicReservationResponse> cancelPublicReservation(
            @PathVariable String reservationCode,
            @RequestBody(required = false) ReservationActionRequest request
    ) {
        return ResponseEntity.ok(reservationService.cancelPublicReservation(reservationCode, request));
    }

    @GetMapping("/tables/{qrCodeValue}")
    @Operation(summary = "Resolve a public table QR code")
    public ResponseEntity<PublicTableLookupResponse> getPublicTable(@PathVariable String qrCodeValue) {
        return ResponseEntity.ok(reservationService.getPublicTable(qrCodeValue));
    }
}
