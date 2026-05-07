package pos.pos.tables.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.dto.AutoArrangeTableLayoutRequest;
import pos.pos.tables.dto.FloorSummaryResponse;
import pos.pos.tables.dto.TableLayoutItemRequest;
import pos.pos.tables.dto.TableLayoutResponse;
import pos.pos.tables.dto.TableMapFloorResponse;
import pos.pos.tables.dto.TableMapResponse;
import pos.pos.tables.dto.UpdateFloorsRequest;
import pos.pos.tables.dto.UpdateTableLayoutRequest;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.service.RestaurantTableSupport.BranchTableSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class RestaurantTableLayoutService {

    private static final String DEFAULT_AUTO_ARRANGE_FLOOR = "Main";

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantTableSupport restaurantTableSupport;

    @Transactional(readOnly = true)
    public TableLayoutResponse getTableLayout(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        return restaurantTableSupport.buildLayoutResponse(snapshot);
    }

    @Transactional
    public TableLayoutResponse updateTableLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateTableLayoutRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Map<UUID, RestaurantTable> tablesById = restaurantTableSupport.loadTablesForUpdate(
                branchId,
                request.getItems().stream().map(TableLayoutItemRequest::getTableId).toList()
        );

        for (TableLayoutItemRequest item : request.getItems()) {
            RestaurantTable table = tablesById.get(item.getTableId());
            applyLayoutItem(table, item, actorId);
        }

        restaurantTableSupport.saveTables(new ArrayList<>(tablesById.values()));
        return getTableLayout(authentication, restaurantId, branchId);
    }

    @Transactional
    public TableLayoutResponse autoArrangeTableLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            AutoArrangeTableLayoutRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        List<RestaurantTable> targetTables = selectAutoArrangeTargets(snapshot.tables(), request);

        AutoArrangeSettings settings = resolveAutoArrangeSettings(request);
        for (int index = 0; index < targetTables.size(); index++) {
            applyAutoArrangePosition(targetTables.get(index), request.getFloor(), settings, index, actorId);
        }

        restaurantTableSupport.saveTables(targetTables);
        return getTableLayout(authentication, restaurantId, branchId);
    }

    @Transactional(readOnly = true)
    public List<FloorSummaryResponse> getFloors(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        return restaurantTableSupport.summarizeFloors(snapshot.tables());
    }

    @Transactional
    public List<FloorSummaryResponse> updateFloors(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateFloorsRequest request
    ) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        Map<String, String> renameMap = restaurantTableSupport.validateAndBuildFloorRenameMap(snapshot.tables(), request.getRenames());

        List<RestaurantTable> changedTables = snapshot.tables().stream()
                .filter(table -> table.getFloor() != null && renameMap.containsKey(table.getFloor()))
                .toList();

        for (RestaurantTable table : changedTables) {
            table.setFloor(renameMap.get(table.getFloor()));
            table.setUpdatedBy(actorId);
        }

        restaurantTableSupport.saveTables(changedTables);
        return restaurantTableSupport.summarizeFloors(snapshot.tables());
    }

    @Transactional(readOnly = true)
    public TableMapResponse getTableMap(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);

        List<TableMapFloorResponse> floors = snapshot.tables().stream()
                .collect(Collectors.groupingBy(
                        restaurantTableSupport::floorLabel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(restaurantTableSupport.floorComparator()))
                .map(entry -> TableMapFloorResponse.builder()
                        .name(entry.getKey())
                        .tableCount(entry.getValue().size())
                        .tables(entry.getValue().stream()
                                .map(table -> restaurantTableSupport.toResponse(table, snapshot.childrenByParentId()))
                                .toList())
                        .build())
                .toList();

        return TableMapResponse.builder()
                .restaurantId(snapshot.restaurantId())
                .branchId(snapshot.branchId())
                .floors(floors)
                .build();
    }

    private void applyLayoutItem(RestaurantTable table, TableLayoutItemRequest item, UUID actorId) {
        if (item.getFloor() != null) {
            table.setFloor(item.getFloor());
        }
        if (item.getPositionX() != null || item.getPositionY() != null) {
            restaurantTableSupport.validatePositionPair(
                    item.getPositionX(),
                    item.getPositionY(),
                    "positionX and positionY must both be set together"
            );
            table.setPositionX(item.getPositionX());
            table.setPositionY(item.getPositionY());
        }
        if (item.getShape() != null) {
            table.setShape(item.getShape());
        }
        table.setUpdatedBy(actorId);
    }

    private List<RestaurantTable> selectAutoArrangeTargets(List<RestaurantTable> branchTables, AutoArrangeTableLayoutRequest request) {
        return branchTables.stream()
                .filter(table -> request.getFloor() == null || restaurantTableSupport.floorLabel(table).equals(request.getFloor()))
                .filter(table -> !Boolean.TRUE.equals(request.getOnlyUnpositioned())
                        || (table.getPositionX() == null && table.getPositionY() == null))
                .sorted(Comparator.comparing(RestaurantTable::getTableNumber))
                .toList();
    }

    private AutoArrangeSettings resolveAutoArrangeSettings(AutoArrangeTableLayoutRequest request) {
        return new AutoArrangeSettings(
                request.getMaxColumns() == null ? 4 : request.getMaxColumns(),
                request.getStartX() == null ? BigDecimal.ZERO : request.getStartX(),
                request.getStartY() == null ? BigDecimal.ZERO : request.getStartY(),
                request.getHorizontalSpacing() == null ? new BigDecimal("40.00") : request.getHorizontalSpacing(),
                request.getVerticalSpacing() == null ? new BigDecimal("40.00") : request.getVerticalSpacing()
        );
    }

    private void applyAutoArrangePosition(
            RestaurantTable table,
            String requestedFloor,
            AutoArrangeSettings settings,
            int index,
            UUID actorId
    ) {
        int row = index / settings.maxColumns();
        int column = index % settings.maxColumns();

        if (requestedFloor != null) {
            table.setFloor(requestedFloor);
        } else if (table.getFloor() == null) {
            table.setFloor(DEFAULT_AUTO_ARRANGE_FLOOR);
        }

        table.setPositionX(settings.startX().add(settings.horizontalSpacing().multiply(BigDecimal.valueOf(column))));
        table.setPositionY(settings.startY().add(settings.verticalSpacing().multiply(BigDecimal.valueOf(row))));
        table.setUpdatedBy(actorId);
    }

    private record AutoArrangeSettings(
            int maxColumns,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal horizontalSpacing,
            BigDecimal verticalSpacing
    ) {
    }
}
