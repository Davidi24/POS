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

    // Loads all tables for a specific branch.
    // Also builds:
    // 1. a map where the key is the parent table ID and the value is the list of tables merged into it
    // 2. a map where the key is the table ID and the value is the table itself
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

    // this function checks if category has come to the request if yes it check if category exist
    // then it calls the mapper to update the restaurant table
    // in the mapper if it exists it will put it, otherwise it will put it null
    public void applyTableRequest(UUID branchId, RestaurantTable table, TableRequest request) {
        TableCategory category = null;
        if (request.getCategoryId() != null) {
            category = tableCategoryRepository.findByIdAndBranch_Id(request.getCategoryId(), branchId)
                    .orElseThrow(TableCategoryNotFoundException::new);
        }

        restaurantTableMapper.applyRequest(table, request, category);
    }

    // simply tries to save the table and uses flush to check for the error immediately
    public RestaurantTable saveTable(RestaurantTable table) {
        try {
            return restaurantTableRepository.saveAndFlush(table);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Table update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // now when we want to response the table response we can have merged tables. Merged tables means
    // like if we have two tables and the tables has each 2 person capacity, and you need to merge them
    // because there are 4 people who want to sit then you merge the tables, you need a parent to say to
    // which table were merge, and the parent is more like random, if one goes to the other, or otherwise
    // it does not meter and this is saved in a map which ahs ids and a list of tables that are merged
    // to this id. so in the end it returns a specific table and a list of ids that are connected to it
    public TableResponse toResponse(RestaurantTable table, Map<UUID, List<RestaurantTable>> childrenByParentId) {
        List<RestaurantTable> mergedChildren = mergedChildren(table, childrenByParentId);
        return restaurantTableMapper.toResponse(
                table,
                mergedChildren.stream().map(RestaurantTable::getId).toList(),
                effectiveCapacity(table, mergedChildren)
        );
    }


    public RestaurantTable requireTable(UUID branchId, UUID tableId) {
        return restaurantTableRepository.findByIdAndBranch_Id(tableId, branchId)
                .orElseThrow(RestaurantTableNotFoundException::new);
    }

    public Map<UUID, List<RestaurantTable>> loadChildMap(UUID tableId) {
        return Map.of(tableId, restaurantTableRepository.findAllByMergedInto_IdOrderByTableNumberAsc(tableId));
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

    /**
     * Creates a summary for each floor in the branch.
     * <p>
     * First, tables are grouped by their floor name.
     * If a table has no floor, it is grouped under "Unassigned".
     * <p>
     * Example:
     * Floor 1 -> [A1, A2]
     * Floor 2 -> [B1]
     * Unassigned -> [C1]
     * <p>
     * Then the floor groups are sorted by floor name.
     * "Unassigned" is always placed at the end.
     * <p>
     * For each floor, the response contains:
     * - name: the floor name
     * - tableCount: total number of tables on that floor
     * - positionedTableCount: number of tables that have both positionX and positionY
     * <p>
     * positionedTableCount is useful for the frontend layout,
     * because it shows how many tables are already placed on the floor map.
     */
    public List<FloorSummaryResponse> summarizeFloors(List<RestaurantTable> tables) {
        return tables.stream()
                // groups tables by floor name.
                // example
                // Floor 1 -> [A1, A2]
                // Floor 2 -> [B1]
                // Unassigned -> [C1]
                .collect(Collectors.groupingBy(
                        this::floorLabel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                // loops through each floor group.
                .entrySet().stream()
                // sorts the floors by name, but keeps "Unassigned" at the end.
                .sorted(Map.Entry.comparingByKey(floorComparator()))
                .map(entry -> FloorSummaryResponse.builder()
                        .name(entry.getKey())
                        .tableCount(entry.getValue().size())
                        // It counts how many tables on that floor have a position saved.
                        .positionedTableCount((int) entry.getValue().stream()
                                .filter(table -> table.getPositionX() != null && table.getPositionY() != null)
                                .count())
                        .build())
                .toList();
    }

    // this let the UNASSIGNED_FLOOR in the end, idk yet how it does
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

    // same as toResponse function just return different DTO( simpler one)
    public TableLayoutItemResponse toLayoutResponseItem(RestaurantTable table, Map<UUID, List<RestaurantTable>> childrenByParentId) {
        List<RestaurantTable> mergedChildren = mergedChildren(table, childrenByParentId);
        return restaurantTableMapper.toLayoutItemResponse(
                table,
                mergedChildren.stream().map(RestaurantTable::getId).toList(),
                effectiveCapacity(table, mergedChildren)
        );
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

    public String floorLabel(RestaurantTable table) {
        return table.getFloor() == null || table.getFloor().isBlank() ? UNASSIGNED_FLOOR : table.getFloor();
    }





    // checks if both position x and y are in the request, you can nto set one without the another
    public void validatePositionPair(BigDecimal positionX, BigDecimal positionY, String message) {
        if ((positionX == null) != (positionY == null)) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    /*
     * Groups merged tables by their parent table ID.
     *
     * Example:
     *
     * A1 id = 1, mergedInto = null
     * A2 id = 2, mergedInto = A1
     * A3 id = 3, mergedInto = A1
     * B1 id = 4, mergedInto = null
     * B2 id = 5, mergedInto = B1
     * C1 id = 6, mergedInto = null
     *
     * After buildChildrenMap(tables):
     *
     * {
     *   1 -> [A2, A3],
     *   4 -> [B2]
     * }
     */
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


    // return the list of a specific key from the Map
    public List<RestaurantTable> mergedChildren(RestaurantTable table, Map<UUID, List<RestaurantTable>> childrenByParentId) {
        // try to get the value for this key; if the key does not exist, return the default value (which is emty lsit).
        return childrenByParentId.getOrDefault(table.getId(), List.of());
    }

    // returns the map of all tables capacity
    public int effectiveCapacity(RestaurantTable table, List<RestaurantTable> mergedChildren) {
        return table.getCapacity() + mergedChildren.stream()
                .mapToInt(RestaurantTable::getCapacity)
                .sum();
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
