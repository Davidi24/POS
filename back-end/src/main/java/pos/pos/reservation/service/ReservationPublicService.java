package pos.pos.reservation.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.PublicReservationRequest;
import pos.pos.reservation.dto.PublicReservationResponse;
import pos.pos.reservation.dto.PublicTableLookupResponse;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationSource;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.service.RestaurantTableSupport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationPublicService {

    private static final int DEFAULT_AVAILABILITY_LIMIT = 10;

    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantTableSupport restaurantTableSupport;
    private final ReservationAvailabilitySupport reservationAvailabilitySupport;
    private final ReservationLifecycleService reservationLifecycleService;
    private final ReservationSupport reservationSupport;
    private final ReservationNotifications reservationNotifications;
    private final GuestBookingService guestBookingService;
    private final pos.pos.reservation.repository.ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    public List<ReservationAvailabilityOptionResponse> getPublicAvailability(
            String restaurantSlug,
            String branchCode,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            Integer partySize,
            Integer maxOptions
    ) {
        Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
        if (partySize == null || partySize <= 0) {
            throw new AuthException("partySize must be greater than 0", HttpStatus.BAD_REQUEST);
        }
        return reservationAvailabilitySupport.availabilityOptionsForBranch(
                branch,
                reservationStart,
                reservationEnd,
                partySize,
                reservationSupport.resolveAvailabilityLimit(maxOptions, DEFAULT_AVAILABILITY_LIMIT)
        );
    }

    // Same rules as the guest booking pages: confirmed when a table is free, otherwise a request.
    @Transactional
    public PublicReservationResponse createPublicReservation(
            String restaurantSlug,
            String branchCode,
            PublicReservationRequest request
    ) {
        var booked = guestBookingService.book(restaurantSlug, branchCode, pos.pos.reservation.dto.OnlineBookingRequest.builder()
                .partySize(request.getPartySize())
                .reservationStart(request.getReservationStart())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .contactEmail(request.getContactEmail())
                .specialRequests(request.getSpecialRequests())
                .build());
        Reservation reservation = reservationRepository.findByGuestToken(booked.getToken())
                .orElseThrow(() -> new AuthException("Reservation not found", HttpStatus.NOT_FOUND));
        return reservationSupport.toPublicResponse(reservation, reservation.getTableAssignments());
    }

    @Transactional(readOnly = true)
    public PublicReservationResponse getPublicReservation(String reservationCode) {
        Reservation reservation = reservationSupport.requirePublicReservation(reservationCode);
        return reservationSupport.toPublicResponse(reservation, reservation.getTableAssignments());
    }

    @Transactional
    public PublicReservationResponse cancelPublicReservation(String reservationCode, ReservationActionRequest request) {
        Reservation reservation = reservationSupport.requirePublicReservation(reservationCode);
        // Guests cancel for free any time before they arrive; after that only staff can.
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new AuthException("This booking can't be cancelled online anymore", HttpStatus.BAD_REQUEST);
        }
        reservationLifecycleService.cancel(
                reservation,
                request == null || request.getReason() == null || request.getReason().isBlank() ? "Cancelled by the guest" : request.getReason().trim(),
                ReservationActor.system(null)
        );
        reservationSupport.saveReservation(reservation);
        reservationNotifications.cancelled(reservation, null);
        return reservationSupport.toPublicResponse(reservation, reservation.getTableAssignments());
    }

    @Transactional(readOnly = true)
    public PublicTableLookupResponse getPublicTable(String qrCodeValue) {
        RestaurantTable table = restaurantTableRepository.findFirstByQrCodeValueAndActiveTrue(qrCodeValue)
                .orElseThrow(() -> new AuthException("Table not found", HttpStatus.NOT_FOUND));
        reservationSupport.assertPublicAvailability(table.getRestaurant(), table.getBranch());
        Map<UUID, List<RestaurantTable>> children = restaurantTableSupport.loadChildMap(table.getId());
        int effectiveCapacity = restaurantTableSupport.effectiveCapacity(
                table,
                children.getOrDefault(table.getId(), List.of())
        );
        return reservationSupport.toPublicTableLookupResponse(table, effectiveCapacity);
    }
}
