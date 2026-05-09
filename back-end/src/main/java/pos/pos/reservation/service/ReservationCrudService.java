package pos.pos.reservation.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.entity.Customer;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.UpdateReservationRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationCrudService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationRepository reservationRepository;
    private final ReservationSupport reservationSupport;
    private final ReservationTableAssignmentService reservationTableAssignmentService;

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

        reservationSupport.applyReservationRequest(reservation, request, actorId, true);
        reservationSupport.addStatusHistory(reservation, null, ReservationStatus.PENDING, "Reservation created", actorId);

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
    public ReservationResponse updateReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = reservationSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId(), reservation.getBranch().getId());
        Customer customer = reservationSupport.resolveCustomer(restaurantId, request.getCustomerId());

        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setUpdatedBy(actorId);

        reservationSupport.applyReservationRequest(reservation, request, actorId, false);
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
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (request.getBranchId() != null) {
            reservation.setBranch(reservationSupport.resolveManagedBranch(authentication, restaurantId, request.getBranchId()));
        }
        if (request.getCustomerId() != null) {
            reservation.setCustomer(reservationSupport.resolveCustomer(restaurantId, request.getCustomerId()));
        }

        reservationSupport.applyReservationPatch(reservation, request, actorId);
        if (request.getTableIds() != null) {
            reservationTableAssignmentService.replaceReservationTables(
                    reservation,
                    request.getTableIds(),
                    request.getPrimaryTableId(),
                    actorId
            );
        }

        return reservationSupport.toResponse(reservationSupport.saveReservation(reservation));
    }

    @Transactional
    public void deleteReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        reservationRepository.delete(reservationSupport.requireReservation(restaurantId, reservationId));
        reservationRepository.flush();
    }
}
