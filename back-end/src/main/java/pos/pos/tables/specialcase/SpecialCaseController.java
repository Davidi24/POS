package pos.pos.tables.specialcase;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/special-cases")
@RequiredArgsConstructor
public class SpecialCaseController {

    private final SpecialCaseService specialCases;

    @PostMapping("/tables/{tableId}/more-time")
    @PreAuthorize("hasAnyAuthority('SETTINGS_UPDATE', 'ORDER_CREATE', 'ORDER_UPDATE')")
    @Operation(summary = "Give the guests at a table more time before the next booking")
    public ResponseEntity<Void> moreTime(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody SpecialCaseDtos.MoreTimeRequest request,
            Authentication authentication
    ) {
        specialCases.giveMoreTime(authentication, restaurantId, branchId, tableId, request.minutes());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tables/{tableId}/still-here")
    @PreAuthorize("hasAnyAuthority('SETTINGS_UPDATE', 'ORDER_CREATE', 'ORDER_UPDATE')")
    @Operation(summary = "The guests are still here: restart the table's clock")
    public ResponseEntity<Void> stillHere(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        specialCases.stillHere(authentication, restaurantId, branchId, tableId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tables/{tableId}/booking-options")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Free tables the next booking of this table fits")
    public ResponseEntity<List<ReservationAvailabilityOptionResponse>> bookingOptions(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(specialCases.bookingOptions(authentication, restaurantId, branchId, tableId));
    }

    @PostMapping("/tables/{tableId}/move-booking")
    @PreAuthorize("hasAuthority('RESERVATION_APPROVE')")
    @Operation(summary = "Move the next booking of this table to free tables")
    public ResponseEntity<Void> moveBooking(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID tableId,
            @Valid @RequestBody SpecialCaseDtos.MoveBookingRequest request,
            Authentication authentication
    ) {
        specialCases.moveBooking(authentication, restaurantId, branchId, tableId, request.tableIds(), request.primaryTableId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "What the system did by itself lately and hasn't been returned")
    public ResponseEntity<List<SpecialCaseDtos.ActionResponse>> actions(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(specialCases.actions(authentication, restaurantId, branchId));
    }

    @PostMapping("/actions/{actionId}/return")
    @PreAuthorize("hasAnyAuthority('SETTINGS_UPDATE', 'ORDER_UPDATE', 'RESERVATION_MANAGE')")
    @Operation(summary = "Put back what the system did by itself")
    public ResponseEntity<Void> returnAction(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID actionId,
            @Valid @RequestBody(required = false) SpecialCaseDtos.ReturnRequest request,
            Authentication authentication
    ) {
        specialCases.returnAction(authentication, restaurantId, branchId, actionId, request == null ? null : request.tableId());
        return ResponseEntity.noContent().build();
    }
}
