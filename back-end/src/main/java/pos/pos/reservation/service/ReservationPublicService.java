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

    @Transactional
    public PublicReservationResponse createPublicReservation(
            String restaurantSlug,
            String branchCode,
            PublicReservationRequest request
    ) {
        Branch branch = reservationSupport.requirePublicBranch(restaurantSlug, branchCode);
        Reservation reservation = new Reservation();
        reservation.setRestaurant(branch.getRestaurant());
        reservation.setBranch(branch);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setSource(ReservationSource.WEB);

        reservationSupport.applyPublicReservationRequest(reservation, request);
        reservationSupport.addStatusHistory(reservation, null, ReservationStatus.PENDING, "Reservation created", null);
        return reservationSupport.toPublicResponse(
                reservationSupport.saveReservation(reservation),
                reservation.getTableAssignments()
        );
    }

    @Transactional(readOnly = true)
    public PublicReservationResponse getPublicReservation(String reservationCode) {
        Reservation reservation = reservationSupport.requirePublicReservation(reservationCode);
        return reservationSupport.toPublicResponse(reservation, reservation.getTableAssignments());
    }

    @Transactional
    public PublicReservationResponse cancelPublicReservation(String reservationCode, ReservationActionRequest request) {
        Reservation reservation = reservationSupport.requirePublicReservation(reservationCode);
        reservationLifecycleService.transitionReservation(
                reservation,
                ReservationStatus.CANCELLED,
                request == null ? null : request.getReason(),
                null
        );
        reservationSupport.saveReservation(reservation);
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
