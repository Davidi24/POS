package pos.pos.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.GuestBookingView;
import pos.pos.reservation.dto.GuestExtraChoice;
import pos.pos.reservation.dto.MoneyLineResponse;
import pos.pos.reservation.dto.OnlineBookingRequest;
import pos.pos.reservation.service.GuestBookingService;

import java.util.List;

// Guests booking and managing their booking online (for the website). The token comes from their email link.
@Tag(name = "Guest bookings")
@Validated
@RestController
@RequestMapping("/public/guest-bookings")
@RequiredArgsConstructor
public class GuestBookingController {

    private final GuestBookingService guestBookingService;

    @PostMapping("/{restaurantSlug}/{branchCode}")
    @Operation(summary = "Book a table: confirmed at once when a table is free, otherwise a request")
    public ResponseEntity<GuestBookingView> book(
            @PathVariable String restaurantSlug,
            @PathVariable String branchCode,
            @Valid @RequestBody OnlineBookingRequest request
    ) {
        return ResponseEntity.ok(guestBookingService.book(restaurantSlug, branchCode, request));
    }

    @GetMapping("/{restaurantSlug}/{branchCode}/extras")
    @Operation(summary = "Paid extras for an occasion (from the special menus)")
    public ResponseEntity<List<GuestExtraChoice>> extras(
            @PathVariable String restaurantSlug,
            @PathVariable String branchCode,
            @RequestParam(required = false) String occasion
    ) {
        return ResponseEntity.ok(guestBookingService.extras(restaurantSlug, branchCode, occasion));
    }

    @GetMapping("/{token}")
    @Operation(summary = "The guest's booking")
    public ResponseEntity<GuestBookingView> view(@PathVariable String token) {
        return ResponseEntity.ok(guestBookingService.view(token));
    }

    @PostMapping("/{token}/confirm")
    @Operation(summary = "The guest confirms they're coming")
    public ResponseEntity<GuestBookingView> confirm(@PathVariable String token) {
        return ResponseEntity.ok(guestBookingService.confirmAttendance(token));
    }

    @PostMapping("/{token}/running-late")
    @Operation(summary = "The guest is running late (10, 15 or 20 minutes, once)")
    public ResponseEntity<GuestBookingView> runningLate(@PathVariable String token, @RequestParam int minutes) {
        return ResponseEntity.ok(guestBookingService.runningLate(token, minutes));
    }

    @GetMapping("/{token}/cancel-preview")
    @Operation(summary = "What cancelling now would refund or keep, line by line")
    public ResponseEntity<List<MoneyLineResponse>> cancelPreview(@PathVariable String token) {
        return ResponseEntity.ok(guestBookingService.cancelPreview(token));
    }

    @PostMapping("/{token}/cancel")
    @Operation(summary = "The guest cancels (always free; paid parts follow their deadlines)")
    public ResponseEntity<GuestBookingView> cancel(@PathVariable String token) {
        return ResponseEntity.ok(guestBookingService.cancel(token));
    }

    @PostMapping("/{token}/pay")
    @Operation(summary = "Pay what's due (test mode until a card provider is connected)")
    public ResponseEntity<GuestBookingView> pay(@PathVariable String token) {
        return ResponseEntity.ok(guestBookingService.payInTestMode(token));
    }
}
