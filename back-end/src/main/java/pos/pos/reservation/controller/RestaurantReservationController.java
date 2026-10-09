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
import pos.pos.reservation.dto.ExtendReservationHoldRequest;
import pos.pos.reservation.dto.GuestHistoryResponse;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationSeatingCheckResponse;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.ReservationDetailsResponse;
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
import pos.pos.reservation.service.ReservationCrudService;
import pos.pos.reservation.service.ReservationDepositService;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationNoteService;
import pos.pos.reservation.service.ReservationQueryService;
import pos.pos.reservation.service.ReservationTableAssignmentService;
import pos.pos.tables.realtime.TableLayoutChangeNotifier;

import java.util.List;
import java.util.UUID;

@Tag(name = "Reservations")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/reservations")
@RequiredArgsConstructor
public class RestaurantReservationController {

    private final ReservationQueryService reservationQueryService;
    private final ReservationCrudService reservationCrudService;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ReservationTableAssignmentService reservationTableAssignmentService;
    private final ReservationNoteService reservationNoteService;
    private final ReservationDepositService reservationDepositService;
    private final TableLayoutChangeNotifier tableLayoutChangeNotifier;
    private final pos.pos.reservation.service.GuestHistoryService guestHistoryService;
    private final pos.pos.reservation.service.BookingMoneyStaffService bookingMoneyStaffService;

    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "List restaurant reservations")
    public ResponseEntity<List<ReservationResponse>> getReservations(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getReservations(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Create a reservation")
    public ResponseEntity<ReservationResponse> createReservation(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationCrudService.createReservation(authentication, restaurantId, request));
    }

    @GetMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Get one reservation")
    public ResponseEntity<ReservationResponse> getReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getReservation(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/details")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Get reservation details for the staff panel")
    public ResponseEntity<ReservationDetailsResponse> getReservationDetails(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getReservationDetails(authentication, restaurantId, reservationId));
    }

    @PutMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Replace a reservation")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationCrudService.updateReservation(authentication, restaurantId, reservationId, request));
    }

    @PatchMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Patch a reservation")
    public ResponseEntity<ReservationResponse> patchReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationCrudService.patchReservation(authentication, restaurantId, reservationId, request));
    }

    @DeleteMapping("/{reservationId}")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Delete a reservation")
    public ResponseEntity<Void> deleteReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        reservationCrudService.deleteReservation(authentication, restaurantId, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reservationId}/confirm")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Confirm a reservation")
    public ResponseEntity<ReservationResponse> confirmReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.confirmReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/cancel")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Cancel a reservation")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.cancelReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/check-in")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Check in a reservation")
    public ResponseEntity<ReservationResponse> checkInReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.checkInReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/seat")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Seat a reservation at its tables")
    public ResponseEntity<ReservationResponse> seatReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return withFloorUpdate(restaurantId, reservationLifecycleService.seatReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/undo-seat")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Move seated guests back to checked in (wrong booking seated)")
    public ResponseEntity<ReservationResponse> undoSeatReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return withFloorUpdate(restaurantId, reservationLifecycleService.undoSeatReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/arrived-guests")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Change how many of the group have arrived")
    public ResponseEntity<ReservationResponse> updateArrivedGuests(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationActionRequest request,
            Authentication authentication
    ) {
        return withFloorUpdate(restaurantId, reservationLifecycleService.updateArrivedGuests(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/extend-hold")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Keep the table longer for late guests (the booking still ends on time)")
    public ResponseEntity<ReservationResponse> extendHold(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ExtendReservationHoldRequest request,
            Authentication authentication
    ) {
        return withFloorUpdate(restaurantId, reservationLifecycleService.extendHold(authentication, restaurantId, reservationId, request));
    }

    @GetMapping("/{reservationId}/seating-check")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Whether late guests still fit at their tables, and other free tables if not")
    public ResponseEntity<ReservationSeatingCheckResponse> getSeatingCheck(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getSeatingCheck(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/confirm-attendance")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Mark that the guest confirmed they're still coming (after a call)")
    public ResponseEntity<ReservationResponse> confirmAttendance(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.confirmAttendance(authentication, restaurantId, reservationId, request));
    }

    @GetMapping("/{reservationId}/guest-history")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "The guest's no-shows and cleared warnings")
    public ResponseEntity<GuestHistoryResponse> getGuestHistory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(guestHistoryService.getGuestHistory(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/clear-no-show-warning")
    @PreAuthorize("hasAuthority('RESERVATION_CORRECT')")
    @Operation(summary = "Clear the guest's no-show warning, with a reason (the history stays)")
    public ResponseEntity<ReservationResponse> clearNoShowWarning(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(guestHistoryService.clearNoShowWarning(authentication, restaurantId, reservationId, request.getReason()));
    }

    @PostMapping("/{reservationId}/decline")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Say no to a booking request (the guest is told and gets all money back)")
    public ResponseEntity<ReservationResponse> declineReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.declineReservation(authentication, restaurantId, reservationId, request));
    }

    @GetMapping("/{reservationId}/money")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Each paid part of the booking (deposit, extras, pre-order) and what a cancel would do")
    public ResponseEntity<java.util.List<pos.pos.reservation.dto.MoneyLineResponse>> getMoney(
            @PathVariable UUID restaurantId, @PathVariable UUID reservationId, Authentication authentication) {
        return ResponseEntity.ok(bookingMoneyStaffService.lines(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/extras")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Paid extras that can be added for this booking's occasion")
    public ResponseEntity<java.util.List<pos.pos.reservation.dto.GuestExtraChoice>> getExtraChoices(
            @PathVariable UUID restaurantId, @PathVariable UUID reservationId, Authentication authentication) {
        return ResponseEntity.ok(bookingMoneyStaffService.extraChoices(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/extras")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Add a paid extra (e.g. a cake), to be paid online or at the desk")
    public ResponseEntity<java.util.List<pos.pos.reservation.dto.MoneyLineResponse>> addExtra(
            @PathVariable UUID restaurantId, @PathVariable UUID reservationId,
            @Valid @RequestBody pos.pos.reservation.dto.AddBookingExtraRequest request, Authentication authentication) {
        return ResponseEntity.ok(bookingMoneyStaffService.addExtra(authentication, restaurantId, reservationId, request.getMenuItemId(), request.getQuantity()));
    }

    @PostMapping("/{reservationId}/payment-link")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Email the guest a link to pay what's due")
    public ResponseEntity<Void> sendPaymentLink(@PathVariable UUID restaurantId, @PathVariable UUID reservationId, Authentication authentication) {
        bookingMoneyStaffService.sendPaymentLink(authentication, restaurantId, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reservationId}/payments/{paymentId}/mark-paid")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "The guest paid at the desk")
    public ResponseEntity<java.util.List<pos.pos.reservation.dto.MoneyLineResponse>> markPaid(
            @PathVariable UUID restaurantId, @PathVariable UUID reservationId, @PathVariable UUID paymentId, Authentication authentication) {
        return ResponseEntity.ok(bookingMoneyStaffService.markPaid(authentication, restaurantId, reservationId, paymentId));
    }

    @PostMapping("/{reservationId}/payments/{paymentId}/remove")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Remove an unpaid extra")
    public ResponseEntity<java.util.List<pos.pos.reservation.dto.MoneyLineResponse>> removeUnpaid(
            @PathVariable UUID restaurantId, @PathVariable UUID reservationId, @PathVariable UUID paymentId, Authentication authentication) {
        return ResponseEntity.ok(bookingMoneyStaffService.removeUnpaid(authentication, restaurantId, reservationId, paymentId));
    }

    @PostMapping("/{reservationId}/money/{lineId}/goodwill")
    @PreAuthorize("hasAuthority('PAYMENT_GOODWILL_REFUND')")
    @Operation(summary = "Give part of kept money back as goodwill, with a reason (never all of it)")
    public ResponseEntity<java.util.List<pos.pos.reservation.dto.MoneyLineResponse>> goodwill(
            @PathVariable UUID restaurantId, @PathVariable UUID reservationId, @PathVariable String lineId,
            @Valid @RequestBody pos.pos.reservation.dto.GoodwillRefundRequest request, Authentication authentication) {
        return ResponseEntity.ok(bookingMoneyStaffService.goodwill(authentication, restaurantId, reservationId, lineId, request.getAmount(), request.getReason()));
    }

    // Seating changes the floor plan, so open table screens refresh.
    private ResponseEntity<ReservationResponse> withFloorUpdate(UUID restaurantId, ReservationResponse response) {
        if (response.getBranchId() != null) {
            tableLayoutChangeNotifier.notifyBranchChanged(restaurantId, response.getBranchId());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{reservationId}/complete")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Complete a reservation")
    public ResponseEntity<ReservationResponse> completeReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return withFloorUpdate(restaurantId, reservationLifecycleService.completeReservation(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/mark-no-show")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Mark a reservation as no-show")
    public ResponseEntity<ReservationResponse> markNoShow(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.markNoShow(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/reopen")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Reopen a reservation")
    public ResponseEntity<ReservationResponse> reopenReservation(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody(required = false) ReservationActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationLifecycleService.reopenReservation(authentication, restaurantId, reservationId, request));
    }

    @GetMapping("/{reservationId}/tables")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "List reservation table assignments")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> getReservationTables(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getReservationTables(authentication, restaurantId, reservationId));
    }

    @PutMapping("/{reservationId}/tables")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Replace reservation table assignments")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> updateReservationTables(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationTablesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationTableAssignmentService.updateReservationTables(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/tables/{tableId}")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Assign one table to a reservation")
    public ResponseEntity<ReservationTableAssignmentResponse> addReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationTableAssignmentService.addReservationTable(authentication, restaurantId, reservationId, tableId));
    }

    @DeleteMapping("/{reservationId}/tables/{tableId}")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Remove one table assignment from a reservation")
    public ResponseEntity<Void> deleteReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        reservationTableAssignmentService.deleteReservationTable(authentication, restaurantId, reservationId, tableId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{reservationId}/tables/{tableId}/primary")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Mark one assigned table as primary")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> markPrimaryReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID tableId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationTableAssignmentService.markPrimaryReservationTable(authentication, restaurantId, reservationId, tableId));
    }

    @PostMapping("/{reservationId}/auto-assign-table")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Auto-assign the best available table combination")
    public ResponseEntity<List<ReservationTableAssignmentResponse>> autoAssignReservationTable(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationTableAssignmentService.autoAssignReservationTable(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/status-history")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "List reservation status history")
    public ResponseEntity<List<ReservationStatusHistoryResponse>> getStatusHistory(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getStatusHistory(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/timeline")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Get reservation timeline")
    public ResponseEntity<List<ReservationTimelineEventResponse>> getTimeline(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getTimeline(authentication, restaurantId, reservationId));
    }

    @GetMapping("/{reservationId}/audit")
    @PreAuthorize("hasAuthority('SETTINGS_AUDIT')")
    @Operation(summary = "Get reservation audit view")
    public ResponseEntity<ReservationAuditResponse> getAudit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationQueryService.getAudit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/notes")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Add a reservation note")
    public ResponseEntity<ReservationNoteResponse> addNote(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationNoteRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationNoteService.addNote(authentication, restaurantId, reservationId, request));
    }

    @DeleteMapping("/{reservationId}/notes/{noteId}")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Delete a reservation note")
    public ResponseEntity<Void> deleteNote(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @PathVariable UUID noteId,
            Authentication authentication
    ) {
        reservationNoteService.deleteNote(authentication, restaurantId, reservationId, noteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{reservationId}/deposit")
    @PreAuthorize("hasAuthority('RESERVATION_READ')")
    @Operation(summary = "Get reservation deposit state")
    public ResponseEntity<ReservationDepositResponse> getDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationDepositService.getDeposit(authentication, restaurantId, reservationId));
    }

    @PutMapping("/{reservationId}/deposit")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Replace reservation deposit settings")
    public ResponseEntity<ReservationDepositResponse> updateDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody UpdateReservationDepositRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationDepositService.updateDeposit(authentication, restaurantId, reservationId, request));
    }

    @PostMapping("/{reservationId}/deposit/pay")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Mark reservation deposit as paid")
    public ResponseEntity<ReservationDepositResponse> payDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationDepositService.payDeposit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/deposit/refund")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Mark reservation deposit as refunded")
    public ResponseEntity<ReservationDepositResponse> refundDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationDepositService.refundDeposit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/deposit/waive")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Waive a reservation deposit")
    public ResponseEntity<ReservationDepositResponse> waiveDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationDepositService.waiveDeposit(authentication, restaurantId, reservationId));
    }

    @PostMapping("/{reservationId}/deposit/forfeit")
    @PreAuthorize("hasAuthority('RESERVATION_MANAGE')")
    @Operation(summary = "Forfeit a reservation deposit")
    public ResponseEntity<ReservationDepositResponse> forfeitDeposit(
            @PathVariable UUID restaurantId,
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(reservationDepositService.forfeitDeposit(authentication, restaurantId, reservationId));
    }
}
