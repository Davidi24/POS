package pos.pos.kds.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.kds.entity.KdsStationRouting;

import java.util.List;
import java.util.UUID;

public interface KdsStationRoutingRepository extends JpaRepository<KdsStationRouting, UUID> {

    @EntityGraph(attributePaths = {"station", "menuItem"})
    List<KdsStationRouting> findAllByStation_IdOrderByDisplayOrderAscCreatedAtAsc(UUID stationId);

    @EntityGraph(attributePaths = {"station", "menuItem"})
    @Query("""
        SELECT routing
        FROM KdsStationRouting routing
        JOIN FETCH routing.station station
        JOIN FETCH routing.menuItem menuItem
        WHERE station.branch.id = :branchId
          AND station.active = true
          AND routing.active = true
        ORDER BY station.displayOrder ASC, routing.displayOrder ASC, routing.createdAt ASC
    """)
    List<KdsStationRouting> findAllActiveByBranchId(UUID branchId);

    @Query("""
        SELECT COUNT(routing)
        FROM KdsStationRouting routing
        JOIN routing.station station
        WHERE station.branch.id = :branchId
          AND station.active = true
          AND routing.active = true
          AND routing.menuItem.id = :menuItemId
          AND (:stationId IS NULL OR station.id <> :stationId)
    """)
    long countActiveBranchRoutingsByMenuItem(UUID branchId, UUID menuItemId, UUID stationId);
}
