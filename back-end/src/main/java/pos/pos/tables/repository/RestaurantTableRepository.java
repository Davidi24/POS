package pos.pos.tables.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID> {

    // Fetches all tables for a branch ordered by floor and name, with category and merge parent preloaded.
    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdOrderByFloorAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    Optional<RestaurantTable> findByIdAndBranch_Id(UUID tableId, UUID branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM RestaurantTable t
            WHERE t.id = :tableId
              AND t.branch.id = :branchId
            """)
    Optional<RestaurantTable> findByIdAndBranchIdForUpdate(
            @Param("tableId") UUID tableId,
            @Param("branchId") UUID branchId
    );

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndStatusOrderByNameAsc(UUID branchId, TableStatus status);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndActiveTrueOrderByFloorAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndMergedIntoIsNullOrderByFloorAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndIdIn(UUID branchId, Collection<UUID> tableIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"restaurant", "branch", "category", "mergedInto"})
    @Query("""
            SELECT t
            FROM RestaurantTable t
            WHERE t.branch.id = :branchId
              AND t.id IN :tableIds
            """)
    List<RestaurantTable> findAllByBranchIdAndIdsForUpdate(
            @Param("branchId") UUID branchId,
            @Param("tableIds") Collection<UUID> tableIds
    );

    // Acquire overlapping table locks in one database order for every workflow that touches multiple tables.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t.id
            FROM RestaurantTable t
            WHERE t.branch.id = :branchId
              AND t.id IN :tableIds
            ORDER BY t.id
            """)
    List<UUID> lockTablesForUpdateInStableOrder(
            @Param("branchId") UUID branchId,
            @Param("tableIds") Collection<UUID> tableIds
    );

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByMergedInto_IdOrderByTableNumberAsc(UUID tableId);

    @EntityGraph(attributePaths = {"restaurant", "branch", "category", "mergedInto"})
    Optional<RestaurantTable> findFirstByQrCodeValueAndActiveTrue(String qrCodeValue);

    @EntityGraph(attributePaths = {"restaurant", "branch", "category", "mergedInto"})
    Optional<RestaurantTable> findByBranch_IdAndTableNumber(UUID branchId, String tableNumber);

    boolean existsByCategory_Id(UUID categoryId);

    List<RestaurantTable> findAllByBranch_IdAndCategory_Id(UUID branchId, UUID categoryId);

    boolean existsByMergedInto_Id(UUID tableId);

    @Query("""
            SELECT DISTINCT t.floor
            FROM RestaurantTable t
            WHERE t.branch.id = :branchId
              AND t.floor IS NOT NULL
            ORDER BY t.floor ASC
            """)
    List<String> findDistinctFloorsByBranchId(UUID branchId);
}
