package pos.pos.tables.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndStatusOrderByNameAsc(UUID branchId, TableStatus status);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndActiveTrueOrderByFloorAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndMergedIntoIsNullOrderByFloorAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByBranch_IdAndIdIn(UUID branchId, Collection<UUID> tableIds);

    @EntityGraph(attributePaths = {"category", "mergedInto"})
    List<RestaurantTable> findAllByMergedInto_IdOrderByTableNumberAsc(UUID tableId);

    @EntityGraph(attributePaths = {"restaurant", "branch", "category", "mergedInto"})
    Optional<RestaurantTable> findFirstByQrCodeValueAndActiveTrue(String qrCodeValue);

    boolean existsByCategory_Id(UUID categoryId);

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
