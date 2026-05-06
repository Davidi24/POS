package pos.pos.tables.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.tables.RestaurantTableNotFoundException;
import pos.pos.exception.tables.TableCategoryNotFoundException;
import pos.pos.tables.dto.FloorRenameRequest;
import pos.pos.tables.dto.FloorSummaryResponse;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.dto.TableLayoutItemResponse;
import pos.pos.tables.dto.TableLayoutResponse;
import pos.pos.tables.dto.TableRequest;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.mapper.RestaurantTableMapper;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.repository.TableCategoryRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@lombok.RequiredArgsConstructor
public class RestaurantTableSupport {

    public static final String UNASSIGNED_FLOOR = "Unassigned";

    private final RestaurantTableRepository restaurantTableRepository;
    private final TableCategoryRepository tableCategoryRepository;
    private final RestaurantTableMapper restaurantTableMapper;

    public void applyTableRequest(UUID branchId, RestaurantTable table, TableRequest request) {
        TableCategory category = resolveCategory(branchId, request.getCategoryId());
        restaurantTableMapper.applyRequest(table, request, category);
    }

    public TableCategory resolveCategory(UUID branchId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }

        return tableCategoryRepository.findByIdAndBranch_Id(categoryId, branchId)
                .orElseThrow(TableCategoryNotFoundException::new);
    }

    public RestaurantTable requireTable(UUID branchId, UUID tableId) {
        return restaurantTableRepository.findByIdAndBranch_Id(tableId, branchId)
                .orElseThrow(RestaurantTableNotFoundException::new);
    }

    public RestaurantTable saveTable(RestaurantTable table) {
        try {
            return restaurantTableRepository.saveAndFlush(table);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Table update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void saveTables(Collection<RestaurantTable> tables) {
        if (tables.isEmpty()) {
            return;
        }

        try {
            restaurantTableRepository.saveAllAndFlush(tables);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Table update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public Map<UUID, RestaurantTable> loadTablesForUpdate(UUID branchId, Collection<UUID> tableIds) {
        Set<UUID> uniqueIds = new LinkedHashSet<>(tableIds);
        if (uniqueIds.size() != tableIds.size()) {
            throw new AuthException("items must not contain duplicate table ids", HttpStatus.BAD_REQUEST);
        }

        List<RestaurantTable> tables = restaurantTableRepository.findAllByBranch_IdAndIdIn(branchId, uniqueIds);
        if (tables.size() != uniqueIds.size()) {
            throw new AuthException("items must only reference tables in this branch", HttpStatus.BAD_REQUEST);
        }

        return tables.stream()
                .collect(Collectors.toMap(RestaurantTable::getId, table -> table));
    }

    public BranchTableSnapshot loadBranchTables(UUID restaurantId, UUID branchId) {
        List<RestaurantTable> tables = restaurantTableRepository.findAllByBranch_IdOrderByFloorAscNameAsc(branchId);
        Map<UUID, RestaurantTable> tablesById = tables.stream()
                .collect(Collectors.toMap(RestaurantTable::getId, table -> table));
        return new BranchTableSnapshot(
                restaurantId,
                branchId,
                tables,
                buildChildrenMap(tables),
                tablesById
        );
    }

    public Map<UUID, List<RestaurantTable>> loadChildMap(UUID tableId) {
        return Map.of(tableId, restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(tableId));
    }

    public List<RestaurantTable> mergedChildren(RestaurantTable table, Map<UUID, List<RestaurantTable>> childrenByParentId) {
        return childrenByParentId.getOrDefault(table.getId(), List.of());
    }

    public TableResponse toResponse(RestaurantTable table, Map<UUID, List<RestaurantTable>> childrenByParentId) {
        List<RestaurantTable> mergedChildren = mergedChildren(table, childrenByParentId);
        return restaurantTableMapper.toResponse(
                table,
                mergedChildren.stream().map(RestaurantTable::getId).toList(),
                effectiveCapacity(table, mergedChildren)
        );
    }

    public TableLayoutItemResponse toLayoutResponseItem(RestaurantTable table, Map<UUID, List<RestaurantTable>> childrenByParentId) {
        List<RestaurantTable> mergedChildren = mergedChildren(table, childrenByParentId);
        return restaurantTableMapper.toLayoutItemResponse(
                table,
                mergedChildren.stream().map(RestaurantTable::getId).toList(),
                effectiveCapacity(table, mergedChildren)
        );
    }

    public TableAvailabilityResponse toAvailabilityResponse(
            RestaurantTable table,
            Map<UUID, List<RestaurantTable>> childrenByParentId,
            boolean operationallyAvailable,
            boolean availableForRequestedWindow,
            String blockingReason,
            List<UUID> overlappingReservationIds
    ) {
        List<RestaurantTable> mergedChildren = mergedChildren(table, childrenByParentId);
        return restaurantTableMapper.toAvailabilityResponse(
                table,
                mergedChildren.stream().map(RestaurantTable::getId).toList(),
                effectiveCapacity(table, mergedChildren),
                operationallyAvailable,
                availableForRequestedWindow,
                blockingReason,
                overlappingReservationIds
        );
    }

    public TableLayoutResponse buildLayoutResponse(BranchTableSnapshot snapshot) {
        return TableLayoutResponse.builder()
                .restaurantId(snapshot.restaurantId())
                .branchId(snapshot.branchId())
                .floors(summarizeFloors(snapshot.tables()))
                .tables(snapshot.tables().stream()
                        .map(table -> toLayoutResponseItem(table, snapshot.childrenByParentId()))
                        .toList())
                .build();
    }

    public List<FloorSummaryResponse> summarizeFloors(List<RestaurantTable> tables) {
        return tables.stream()
                .collect(Collectors.groupingBy(
                        this::floorLabel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey(floorComparator()))
                .map(entry -> FloorSummaryResponse.builder()
                        .name(entry.getKey())
                        .tableCount(entry.getValue().size())
                        .positionedTableCount((int) entry.getValue().stream()
                                .filter(table -> table.getPositionX() != null && table.getPositionY() != null)
                                .count())
                        .build())
                .toList();
    }

    public Map<String, String> validateAndBuildFloorRenameMap(List<RestaurantTable> tables, List<FloorRenameRequest> renames) {
        Set<String> existingFloors = tables.stream()
                .map(RestaurantTable::getFloor)
                .filter(floor -> floor != null && !floor.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> renameMap = new LinkedHashMap<>();

        for (FloorRenameRequest rename : renames) {
            if (!existingFloors.contains(rename.getFrom())) {
                throw new AuthException("Floor '" + rename.getFrom() + "' does not exist in this branch", HttpStatus.BAD_REQUEST);
            }

            if (renameMap.put(rename.getFrom(), rename.getTo()) != null) {
                throw new AuthException("Floor renames must not contain duplicate source values", HttpStatus.BAD_REQUEST);
            }
        }

        return renameMap;
    }

    public void validatePositionPair(BigDecimal positionX, BigDecimal positionY, String message) {
        if ((positionX == null) != (positionY == null)) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    public String floorLabel(RestaurantTable table) {
        return table.getFloor() == null || table.getFloor().isBlank() ? UNASSIGNED_FLOOR : table.getFloor();
    }

    public Comparator<String> floorComparator() {
        return (left, right) -> {
            if (UNASSIGNED_FLOOR.equals(left) && UNASSIGNED_FLOOR.equals(right)) {
                return 0;
            }
            if (UNASSIGNED_FLOOR.equals(left)) {
                return 1;
            }
            if (UNASSIGNED_FLOOR.equals(right)) {
                return -1;
            }
            return left.compareToIgnoreCase(right);
        };
    }

    public int effectiveCapacity(RestaurantTable table, List<RestaurantTable> mergedChildren) {
        return table.getCapacity() + mergedChildren.stream()
                .mapToInt(RestaurantTable::getCapacity)
                .sum();
    }

    private Map<UUID, List<RestaurantTable>> buildChildrenMap(List<RestaurantTable> tables) {
        return tables.stream()
                .filter(table -> table.getMergedInto() != null)
                .collect(Collectors.groupingBy(
                        table -> table.getMergedInto().getId(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(RestaurantTable::getTableNumber))
                                .toList())
                ));
    }

    public record BranchTableSnapshot(
            UUID restaurantId,
            UUID branchId,
            List<RestaurantTable> tables,
            Map<UUID, List<RestaurantTable>> childrenByParentId,
            Map<UUID, RestaurantTable> tablesById
    ) {
    }
}
