package pos.pos.reservation.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.dto.UpdateReservationTablesRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationTableAssignmentService {

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    private final ReservationAvailabilitySupport reservationAvailabilitySupport;
    private final ReservationSupport reservationSupport;

    @Transactional
    public List<ReservationTableAssignmentResponse> updateReservationTables(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UpdateReservationTablesRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        replaceReservationTables(
                reservation,
                request.getTableIds(),
                request.getPrimaryTableId(),
                restaurantScopeService.currentUserId(authentication)
        );
        reservationSupport.saveReservation(reservation);
        return reservationSupport.mapAssignments(reservation);
    }

    @Transactional
    public ReservationTableAssignmentResponse addReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID tableId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);

        if (reservation.getTableAssignments().stream()
                .anyMatch(assignment -> Objects.equals(assignment.getRestaurantTable().getId(), tableId))) {
            throw new AuthException("Table is already assigned to this reservation", HttpStatus.CONFLICT);
        }

        List<UUID> selectedTableIds = new ArrayList<>(reservation.getTableAssignments().stream()
                .map(assignment -> assignment.getRestaurantTable().getId())
                .toList());
        selectedTableIds.add(tableId);

        ReservationAvailabilitySupport.SelectionValidationResult validation = reservationAvailabilitySupport.validateTableSelection(
                reservation.getBranch(),
                reservation.getId(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                selectedTableIds,
                reservationSupport.currentPrimaryTableId(reservation)
        );

        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(validation.selectedTables().get(tableId));
        assignment.setPrimaryAssignment(reservation.getTableAssignments().stream().noneMatch(ReservationTableAssignment::isPrimaryAssignment));
        assignment.setAssignedBy(restaurantScopeService.currentUserId(authentication));
        reservation.addTableAssignment(assignment);
        reservationSupport.saveReservation(reservation);

        return reservationSupport.toTableAssignmentResponse(
                reservationTableAssignmentRepository.findByReservation_IdAndRestaurantTable_Id(reservationId, tableId)
                        .orElseThrow(() -> new AuthException("Table assignment could not be created", HttpStatus.INTERNAL_SERVER_ERROR))
        );
    }

    @Transactional
    public void deleteReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID tableId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);

        ReservationTableAssignment assignment = reservation.getTableAssignments().stream()
                .filter(item -> Objects.equals(item.getRestaurantTable().getId(), tableId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Table is not assigned to this reservation", HttpStatus.NOT_FOUND));

        boolean removingPrimary = assignment.isPrimaryAssignment();
        reservation.removeTableAssignment(assignment);

        if (removingPrimary && !reservation.getTableAssignments().isEmpty()) {
            reservation.getTableAssignments().get(0).setPrimaryAssignment(true);
        }

        reservationSupport.saveReservation(reservation);
    }

    @Transactional
    public List<ReservationTableAssignmentResponse> markPrimaryReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID tableId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);

        ReservationTableAssignment assignment = reservation.getTableAssignments().stream()
                .filter(item -> Objects.equals(item.getRestaurantTable().getId(), tableId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Table is not assigned to this reservation", HttpStatus.NOT_FOUND));

        reservation.getTableAssignments().forEach(item -> item.setPrimaryAssignment(false));
        assignment.setPrimaryAssignment(true);
        reservationSupport.saveReservation(reservation);
        return reservationSupport.mapAssignments(reservation);
    }

    @Transactional
    public List<ReservationTableAssignmentResponse> autoAssignReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        List<ReservationAvailabilityOptionResponse> options = reservationAvailabilitySupport.availabilityOptionsForBranch(
                reservation.getBranch(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                1
        );

        if (options.isEmpty()) {
            throw new AuthException("No available table combination can be auto-assigned", HttpStatus.CONFLICT);
        }

        ReservationAvailabilityOptionResponse bestOption = options.get(0);
        replaceReservationTables(
                reservation,
                bestOption.getTableIds(),
                bestOption.getPrimaryTableId(),
                restaurantScopeService.currentUserId(authentication)
        );
        reservationSupport.saveReservation(reservation);
        return reservationSupport.mapAssignments(reservation);
    }

    // ---- Settings → Tables → Special cases ----

    // Free tables the booking fits, for the time it has left.
    public List<ReservationAvailabilityOptionResponse> freeOptions(Reservation reservation) {
        OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
        OffsetDateTime from = reservation.getReservationStart().isAfter(now) ? reservation.getReservationStart() : now;
        if (!reservation.getReservationEnd().isAfter(from)) {
            return List.of();
        }
        var current = reservation.getTableAssignments().stream().map(assignment -> assignment.getRestaurantTable().getId()).toList();
        return reservationAvailabilitySupport.availabilityOptionsForBranch(reservation.getBranch(), from, reservation.getReservationEnd(), reservation.getPartySize(), 12)
                .stream()
                .filter(option -> option.getTableIds().stream().noneMatch(current::contains))
                .toList();
    }

    public void moveTo(Reservation reservation, Collection<UUID> tableIds, UUID primaryTableId, UUID actorId) {
        replaceReservationTables(reservation, tableIds, primaryTableId, actorId);
    }

    // The system never joins tables by itself, and stays on the floor: only one table there that fits. Answers its number,
    // or null when none does.
    public String moveToFreeTable(Reservation reservation, java.util.function.Predicate<UUID> allowed) {
        for (ReservationAvailabilityOptionResponse option : freeOptions(reservation)) {
            if (option.getTableIds().size() != 1 || !allowed.test(option.getTableIds().get(0))) {
                continue;
            }
            try {
                replaceReservationTables(reservation, option.getTableIds(), option.getPrimaryTableId(), null);
                return option.getTableNumbers().get(0);
            } catch (AuthException taken) {
                // Taken in the meantime: try the next one.
            }
        }
        return null;
    }

    // Back on the table it came from, guests there or not: staff asked for it.
    public void putBack(Reservation reservation, pos.pos.tables.entity.RestaurantTable table, UUID actorId) {
        new ArrayList<>(reservation.getTableAssignments()).forEach(reservation::removeTableAssignment);
        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(table);
        assignment.setPrimaryAssignment(true);
        assignment.setAssignedBy(actorId);
        reservation.addTableAssignment(assignment);
    }

    void replaceReservationTables(Reservation reservation, Collection<UUID> tableIds, UUID primaryTableId, UUID actorId) {
        if (tableIds == null) {
            return;
        }

        if (tableIds.isEmpty()) {
            new ArrayList<>(reservation.getTableAssignments()).forEach(reservation::removeTableAssignment);
            return;
        }

        ReservationAvailabilitySupport.SelectionValidationResult validation = reservationAvailabilitySupport.validateTableSelection(
                reservation.getBranch(),
                reservation.getId(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                tableIds,
                primaryTableId
        );

        new ArrayList<>(reservation.getTableAssignments()).forEach(reservation::removeTableAssignment);

        for (UUID tableId : validation.selectedTableIds()) {
            ReservationTableAssignment assignment = new ReservationTableAssignment();
            assignment.setRestaurantTable(validation.selectedTables().get(tableId));
            assignment.setPrimaryAssignment(Objects.equals(tableId, validation.primaryTableId()));
            assignment.setAssignedBy(actorId);
            reservation.addTableAssignment(assignment);
        }
    }

    // Re-checks the reservation's current tables after its time or party size changed.
    public void revalidateCurrentTables(Reservation reservation) {
        List<UUID> tableIds = reservation.getTableAssignments().stream()
                .map(assignment -> assignment.getRestaurantTable().getId())
                .toList();
        if (tableIds.isEmpty()) {
            return;
        }
        UUID primaryTableId = reservation.getTableAssignments().stream()
                .filter(ReservationTableAssignment::isPrimaryAssignment)
                .map(assignment -> assignment.getRestaurantTable().getId())
                .findFirst()
                .orElse(tableIds.getFirst());
        reservationAvailabilitySupport.validateTableSelection(
                reservation.getBranch(),
                reservation.getId(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                tableIds,
                primaryTableId
        );
    }
}
