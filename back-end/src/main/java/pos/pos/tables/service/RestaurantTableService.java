package pos.pos.tables.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
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
import pos.pos.tables.repository.RestaurantTableRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class RestaurantTableService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    private final RestaurantTableSupport restaurantTableSupport;
    private final RestaurantTableLayoutService restaurantTableLayoutService;
    private final RestaurantTableAvailabilityService restaurantTableAvailabilityService;

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
                request.getGuestCount()
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
        RestaurantTable primaryTable = restaurantTableSupport.requireTable(branchId, tableId);
        if (primaryTable.getMergedInto() != null) {
            throw new AuthException("Cannot merge tables into a table that is already merged into another table", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> uniqueIds = new LinkedHashSet<>(request.getTableIds());
        if (uniqueIds.contains(tableId)) {
            throw new AuthException("tableIds must not include the primary table id", HttpStatus.BAD_REQUEST);
        }

        if (uniqueIds.size() != request.getTableIds().size()) {
            throw new AuthException("tableIds must not contain duplicates", HttpStatus.BAD_REQUEST);
        }

        List<RestaurantTable> mergeTargets = restaurantTableRepository.findAllByBranch_IdAndIdIn(branchId, uniqueIds);
        if (mergeTargets.size() != uniqueIds.size()) {
            throw new AuthException("tableIds must all belong to this branch", HttpStatus.BAD_REQUEST);
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);
        for (RestaurantTable mergeTarget : mergeTargets) {
            validateMergeTarget(mergeTarget);
            mergeTarget.setMergedInto(primaryTable);
            mergeTarget.setUpdatedBy(actorId);
        }

        restaurantTableSupport.saveTables(mergeTargets);
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

    private TableResponse updateOperationalStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID tableId,
            TableStatus status,
            Integer guestCount
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        RestaurantTable table = restaurantTableSupport.requireTable(branchId, tableId);
        if (status == TableStatus.OCCUPIED) {
            if (guestCount == null || guestCount <= 0) {
                throw new AuthException(
                        "guestCount is required when seating guests",
                        HttpStatus.BAD_REQUEST
                );
            }
            table.setGuestCount(guestCount);
            if (table.getStatus() != TableStatus.OCCUPIED || table.getSeatedAt() == null) {
                table.setSeatedAt(OffsetDateTime.now());
            }
        } else {
            table.setGuestCount(null);
            table.setSeatedAt(null);
        }
        table.setStatus(status);
        table.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        return restaurantTableSupport.toResponse(
                restaurantTableSupport.saveTable(table),
                restaurantTableSupport.loadChildMap(tableId)
        );
    }

    private void validateMergeTarget(RestaurantTable mergeTarget) {
        if (!mergeTarget.isActive()) {
            throw new AuthException("Cannot merge an inactive table", HttpStatus.BAD_REQUEST);
        }

        if (mergeTarget.getMergedInto() != null) {
            throw new AuthException("Cannot merge a table that is already merged into another table", HttpStatus.BAD_REQUEST);
        }

        if (restaurantTableRepository.existsByMergedInto_Id(mergeTarget.getId())) {
            throw new AuthException("Cannot merge a table that already has merged child tables", HttpStatus.BAD_REQUEST);
        }

        if (mergeTarget.getStatus() == TableStatus.MAINTENANCE || mergeTarget.getStatus() == TableStatus.OUT_OF_SERVICE) {
            throw new AuthException("Cannot merge a table that is blocked or under maintenance", HttpStatus.BAD_REQUEST);
        }
    }

}
