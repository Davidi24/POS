package pos.pos.kds.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.kds.entity.KdsStation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KdsStationRepository extends JpaRepository<KdsStation, UUID> {

    @EntityGraph(attributePaths = {"branch", "device", "routings", "routings.menuItem"})
    List<KdsStation> findAllByBranch_IdOrderByDisplayOrderAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"branch", "device", "routings", "routings.menuItem"})
    List<KdsStation> findAllByBranch_IdAndActiveTrueOrderByDisplayOrderAscNameAsc(UUID branchId);

    @EntityGraph(attributePaths = {"branch", "device", "routings", "routings.menuItem"})
    Optional<KdsStation> findByIdAndBranch_Id(UUID stationId, UUID branchId);

    @EntityGraph(attributePaths = {"branch", "device", "routings", "routings.menuItem"})
    Optional<KdsStation> findByDevice_IdAndRestaurant_Id(UUID deviceId, UUID restaurantId);

    boolean existsByBranch_IdAndCode(UUID branchId, String code);

    boolean existsByBranch_IdAndCodeAndIdNot(UUID branchId, String code, UUID stationId);
}
