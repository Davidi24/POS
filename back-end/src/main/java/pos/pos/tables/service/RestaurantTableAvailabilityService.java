package pos.pos.tables.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.tables.RestaurantTableNotFoundException;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.service.RestaurantTableSupport.BranchTableSnapshot;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class RestaurantTableAvailabilityService {

    private static final EnumSet<ReservationStatus> RESERVATION_BLOCKING_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationRepository reservationRepository;
    private final RestaurantTableSupport restaurantTableSupport;

    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> getTableAvailability(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer partySize
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        AvailabilityWindow window = resolveAvailabilityWindow(from, to);
        Map<UUID, List<UUID>> overlappingReservationIdsByTableId = loadOverlappingReservationIds(branchId, window);

        return snapshot.tables().stream()
                .map(table -> toAvailabilityResponse(table, snapshot.childrenByParentId(), overlappingReservationIdsByTableId, partySize))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> getAvailableTables(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer partySize
    ) {
        return getTableAvailability(authentication, restaurantId, branchId, from, to, partySize).stream()
                .filter(TableAvailabilityResponse::getAvailableForRequestedWindow)
                .toList();
    }

    @Transactional(readOnly = true)
    public TableAvailabilityResponse getTableAvailability(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer partySize
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        RestaurantTable table = snapshot.tablesById().get(tableId);
        if (table == null) {
            throw new RestaurantTableNotFoundException();
        }

        AvailabilityWindow window = resolveAvailabilityWindow(from, to);
        Map<UUID, List<UUID>> overlappingReservationIdsByTableId = loadOverlappingReservationIds(branchId, window);
        return toAvailabilityResponse(table, snapshot.childrenByParentId(), overlappingReservationIdsByTableId, partySize);
    }

    private AvailabilityWindow resolveAvailabilityWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return new AvailabilityWindow(now, now.plusMinutes(1));
        }

        if (from == null || to == null) {
            throw new AuthException("from and to must both be provided together", HttpStatus.BAD_REQUEST);
        }

        if (!to.isAfter(from)) {
            throw new AuthException("to must be after from", HttpStatus.BAD_REQUEST);
        }

        return new AvailabilityWindow(from, to);
    }

    private Map<UUID, List<UUID>> loadOverlappingReservationIds(UUID branchId, AvailabilityWindow window) {
        List<Reservation> overlappingReservations = reservationRepository
                .findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                        branchId,
                        RESERVATION_BLOCKING_STATUSES,
                        window.to(),
                        window.from()
                );

        Map<UUID, List<UUID>> reservationIdsByTableId = new HashMap<>();
        for (Reservation reservation : overlappingReservations) {
            reservation.getTableAssignments().forEach(assignment -> reservationIdsByTableId
                    .computeIfAbsent(assignment.getRestaurantTable().getId(), ignored -> new ArrayList<>())
                    .add(reservation.getId()));
        }

        return reservationIdsByTableId;
    }

    private TableAvailabilityResponse toAvailabilityResponse(
            RestaurantTable table,
            Map<UUID, List<RestaurantTable>> childrenByParentId,
            Map<UUID, List<UUID>> overlappingReservationIdsByTableId,
            Integer partySize
    ) {
        List<RestaurantTable> mergedChildren = restaurantTableSupport.mergedChildren(table, childrenByParentId);
        int effectiveCapacity = restaurantTableSupport.effectiveCapacity(table, mergedChildren);
        List<UUID> overlappingReservationIds = collectOverlappingReservationIds(table, mergedChildren, overlappingReservationIdsByTableId);
        boolean operationallyAvailable = table.isActive()
                && table.getMergedInto() == null
                && table.getStatus() == TableStatus.AVAILABLE;
        boolean availableForRequestedWindow = operationallyAvailable
                && (partySize == null || effectiveCapacity >= partySize)
                && overlappingReservationIds.isEmpty();

        return restaurantTableSupport.toAvailabilityResponse(
                table,
                childrenByParentId,
                operationallyAvailable,
                availableForRequestedWindow,
                determineBlockingReason(table, effectiveCapacity, partySize, overlappingReservationIds),
                overlappingReservationIds
        );
    }

    private List<UUID> collectOverlappingReservationIds(
            RestaurantTable table,
            List<RestaurantTable> mergedChildren,
            Map<UUID, List<UUID>> overlappingReservationIdsByTableId
    ) {
        LinkedHashSet<UUID> reservationIds = new LinkedHashSet<>(overlappingReservationIdsByTableId.getOrDefault(table.getId(), List.of()));
        for (RestaurantTable mergedChild : mergedChildren) {
            reservationIds.addAll(overlappingReservationIdsByTableId.getOrDefault(mergedChild.getId(), List.of()));
        }
        return List.copyOf(reservationIds);
    }

    private String determineBlockingReason(
            RestaurantTable table,
            int effectiveCapacity,
            Integer partySize,
            List<UUID> overlappingReservationIds
    ) {
        if (!table.isActive()) {
            return "INACTIVE";
        }
        if (table.getMergedInto() != null) {
            return "MERGED_INTO_OTHER_TABLE";
        }
        if (table.getStatus() != TableStatus.AVAILABLE) {
            return table.getStatus().name();
        }
        if (partySize != null && effectiveCapacity < partySize) {
            return "INSUFFICIENT_CAPACITY";
        }
        if (!overlappingReservationIds.isEmpty()) {
            return "RESERVED_FOR_REQUESTED_WINDOW";
        }
        return null;
    }

    private record AvailabilityWindow(OffsetDateTime from, OffsetDateTime to) {
    }
}
