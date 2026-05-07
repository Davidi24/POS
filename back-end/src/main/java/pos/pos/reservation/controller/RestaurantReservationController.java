package pos.pos.reservation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.ReservationNoteRequest;
import pos.pos.reservation.dto.ReservationNoteResponse;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationStatusHistoryResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.dto.ReservationTimelineEventResponse;
import pos.pos.reservation.dto.UpdateReservationDepositRequest;
import pos.pos.reservation.dto.UpdateReservationRequest;
import pos.pos.reservation.dto.UpdateReservationTablesRequest;
import pos.pos.reservation.service.ReservationService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Reservations")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/reservations")
@RequiredArgsConstructor
public class RestaurantReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List restaurant reservations")
    public ResponseEntity<List<ReservationResponse>> getReservations(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getReservations(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a reservation")
    public ResponseEntity<ReservationResponse> createReservation(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.createReservation(authentication, restaurantId, request));
    }

    @GetMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one reservation")
    public ResponseEntity<ReservationResponse> getReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getReservation(authentication, restaurantId, reservationId));
    }

    @PutMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a reservation")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.updateReservation(authentication, restaurantId, reservationId, request));
    }

    @PatchMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Patch a reservation")
    public ResponseEntity<ReservationResponse> patchReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.patchReservation(authentication, restaurantId, reservationId, request));
    }

    @DeleteMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete a reservation")
    public ResponseEntity<Void> deleteReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        reservationService.deleteReservation(authentication, restaurantId, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reservationId}/confirm")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Confirm a reservation")
    public ResponseEntity<ReservationResponse> confirmReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.confirmReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/cancel")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Cancel a reservation")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.cancelReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/check-in")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Check in a reservation")
    public ResponseEntity<ReservationResponse> checkInReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.checkInReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/seat")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Seat a reservation")
    public ResponseEntity<ReservationResponse> seatReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.seatReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/complete")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Complete a reservation")
    public ResponseEntity<ReservationResponse> completeReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.completeReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/mark-no-show")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Mark a reservation as no-show")
    public ResponseEntity<ReservationResponse> markNoShow(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.markNoShow(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/reopen")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Reopen a reservation")
    public ResponseEntity<ReservationResponse> reopenReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.reopenReservation(authentication, restaurantId, reservationId, request));
    }

    @GetMapping("/{reservationId}/tables")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List reservation table assignments")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> getReservationTables(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getReservationTables(authentication, restaurantId, reservationId));
    }

    @PutMapping("/{reservationId}/tables")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace reservation table assignments")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> updateReservationTables(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationTablesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.updateReservationTables(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/tables/{tableId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Assign one table to a reservation")
    public ResponseEntity<ReservationTableAssignmentResponse> addReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.addReservationTable(authentication, restaurantId, reservationId, tableId));
    }

    @DeleteMapping("/{reservationId}/tables/{tableId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Remove one table assignment from a reservation")
    public ResponseEntity<Void> deleteReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        reservationService.deleteReservationTable(authentication, restaurantId, reservationId, tableId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{reservationId}/tables/{tableId}/primary")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Mark one assigned table as primary")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> markPrimaryReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.markPrimaryReservationTable(authentication, restaurantId, reservationId, tableId));
    }

    @PostMapping("/{reservationId}/auto-assign-table")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Auto-assign the best available table combination")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> autoAssignReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.autoAssignReservationTable(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/status-history")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List reservation status history")
    public ResponseEntity<List<ReservationStatusHistoryResponse>> getStatusHistory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getStatusHistory(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/timeline")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get reservation timeline")
    public ResponseEntity<List<ReservationTimelineEventResponse>> getTimeline(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getTimeline(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/audit")
    @PreAuthorize("hasAuthority('SETTINGS_AUDIT')")
    @Operation(summary = "Get reservation audit view")
    public ResponseEntity<ReservationAuditResponse> getAudit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getAudit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/notes")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Add a reservation note")
    public ResponseEntity<ReservationNoteResponse> addNote(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationNoteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.addNote(authentication, restaurantId, reservationId, request));
    }

    @DeleteMapping("/{reservationId}/notes/{noteId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete a reservation note")
    public ResponseEntity<Void> deleteNote(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID noteId,
            Authentication authentication
    ) {
        reservationService.deleteNote(authentication, restaurantId, reservationId, noteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{reservationId}/deposit")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get reservation deposit state")
    public ResponseEntity<ReservationDepositResponse> getDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.getDeposit(authentication, restaurantId, reservationId));
    }

    @PutMapping("/{reservationId}/deposit")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace reservation deposit settings")
    public ResponseEntity<ReservationDepositResponse> updateDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationDepositRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.updateDeposit(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/deposit/pay")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Mark reservation deposit as paid")
    public ResponseEntity<ReservationDepositResponse> payDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.payDeposit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/deposit/refund")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Mark reservation deposit as refunded")
    public ResponseEntity<ReservationDepositResponse> refundDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.refundDeposit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/deposit/waive")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Waive a reservation deposit")
    public ResponseEntity<ReservationDepositResponse> waiveDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.waiveDeposit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/deposit/forfeit")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Forfeit a reservation deposit")
    public ResponseEntity<ReservationDepositResponse> forfeitDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationService.forfeitDeposit(authentication, restaurantId, reservationId));
    }
}
