package pos.pos.reservation.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.entity.Customer;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.UpdateReservationRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.event.ReservationDeletingEvent;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationCrudService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationRepository reservationRepository;
    private final ReservationSupport reservationSupport;
    private final ReservationTableAssignmentService reservationTableAssignmentService;
    private final ReservationNotifications reservationNotifications;
    private final ReservationPolicy reservationPolicy;
    private final ReservationAvailabilitySupport reservationAvailabilitySupport;
    private final GuestNoShowCounter guestNoShowCounter;
    private final ReservationOccasionService reservationOccasionService;
    private BookingMoneyService bookingMoneyService;
    private ReservationMailService reservationMailService;

    // Optional so the CRUD works without money and email (e.g. in tests).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setBookingMoneyService(BookingMoneyService bookingMoneyService) {
        this.bookingMoneyService = bookingMoneyService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setReservationMailService(ReservationMailService reservationMailService) {
        this.reservationMailService = reservationMailService;
    }
    private final ApplicationEventPublisher events;

    @Transactional
    public ReservationResponse createReservation(Authentication authentication, UUID restaurantId, ReservationRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = reservationSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId());
        Customer customer = reservationSupport.resolveCustomer(restaurantId, request.getCustomerId());

        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreatedBy(actorId);
        reservation.setUpdatedBy(actorId);

        reservationSupport.applyReservationRequest(reservation, request, actorId, true, endOf(branch, request));
        reservationOccasionService.apply(reservation, request.getOccasionCode(), request.getOccasionOptions(), request.getOccasionNote());

        // this check if the tables that will be assigned to this reservation can be like maybe there is
        // another reservation there ... after that it clears the previous connection like the previous reservation
        // and set the new one
        if (request.getInitialTableIds() != null) {
            reservationTableAssignmentService.replaceReservationTables(
                    reservation,
                    request.getInitialTableIds(),
                    request.getPrimaryTableId(),
                    actorId
            );
        }

        ReservationActor actor = ReservationActor.of(authentication, actorId);
        decideStatus(reservation, actor);
        if (Boolean.TRUE.equals(request.getAttendanceConfirmed()) && reservation.getStatus() == ReservationStatus.CONFIRMED) {
            reservation.setAttendanceConfirmedAt(OffsetDateTime.now(ZoneOffset.UTC));
            reservation.setAttendanceConfirmedBy(actorId);
            reservation.setAttendanceConfirmedVia(pos.pos.reservation.enums.AttendanceConfirmedVia.STAFF);
        }

        Reservation saved = reservationSupport.saveReservation(reservation);
        reservationNotifications.created(saved, actorId, false);
        // From the guest's first no-show (Admin Hub setting), staff are warned when they book again.
        int noShows = guestNoShowCounter.noShowsOf(saved).size();
        if (noShows > 0 && noShows >= reservationPolicy.values(restaurant).noShowWarningFrom()) {
            reservationNotifications.noShowWarning(saved, noShows, actorId);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (bookingMoneyService != null) {
            bookingMoneyService.createDepositIfRequired(saved);
        }
        // A booking taken on the phone: the guest gets the confirmation (with their booking page) by email.
        if (reservationMailService != null && saved.getStatus() == ReservationStatus.CONFIRMED && saved.getContactEmail() != null) {
            var due = bookingMoneyService == null ? java.util.List.<pos.pos.reservation.dto.MoneyLineResponse>of()
                    : bookingMoneyService.lines(saved, now).stream().filter(line -> "PENDING".equals(line.getStatus())).toList();
            boolean askNow = saved.getAttendanceConfirmedAt() == null
                    && !now.isBefore(saved.getReservationStart().minusHours(reservationPolicy.values(restaurant).guestReminderHours()));
            reservationMailService.confirmed(saved, false, askNow, due);
        }
        return reservationSupport.toResponse(saved);
    }

    // Staff take bookings on the phone or at the door: confirmed straight away when a table fits the whole visit.
    // Otherwise it's a request staff answer later: no table free then, or a big group that needs someone who can
    // approve bookings.
    private void decideStatus(Reservation reservation, ReservationActor actor) {
        int approvalFrom = reservationPolicy.values(reservation.getRestaurant()).approvalGroupSize();
        boolean needsApproval = reservation.getPartySize() >= approvalFrom && !actor.canApprove();
        boolean tableFree = !reservation.getTableAssignments().isEmpty() || !reservationAvailabilitySupport.availabilityOptionsForBranch(
                reservation.getBranch(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                1
        ).isEmpty();
        if (!needsApproval && tableFree) {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(OffsetDateTime.now(ZoneOffset.UTC));
            reservationSupport.addStatusHistory(reservation, null, ReservationStatus.CONFIRMED, "Booked by staff", actor.id());
            return;
        }
        String reason = needsApproval
                ? "Request: bookings of " + approvalFrom + " or more guests need approval"
                : "Request: no table is free for the whole booking";
        reservationSupport.addStatusHistory(reservation, null, ReservationStatus.PENDING, reason, actor.id());
    }

    private OffsetDateTime endOf(Branch branch, ReservationRequest request) {
        if (request.getReservationEnd() != null || request.getReservationStart() == null) {
            return request.getReservationEnd();
        }
        return request.getReservationStart().plus(
                reservationPolicy.bookingLength(branch, request.getReservationStart(), request.getPartySize()));
    }

    // A booking from an earlier service day is a correction: only a manager may change it.
    private void requireCurrentDayOrCorrection(Authentication authentication, Reservation reservation) {
        ZoneId zone = reservationSupport.restaurantZone(reservation.getRestaurant());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (reservationPolicy.serviceDate(reservation.getReservationStart(), zone).isBefore(reservationPolicy.serviceDate(now, zone))
                && !ReservationActor.of(authentication, null).canCorrect()) {
            throw new AuthException(
                    "This booking is from an earlier day. Only a manager can change it now",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Transactional
    public ReservationResponse updateReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        requireCurrentDayOrCorrection(authentication, reservation);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = reservationSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId(), reservation.getBranch().getId());
        Customer customer = reservationSupport.resolveCustomer(restaurantId, request.getCustomerId());

        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setUpdatedBy(actorId);

        reservationSupport.applyReservationRequest(reservation, request, actorId, false, endOf(branch, request));
        reservationOccasionService.apply(reservation, request.getOccasionCode(), request.getOccasionOptions(), request.getOccasionNote());
        if (request.getInitialTableIds() != null) {
            reservationTableAssignmentService.replaceReservationTables(
                    reservation,
                    request.getInitialTableIds(),
                    request.getPrimaryTableId(),
                    actorId
            );
        }

        return reservationSupport.toResponse(reservationSupport.saveReservation(reservation));
    }

    @Transactional
    public ReservationResponse patchReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UpdateReservationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        requireCurrentDayOrCorrection(authentication, reservation);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (request.getBranchId() != null) {
            reservation.setBranch(reservationSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId()));
        }
        if (request.getCustomerId() != null) {
            reservation.setCustomer(reservationSupport.resolveCustomer(restaurantId, request.getCustomerId()));
        }

        reservationSupport.applyReservationPatch(reservation, request, actorId);
        // An occasion change comes with its code; an empty code removes it.
        if (request.getOccasionCode() != null) {
            reservationOccasionService.apply(reservation, request.getOccasionCode(), request.getOccasionOptions(), request.getOccasionNote());
        } else if (request.getOccasionNote() != null) {
            reservation.setOccasionNote(request.getOccasionNote().isBlank() ? null : request.getOccasionNote().trim());
        }
        if (request.getTableIds() != null) {
            reservationTableAssignmentService.replaceReservationTables(
                    reservation,
                    request.getTableIds(),
                    request.getPrimaryTableId(),
                    actorId
            );
        } else if (request.getReservationStart() != null || request.getReservationEnd() != null || request.getPartySize() != null) {
            // New time or party size must still fit the tables it already has.
            reservationTableAssignmentService.revalidateCurrentTables(reservation);
        }

        return reservationSupport.toResponse(reservationSupport.saveReservation(reservation));
    }

    @Transactional
    public void deleteReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        events.publishEvent(new ReservationDeletingEvent(reservation.getId(), restaurantId));
        reservationRepository.delete(reservation);
        reservationRepository.flush();
    }
}
