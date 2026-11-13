package pos.pos.tables.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderEventType;
import pos.pos.order.enums.OrderPaymentStatus;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.repository.OrderRepository;
import pos.pos.order.service.OrderSupport;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.dto.AutoArrangeTableLayoutRequest;
import pos.pos.tables.dto.FloorSummaryResponse;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.dto.TableLayoutResponse;
import pos.pos.tables.dto.TableMapResponse;
import pos.pos.tables.dto.TableMergeRequest;
import pos.pos.tables.dto.TableRequest;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.dto.UpdateFloorsRequest;
import pos.pos.tables.dto.UpdateTableLayoutRequest;
import pos.pos.tables.dto.UpdateTablePositionRequest;
import pos.pos.tables.dto.UpdateTableQrCodeRequest;
import pos.pos.tables.dto.UpdateTableStatusRequest;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.event.TableStatusChangedEvent;
import pos.pos.tables.repository.RestaurantTableRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class RestaurantTableService {

    // A table takes in tables of its own kind or lower; a free one doesn't take in a reserved one (user, 2026-11-01).
    private static final int JOIN_FREE = 0;
    private static final int JOIN_RESERVED = 1;
    private static final int JOIN_SEATED = 2;
    private static final int JOIN_ORDER = 3;

    private final RestaurantScopeService restaurantScopeService;
    private final ApplicationEventPublisher events;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    private final RestaurantTableSupport restaurantTableSupport;
    private final RestaurantTableLayoutService restaurantTableLayoutService;
    private final RestaurantTableAvailabilityService restaurantTableAvailabilityService;
    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final TableSeatingRules tableSeatingRules;
    private final pos.pos.order.service.TableOrderCombiner tableOrderCombiner;

    /**
     * First, we check if the logged-in user is allowed to access this restaurant branch.
     * <p>
     * Then we load all tables of this branch using loadBranchTables().
     * That method returns a BranchTableSnapshot, which contains:
     * <p>
     * 1. restaurantId
     * 2. branchId
     * 3. tables
     *    -> the full list of tables in this branch
     * <p>
     * 4. childrenByParentId
     *    -> parent table ID -> merged child tables
     *    -> example: A1_id -> [A2, A3]
     * <p>
     * 5. tablesById
     *    -> table ID -> table
     *    -> example: A1_id -> A1
     * <p>
     * Then we convert each table to a TableResponse.
     * If a table has merged child tables, their IDs are added to the response.
     * The effective capacity is also calculated:
     * parent table capacity + merged child tables capacity.
     */
    @Transactional(readOnly = true)
    public List<TableResponse> getTables(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        return snapshot.tables().stream()
                .map(table -> restaurantTableSupport.toResponse(table, snapshot.childrenByParentId()))
                .toList();
    }

    @Transactional
    public TableResponse createTable(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            TableRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(branch.getRestaurant());
        table.setBranch(branch);
        table.setCreatedBy(actorId);
        table.setUpdatedBy(actorId);

        restaurantTableSupport.applyTableRequest(branchId, table, request);
        return restaurantTableSupport.toResponse(restaurantTableSupport.saveTable(table), Map.of());
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);
        return restaurantTableSupport.toResponse(table, restaurantTableSupport.loadChildMap(tableId));
    }

    @Transactional
    public TableResponse updateTable(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            TableRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);
        table.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        restaurantTableSupport.applyTableRequest(branchId, table, request);
        return restaurantTableSupport.toResponse(
                restaurantTableSupport.saveTable(table),
                restaurantTableSupport.loadChildMap(tableId)
        );
    }

    @Transactional
    public TableResponse updateTableStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            UpdateTableStatusRequest request
    ) {
        return updateOperationalStatus(
                authentication,
                restaurantId,
                branchId,
                tableId,
                request.getStatus(),
                request.getGuestCount(),
                request.getMoreComing()
        );
    }

    @Transactional
    public TableResponse updateTablePosition(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            UpdateTablePositionRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);
        if (request.getFloor() != null) {
            table.setFloor(request.getFloor());
        }
        if (request.getPositionX() != null || request.getPositionY() != null) {
            restaurantTableSupport.validatePositionPair(
                    request.getPositionX(),
                    request.getPositionY(),
                    "positionX and positionY must both be set together"
            );
            table.setPositionX(request.getPositionX());
            table.setPositionY(request.getPositionY());
        }
        if (request.getRotationDegrees() != null) {
            table.setRotationDegrees(request.getRotationDegrees());
        }
        if (request.getLayoutScale() != null) {
            table.setLayoutScale(request.getLayoutScale());
        }
        table.setLayoutOnly(true);
        table.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return restaurantTableSupport.toResponse(
                restaurantTableSupport.saveTable(table),
                restaurantTableSupport.loadChildMap(tableId)
        );
    }

    @Transactional
    public TableResponse updateTableQrCode(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            UpdateTableQrCodeRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);
        table.setQrCodeValue(request.getQrCodeValue());
        table.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return restaurantTableSupport.toResponse(
                restaurantTableSupport.saveTable(table),
                restaurantTableSupport.loadChildMap(tableId)
        );
    }

    @Transactional
    public void deleteTable(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);

        // if the table is reserved you can not delete it.
        if (reservationTableAssignmentRepository.existsByRestaurantTable_Id(tableId)) {
            throw new AuthException("Cannot delete a table that already has reservation assignments", HttpStatus.CONFLICT);
        }

        if (table.getMergedInto() != null) {
            throw new AuthException("Cannot delete a table while it is merged into another table", HttpStatus.CONFLICT);
        }

        if (restaurantTableRepository.existsByMergedInto_Id(tableId)) {
            throw new AuthException("Cannot delete a table while other tables are merged into it", HttpStatus.CONFLICT);
        }

        restaurantTableRepository.delete(table);
        restaurantTableRepository.flush();
    }

    @Transactional(readOnly = true)
    public TableLayoutResponse getTableLayout(Authentication authentication, UUID restaurantId, UUID branchId) {
        return restaurantTableLayoutService.getTableLayout(authentication, restaurantId, branchId);
    }

    @Transactional
    public TableLayoutResponse updateTableLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateTableLayoutRequest request
    ) {
        return restaurantTableLayoutService.updateTableLayout(authentication, restaurantId, branchId, request);
    }

    @Transactional
    public TableLayoutResponse autoArrangeTableLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            AutoArrangeTableLayoutRequest request
    ) {
        return restaurantTableLayoutService.autoArrangeTableLayout(authentication, restaurantId, branchId, request);
    }

    @Transactional(readOnly = true)
    public List<FloorSummaryResponse> getFloors(Authentication authentication, UUID restaurantId, UUID branchId) {
        return restaurantTableLayoutService.getFloors(authentication, restaurantId, branchId);
    }

    @Transactional
    public List<FloorSummaryResponse> updateFloors(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateFloorsRequest request
    ) {
        return restaurantTableLayoutService.updateFloors(authentication, restaurantId, branchId, request);
    }

    @Transactional(readOnly = true)
    public TableMapResponse getTableMap(Authentication authentication, UUID restaurantId, UUID branchId) {
        return restaurantTableLayoutService.getTableMap(authentication, restaurantId, branchId);
    }

    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> getTableAvailability(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer partySize
    ) {
        return restaurantTableAvailabilityService.getTableAvailability(authentication, restaurantId, branchId, from, to, partySize);
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
        return restaurantTableAvailabilityService.getAvailableTables(authentication, restaurantId, branchId, from, to, partySize);
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
        return restaurantTableAvailabilityService.getTableAvailability(authentication, restaurantId, branchId, tableId, from, to, partySize);
    }

    @Transactional
    public TableResponse blockTable(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        return updateOperationalStatus(authentication, restaurantId, branchId, tableId, TableStatus.OUT_OF_SERVICE, null);
    }

    @Transactional
    public TableResponse unblockTable(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        return updateOperationalStatus(authentication, restaurantId, branchId, tableId, TableStatus.AVAILABLE, null);
    }

    @Transactional
    public TableResponse markTableClean(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        return updateOperationalStatus(authentication, restaurantId, branchId, tableId, TableStatus.AVAILABLE, null);
    }

    @Transactional
    public TableResponse markTableDirty(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        return updateOperationalStatus(authentication, restaurantId, branchId, tableId, TableStatus.DIRTY, null);
    }

    @Transactional
    public TableResponse mergeTable(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            TableMergeRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        Set<UUID> uniqueIds = new LinkedHashSet<>(request.getTableIds());
        if (uniqueIds.contains(tableId)) {
            throw new AuthException("tableIds must not include the primary table id", HttpStatus.BAD_REQUEST);
        }

        if (uniqueIds.size() != request.getTableIds().size()) {
            throw new AuthException("tableIds must not contain duplicates", HttpStatus.BAD_REQUEST);
        }

        // Every merge locks all participants in the same database order. Locking
        // the primary first and targets second can deadlock overlapping A→B/B→A requests.
        List<UUID> participantIds = new java.util.ArrayList<>(uniqueIds);
        participantIds.add(tableId);
        restaurantTableRepository.lockTablesForUpdateInStableOrder(branchId, participantIds);

        RestaurantTable primaryTable = requireTableForUpdate(branchId, tableId);
        if (primaryTable.getMergedInto() != null) {
            throw new AuthException(
                    primaryTable.getTableNumber() + " is already merged with "
                            + primaryTable.getMergedInto().getTableNumber() + ". Edit the existing table group instead.",
                    HttpStatus.CONFLICT
            );
        }

        List<RestaurantTable> mergeTargets = restaurantTableRepository.findAllByBranchIdAndIdsForUpdate(branchId, uniqueIds);
        if (mergeTargets.size() != uniqueIds.size()) {
            throw new AuthException("tableIds must all belong to this branch", HttpStatus.BAD_REQUEST);
        }

        validateMergeParticipant(primaryTable);
        int startRank = joinRank(primaryTable);
        for (RestaurantTable mergeTarget : mergeTargets) {
            validateMergeTarget(mergeTarget);
            validateMergeParticipant(mergeTarget);
            if (!Objects.equals(primaryTable.getFloor(), mergeTarget.getFloor())) {
                throw new AuthException("Tables on different floors cannot be merged.", HttpStatus.BAD_REQUEST);
            }
            int targetRank = joinRank(mergeTarget);
            if (targetRank > startRank) {
                String number = mergeTarget.getTableNumber();
                throw new AuthException(
                        number + (targetRank == JOIN_ORDER ? " has an order" : targetRank == JOIN_SEATED ? " has guests seated" : " is reserved")
                                + ". Start from " + number + " to join these tables.",
                        HttpStatus.CONFLICT
                );
            }
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        for (RestaurantTable mergeTarget : mergeTargets) {
            mergeTarget.setMergedInto(primaryTable);
            mergeTarget.setUpdatedBy(actorId);
        }

        restaurantTableSupport.saveTables(mergeTargets);
        moveOpenOrdersToMergedPrimary(primaryTable, mergeTargets, actorId);
        return restaurantTableSupport.toResponse(primaryTable, restaurantTableSupport.loadChildMap(tableId));
    }

    @Transactional
    public TableResponse unmergeTable(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (table.getMergedInto() != null) {
            table.setMergedInto(null);
            table.setUpdatedBy(actorId);
            return restaurantTableSupport.toResponse(restaurantTableSupport.saveTable(table), Map.of());
        }

        List<RestaurantTable> mergedChildren = restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(tableId);
        for (RestaurantTable mergedChild : mergedChildren) {
            mergedChild.setMergedInto(null);
            mergedChild.setUpdatedBy(actorId);
        }

        restaurantTableSupport.saveTables(mergedChildren);
        return restaurantTableSupport.toResponse(table, Map.of());
    }

    /**
     * Some tables leave a merged group (user, 2026-11-01): any of them, the main one too. Those that leave become free;
     * the guests and the open orders go to the table that keeps them, which leads what's left of the group. Each order's
     * history says which tables were freed and where it went.
     */
    @Transactional
    public TableResponse unmergeTables(Authentication authentication, UUID restaurantId, UUID branchId, UUID tableId,
                                       List<UUID> freeTableIds, UUID keepTableId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable picked = requireTableForUpdate(branchId, tableId);
        RestaurantTable head = picked.getMergedInto() == null ? picked : requireTableForUpdate(branchId, picked.getMergedInto().getId());
        List<RestaurantTable> group = new java.util.ArrayList<>(List.of(head));
        group.addAll(restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(head.getId()));
        if (group.size() < 2) {
            throw new AuthException("These tables aren't merged", HttpStatus.BAD_REQUEST);
        }
        java.util.Set<UUID> leaving = new java.util.HashSet<>(freeTableIds);
        if (!group.stream().map(RestaurantTable::getId).collect(java.util.stream.Collectors.toSet()).containsAll(leaving)) {
            throw new AuthException("Only tables of this group can be unmerged", HttpStatus.BAD_REQUEST);
        }
        List<RestaurantTable> freed = group.stream().filter(table -> leaving.contains(table.getId())).toList();
        List<RestaurantTable> remaining = new java.util.ArrayList<>(group.stream().filter(table -> !leaving.contains(table.getId())).toList());
        if (remaining.isEmpty()) {
            throw new AuthException("Keep at least one table for the guests", HttpStatus.BAD_REQUEST);
        }
        RestaurantTable keep = keepTableId == null
                ? (remaining.contains(head) ? head : remaining.stream()
                        .max(java.util.Comparator.comparingInt(RestaurantTable::getCapacity).thenComparing(RestaurantTable::getTableNumber, java.util.Comparator.reverseOrder()))
                        .orElseThrow())
                : remaining.stream().filter(table -> table.getId().equals(keepTableId)).findFirst()
                        .orElseThrow(() -> new AuthException("The table that keeps the order must stay", HttpStatus.BAD_REQUEST));
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        boolean seated = group.stream().anyMatch(table -> table.getStatus() == TableStatus.OCCUPIED);
        int guests = group.stream().mapToInt(this::guestsAt).sum();
        OffsetDateTime seatedAt = group.stream().map(RestaurantTable::getSeatedAt).filter(Objects::nonNull).min(OffsetDateTime::compareTo).orElse(null);
        boolean moreComing = head.isMoreComing();
        List<Order> orders = orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(
                group.stream().map(RestaurantTable::getId).toList(), List.of(OrderStatus.DRAFT, OrderStatus.OPEN));

        // What's left of the group, led by the table that keeps everything.
        remaining.remove(keep);
        remaining.add(0, keep);
        keep.setMergedInto(null);
        remaining.stream().skip(1).forEach(table -> table.setMergedInto(keep));
        List<TableStatus> before = freed.stream().map(RestaurantTable::getStatus).toList();
        for (RestaurantTable table : freed) {
            table.setMergedInto(null);
            if (table.getStatus() == TableStatus.OCCUPIED) {
                table.setStatus(TableStatus.AVAILABLE);
            }
            table.setGuestCount(null);
            table.setSeatedAt(null);
            table.setMoreComing(false);
            table.setUpdatedBy(actorId);
        }
        if (seated) {
            spreadGuests(remaining, Math.max(guests, remaining.size()), actorId);
            if (seatedAt != null) {
                remaining.forEach(table -> table.setSeatedAt(seatedAt));
            }
            keep.setMoreComing(moreComing);
        }
        remaining.forEach(table -> table.setUpdatedBy(actorId));
        restaurantTableSupport.saveTables(group);

        String freedNumbers = freed.stream().map(RestaurantTable::getTableNumber).sorted().collect(java.util.stream.Collectors.joining(", "));
        // One open order per table: anything that ended up apart on the group becomes the one order that stays.
        if (orders.size() > 1) {
            Order kept = tableOrderCombiner.combineOnto(orders.get(0).getRestaurantTable(), orders, freedNumbers + " unmerged", actorId);
            orders = kept == null ? List.of() : List.of(kept);
        }
        for (Order order : orders) {
            String from = order.getRestaurantTable() == null ? null : order.getRestaurantTable().getTableNumber();
            boolean moves = order.getRestaurantTable() == null || !order.getRestaurantTable().getId().equals(keep.getId());
            order.setRestaurantTable(keep);
            order.setUpdatedBy(actorId);
            orderSupport.addEvent(order, OrderEventType.TABLE_CHANGED,
                    freedNumbers + " unmerged and free" + (moves && from != null ? "; moved from " + from + " to " + keep.getTableNumber()
                            : "; the order stays on " + keep.getTableNumber()), actorId);
            orderSupport.saveOrder(order);
        }
        for (int index = 0; index < freed.size(); index++) {
            if (before.get(index) != freed.get(index).getStatus()) {
                events.publishEvent(new TableStatusChangedEvent(restaurantId, branchId, freed.get(index).getId(), before.get(index), freed.get(index).getStatus(), actorId));
            }
        }
        return restaurantTableSupport.toResponse(keep, restaurantTableSupport.loadChildMap(keep.getId()));
    }

    private TableResponse updateOperationalStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            TableStatus status,
            Integer guestCount
    ) {
        return updateOperationalStatus(authentication, restaurantId, branchId, tableId, status, guestCount, null);
    }

    private TableResponse updateOperationalStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            TableStatus status,
            Integer guestCount,
            Boolean moreComing
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = requireTableForUpdate(branchId, tableId);
        TableStatus previousStatus = table.getStatus();
        List<RestaurantTable> merged = restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(tableId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        int previousGuests = guestsAt(table) + merged.stream().mapToInt(this::guestsAt).sum();
        if (status == TableStatus.OCCUPIED) {
            if (guestCount == null || guestCount <= 0) {
                throw new AuthException(
                        "guestCount is required when seating guests",
                        HttpStatus.BAD_REQUEST
                );
            }
            List<RestaurantTable> group = new java.util.ArrayList<>(List.of(table));
            group.addAll(merged);
            // "More will come": a mark until staff untick it (how many is unknown).
            if (moreComing != null) {
                table.setMoreComing(moreComing);
            }
            tableSeatingRules.check(restaurantId, group, guestCount);
            spreadGuests(group, guestCount, actorId);
            String tables = group.stream().map(RestaurantTable::getTableNumber).collect(java.util.stream.Collectors.joining("-"));
            int seats = group.stream().mapToInt(RestaurantTable::getCapacity).sum();
            events.publishEvent(new pos.pos.tables.event.GuestsSeatedEvent(
                    restaurantId, branchId, table.getBranch().getName(), tableId, tables, seats, guestCount, previousGuests, actorId));
        } else {
            table.setGuestCount(null);
            table.setSeatedAt(null);
            table.setMoreComing(false);
            // The guests have gone, so the tables merged into this one are free too.
            merged.stream().filter(child -> child.getStatus() == TableStatus.OCCUPIED).forEach(child -> {
                child.setStatus(TableStatus.AVAILABLE);
                child.setGuestCount(null);
                child.setSeatedAt(null);
                child.setUpdatedBy(actorId);
            });
        }
        table.setStatus(status);
        table.setUpdatedBy(actorId);
        RestaurantTable saved = restaurantTableSupport.saveTable(table);
        restaurantTableSupport.saveTables(merged);
        // After the table is saved: saving an order flushes it, and a half-freed table fails its own checks.
        if (status == TableStatus.AVAILABLE) {
            cancelEmptyOrders(saved, merged, actorId);
        }
        if (previousStatus != status) {
            events.publishEvent(new TableStatusChangedEvent(restaurantId, branchId, tableId, previousStatus, status, actorId));
        }
        return restaurantTableSupport.toResponse(saved, restaurantTableSupport.loadChildMap(tableId));
    }

    // The guests left before ordering anything: an order with no dishes has nothing to pay, so it ends with them (user, 2026-11-01).
    private void cancelEmptyOrders(RestaurantTable table, List<RestaurantTable> merged, UUID actorId) {
        List<UUID> tableIds = new java.util.ArrayList<>(List.of(table.getId()));
        merged.forEach(child -> tableIds.add(child.getId()));
        List<Order> orders = orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(
                tableIds, List.of(OrderStatus.DRAFT, OrderStatus.OPEN));
        if (orders.isEmpty()) {
            return;
        }
        Set<UUID> withDishes = orderRepository.countServed(orders.stream().map(Order::getId).toList()).stream()
                .map(OrderRepository.OrderServedCount::getOrderId)
                .collect(java.util.stream.Collectors.toSet());
        orders.stream()
                .filter(order -> !withDishes.contains(order.getId()) && !orderSupport.hasMoneyTaken(order))
                .forEach(order -> {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setUpdatedBy(actorId);
                    orderSupport.addEvent(order, OrderEventType.CANCELLED, "Cancelled: the table was set free with no dishes ordered", actorId);
                    orderSupport.saveOrder(order);
                });
    }

    private int guestsAt(RestaurantTable table) {
        return table.getStatus() == TableStatus.OCCUPIED && table.getGuestCount() != null ? table.getGuestCount() : 0;
    }

    // Seats the guests across merged tables, main table first, filling each up to its seats, at least one per table.
    private void spreadGuests(List<RestaurantTable> group, int guests, UUID actorId) {
        int left = guests;
        for (int index = 0; index < group.size(); index++) {
            RestaurantTable table = group.get(index);
            int tablesAfter = group.size() - index - 1;
            int here = tablesAfter == 0
                    ? Math.max(1, left)
                    : Math.max(1, Math.min(Math.max(1, table.getCapacity()), left - tablesAfter));
            left -= here;
            table.setGuestCount(here);
            if (table.getStatus() != TableStatus.OCCUPIED || table.getSeatedAt() == null) {
                table.setSeatedAt(OffsetDateTime.now());
            }
            table.setStatus(TableStatus.OCCUPIED);
            table.setUpdatedBy(actorId);
        }
    }

    private RestaurantTable requireTableForUpdate(UUID branchId, UUID tableId) {
        return restaurantTableRepository.findByIdAndBranchIdForUpdate(tableId, branchId)
                .orElseThrow(pos.pos.exception.tables.RestaurantTableNotFoundException::new);
    }

    private void moveOpenOrdersToMergedPrimary(
            RestaurantTable primaryTable,
            List<RestaurantTable> mergeTargets,
            UUID actorId
    ) {
        List<UUID> childTableIds = mergeTargets.stream()
                .map(RestaurantTable::getId)
                .toList();
        if (childTableIds.isEmpty()) {
            return;
        }

        List<Order> childTableOrders = orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(
                childTableIds,
                List.of(OrderStatus.DRAFT, OrderStatus.OPEN)
        );
        if (childTableOrders.isEmpty()) {
            return;
        }
        // One open order per table (user, 2026-11-01): the start table's order stays and the others' dishes join it.
        List<Order> orders = new java.util.ArrayList<>(orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(
                List.of(primaryTable.getId()), List.of(OrderStatus.DRAFT, OrderStatus.OPEN)));
        String joined = mergeTargets.stream().map(RestaurantTable::getTableNumber).sorted().collect(java.util.stream.Collectors.joining(", "));
        // A joined table's order with no dishes ends there: its guests sat down without ordering (user, 2026-11-01).
        childTableOrders.stream()
                .filter(order -> !tableOrderCombiner.cancelIfEmpty(order, "Table " + order.getRestaurantTable().getTableNumber() + " joined " + primaryTable.getTableNumber(), actorId))
                .forEach(orders::add);
        Order kept = tableOrderCombiner.combineOnto(primaryTable, orders, joined + " joined " + primaryTable.getTableNumber(), actorId);
        if (kept != null) {
            orderSupport.addEvent(kept, OrderEventType.TABLE_CHANGED, joined + " joined " + primaryTable.getTableNumber(), actorId);
            orderSupport.saveOrder(kept);
        }
    }

    // Who can take in whom (user rules, 2026-10-31): a table takes in tables of its own kind or lower. Free and reserved
    // are the lowest, then guests seated without an order, then a table with an order.
    // An order with no dishes yet counts as guests seated (user, 2026-11-01).
    private int joinRank(RestaurantTable table) {
        boolean hasOrder = orderRepository.findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(
                List.of(table.getId()), List.of(OrderStatus.DRAFT, OrderStatus.OPEN)
        ).stream().anyMatch(tableOrderCombiner::hasDishes);
        if (hasOrder) {
            return JOIN_ORDER;
        }
        if (table.getStatus() == TableStatus.OCCUPIED) {
            return JOIN_SEATED;
        }
        return table.getStatus() == TableStatus.RESERVED ? JOIN_RESERVED : JOIN_FREE;
    }

    private void validateMergeTarget(RestaurantTable mergeTarget) {
        if (!mergeTarget.isActive()) {
            throw new AuthException("Cannot merge an inactive table", HttpStatus.BAD_REQUEST);
        }

        if (mergeTarget.getMergedInto() != null) {
            throw new AuthException(
                    mergeTarget.getTableNumber() + " is already merged with "
                            + mergeTarget.getMergedInto().getTableNumber() + ". Edit the existing table group instead.",
                    HttpStatus.CONFLICT
            );
        }

        if (restaurantTableRepository.existsByMergedInto_Id(mergeTarget.getId())) {
            throw new AuthException(
                    mergeTarget.getTableNumber() + " already has merged tables. Edit the existing table group instead.",
                    HttpStatus.CONFLICT
            );
        }
    }

    /**
     * Rules that apply uniformly to every table in a merge attempt (the primary
     * and every target): blocked/under-maintenance tables, tables waiting to be
     * cleared, tables whose bill was requested and tables with a payment already
     * underway can't be merged.
     */
    private void validateMergeParticipant(RestaurantTable table) {
        if (table.getStatus() == TableStatus.MAINTENANCE || table.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw new AuthException(table.getTableNumber() + " is unavailable and cannot be merged.", HttpStatus.BAD_REQUEST);
        }

        if (table.getStatus() == TableStatus.DIRTY) {
            throw new AuthException(table.getTableNumber() + " is waiting to be cleared before it can be merged.", HttpStatus.BAD_REQUEST);
        }

        Optional<Order> openOrder = orderRepository.findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(
                table.getId(), List.of(OrderStatus.DRAFT, OrderStatus.OPEN)
        );
        // Guests who have paid are about to leave (user, 2026-11-01).
        if ((openOrder.isPresent() && openOrder.get().getPaymentStatus() == pos.pos.order.enums.OrderPaymentStatus.PAID)
                || (openOrder.isEmpty() && !restaurantTableSupport.paidSinceSeated(List.of(table)).isEmpty())) {
            throw new AuthException(table.getTableNumber() + " has paid and is waiting for its guests to leave, so it can't be joined.", HttpStatus.CONFLICT);
        }
        if (openOrder.isPresent() && openOrder.get().getBillRequestedAt() != null) {
            throw new AuthException(table.getTableNumber() + "'s bill has been requested, so it can't be joined.", HttpStatus.CONFLICT);
        }
        if (openOrder.isPresent() && openOrder.get().getPaymentStatus() == OrderPaymentStatus.PARTIALLY_PAID) {
            throw new AuthException(
                    "Payment has started for " + table.getTableNumber() + ". Finish or cancel payment before merging.",
                    HttpStatus.CONFLICT
            );
        }
    }

}
