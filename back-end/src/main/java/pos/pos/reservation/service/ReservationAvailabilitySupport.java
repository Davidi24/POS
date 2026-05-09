package pos.pos.reservation.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.service.RestaurantTableSupport;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@lombok.RequiredArgsConstructor
public class ReservationAvailabilitySupport {

    private static final EnumSet<ReservationStatus> BLOCKING_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );
    private static final int MAX_COMBINATION_DEPTH = 4;

    private final ReservationRepository reservationRepository;
    private final RestaurantTableSupport restaurantTableSupport;

    List<ReservationAvailabilityOptionResponse> availabilityOptionsForBranch(
            Branch branch,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            int partySize,
            int limit
    ) {
        validateReservationWindow(reservationStart, reservationEnd);
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(
                branch.getRestaurant().getId(),
                branch.getId()
        );
        List<AvailableRootTable> availableRootTables = loadAvailableRootTables(
                branch,
                snapshot,
                reservationStart,
                reservationEnd
        );
        return buildAvailabilityOptions(availableRootTables, partySize, limit);
    }

    SelectionValidationResult validateTableSelection(
            Branch branch,
            UUID reservationId,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            int partySize,
            Collection<UUID> rawTableIds,
            UUID primaryTableId
    ) {
        validateReservationWindow(reservationStart, reservationEnd);

        LinkedHashSet<UUID> uniqueTableIds = new LinkedHashSet<>(rawTableIds);
        if (uniqueTableIds.size() != rawTableIds.size()) {
            throw new AuthException("tableIds must not contain duplicates", HttpStatus.BAD_REQUEST);
        }

        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(
                branch.getRestaurant().getId(),
                branch.getId()
        );
        Map<UUID, RestaurantTable> selectedTables = requireSelectableTables(snapshot, uniqueTableIds);

        UUID resolvedPrimaryTableId = primaryTableId == null ? uniqueTableIds.getFirst() : primaryTableId;
        if (!selectedTables.containsKey(resolvedPrimaryTableId)) {
            throw new AuthException("primaryTableId must reference one of the selected tableIds", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> unavailableTableIds = overlappingAssignedTableIds(
                branch.getId(),
                reservationStart,
                reservationEnd,
                reservationId
        );
        for (RestaurantTable table : selectedTables.values()) {
            if (unavailableTableIds.contains(table.getId())) {
                throw new AuthException("Selected tables overlap with another reservation in the requested window", HttpStatus.CONFLICT);
            }
            for (RestaurantTable child : snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())) {
                if (unavailableTableIds.contains(child.getId())) {
                    throw new AuthException("Selected tables overlap with another reservation in the requested window", HttpStatus.CONFLICT);
                }
            }
        }

        int effectiveCapacity = selectedTables.values().stream()
                .mapToInt(table -> restaurantTableSupport.effectiveCapacity(
                        table,
                        snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())
                ))
                .sum();
        if (effectiveCapacity < partySize) {
            throw new AuthException("Selected tables do not provide enough capacity for this reservation", HttpStatus.BAD_REQUEST);
        }

        return new SelectionValidationResult(List.copyOf(uniqueTableIds), selectedTables, resolvedPrimaryTableId);
    }


    // Returns the root/main tables that are available for a reservation in the given time window.
    // It first gets all table IDs already used by overlapping reservations.
    // Then it checks all branch tables and keeps only:
    // - root tables, not merged child tables
    // - active tables
    // - tables with AVAILABLE status
    // - tables that are not already reserved
    // - tables whose merged child tables are also not already reserved
    //
    // Example:
    // Table 1 has child Table 2.
    // If Table 2 is already used by another reservation,
    // then Table 1 is also blocked.
    // So this check prevents the system from accidentally offering a merged table group when part of it is already occupied.
    //
    // Final result:
    // List of available root tables with their effective capacity,
    // sorted by smallest capacity first, then by table number.
    List<AvailableRootTable> loadAvailableRootTables(
            Branch branch,
            RestaurantTableSupport.BranchTableSnapshot snapshot,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd
    ) {
        Set<UUID> unavailableTableIds = overlappingAssignedTableIds(
                branch.getId(),
                reservationStart,
                reservationEnd,
                null
        );

        return snapshot.tables().stream()
                // It keeps only root/main tables.
                .filter(table -> table.getMergedInto() == null)
                .filter(RestaurantTable::isActive)
                .filter(table -> table.getStatus() == TableStatus.AVAILABLE)
                .map(table -> {
                    List<RestaurantTable> children = snapshot.childrenByParentId().getOrDefault(table.getId(), List.of());
                    boolean blocked = unavailableTableIds.contains(table.getId())
                            || children.stream().anyMatch(child -> unavailableTableIds.contains(child.getId()));
                    if (blocked) {
                        return null;
                    }
                    return new AvailableRootTable(
                            table,
                            restaurantTableSupport.effectiveCapacity(table, children)
                    );
                })
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(AvailableRootTable::effectiveCapacity)
                        .thenComparing(table -> table.table().getTableNumber(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // It finds table IDs that are already reserved in the same time window.
    // Meaning: it checks if another reservation overlaps with the requested start/end time, then collects the tables used by those reservations.
    // Example:
    // Existing reservation: 17:30 - 19:00
    // New reservation:      18:00 - 20:00
    // Overlap exists → table is unavailable
    private Set<UUID> overlappingAssignedTableIds(
            UUID branchId,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            UUID currentReservationId
    ) {
        return reservationRepository.findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                        branchId,
                        BLOCKING_STATUSES,
                        reservationEnd,
                        reservationStart
                ).stream()
                .filter(reservation -> !Objects.equals(reservation.getId(), currentReservationId))
                .flatMap(reservation -> reservation.getTableAssignments().stream())
                .map(assignment -> assignment.getRestaurantTable().getId())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private List<ReservationAvailabilityOptionResponse> buildAvailabilityOptions(
            List<AvailableRootTable> availableRootTables,
            int partySize,
            int limit
    ) {
        List<TableCombination> combinations = new ArrayList<>();
        buildAvailabilityOptionsDepthFirst(
                availableRootTables,
                partySize,
                limit,
                0,
                new ArrayList<>(),
                0,
                combinations,
                new HashSet<>()
        );

        return combinations.stream()
                .sorted(Comparator
                        .comparingInt(TableCombination::tableCount)
                        .thenComparingInt(combination -> combination.totalCapacity() - partySize)
                        .thenComparing(TableCombination::tableNumbersKey, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(combination -> ReservationAvailabilityOptionResponse.builder()
                        .tableIds(combination.tableIds())
                        .tableNumbers(combination.tableNumbers())
                        .primaryTableId(combination.tableIds().getFirst())
                        .tableCount(combination.tableCount())
                        .totalCapacity(combination.totalCapacity())
                        .exactFit(combination.totalCapacity() == partySize)
                        .build())
                .toList();
    }

    private Map<UUID, RestaurantTable> requireSelectableTables(
            RestaurantTableSupport.BranchTableSnapshot snapshot,
            Collection<UUID> tableIds
    ) {
        Map<UUID, RestaurantTable> tablesById = snapshot.tablesById();
        Map<UUID, RestaurantTable> selectedTables = new LinkedHashMap<>();
        for (UUID tableId : tableIds) {
            RestaurantTable table = tablesById.get(tableId);
            if (table == null) {
                throw new AuthException("tableIds must only reference tables in this branch", HttpStatus.BAD_REQUEST);
            }
            if (table.getMergedInto() != null) {
                throw new AuthException("Merged child tables cannot be assigned directly", HttpStatus.BAD_REQUEST);
            }
            if (!table.isActive()) {
                throw new AuthException("Inactive tables cannot be assigned", HttpStatus.BAD_REQUEST);
            }
            if (table.getStatus() != TableStatus.AVAILABLE) {
                throw new AuthException("Only AVAILABLE tables can be assigned", HttpStatus.BAD_REQUEST);
            }
            selectedTables.put(tableId, table);
        }
        return selectedTables;
    }

    private void buildAvailabilityOptionsDepthFirst(
            List<AvailableRootTable> availableRootTables,
            int partySize,
            int limit,
            int startIndex,
            List<AvailableRootTable> currentSelection,
            int currentCapacity,
            List<TableCombination> combinations,
            Set<String> seenKeys
    ) {
        if (currentCapacity >= partySize) {
            List<UUID> tableIds = currentSelection.stream().map(selection -> selection.table().getId()).toList();
            List<String> tableNumbers = currentSelection.stream().map(selection -> selection.table().getTableNumber()).toList();
            String key = tableIds.stream().map(UUID::toString).collect(Collectors.joining("|"));
            if (seenKeys.add(key)) {
                combinations.add(new TableCombination(
                        tableIds,
                        tableNumbers,
                        currentSelection.size(),
                        currentCapacity,
                        String.join("|", tableNumbers)
                ));
            }
            return;
        }

        if (currentSelection.size() >= MAX_COMBINATION_DEPTH || combinations.size() >= limit * 4) {
            return;
        }

        for (int index = startIndex; index < availableRootTables.size(); index++) {
            currentSelection.add(availableRootTables.get(index));
            buildAvailabilityOptionsDepthFirst(
                    availableRootTables,
                    partySize,
                    limit,
                    index + 1,
                    currentSelection,
                    currentCapacity + availableRootTables.get(index).effectiveCapacity(),
                    combinations,
                    seenKeys
            );
            currentSelection.removeLast();
        }
    }

    // checks if reservationStart is after reservationEnd
    private void validateReservationWindow(OffsetDateTime reservationStart, OffsetDateTime reservationEnd) {
        requireCompleteWindow(reservationStart, reservationEnd);
        if (!reservationEnd.isAfter(reservationStart)) {
            throw new AuthException("reservationEnd must be after reservationStart", HttpStatus.BAD_REQUEST);
        }
    }

    // check if reservationStart and end are both present not onle of them
    private void requireCompleteWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new AuthException("reservationStart and to reservationEnd both be provided together", HttpStatus.BAD_REQUEST);
        }
    }

    record SelectionValidationResult(
            List<UUID> selectedTableIds,
            Map<UUID, RestaurantTable> selectedTables,
            UUID primaryTableId
    ) {
    }

    record AvailableRootTable(
            RestaurantTable table,
            int effectiveCapacity
    ) {
    }

    private record TableCombination(
            List<UUID> tableIds,
            List<String> tableNumbers,
            int tableCount,
            int totalCapacity,
            String tableNumbersKey
    ) {
    }
}
