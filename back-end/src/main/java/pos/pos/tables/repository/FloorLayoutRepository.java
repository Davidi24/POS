package pos.pos.tables.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.tables.entity.FloorLayout;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FloorLayoutRepository
        extends JpaRepository<FloorLayout, UUID> {

    List<FloorLayout> findAllByBranch_IdOrderByFloorNameAsc(
            UUID branchId
    );

    Optional<FloorLayout> findByIdAndBranch_Id(
            UUID floorLayoutId,
            UUID branchId
    );

    Optional<FloorLayout> findByBranch_IdAndFloorName(
            UUID branchId,
            String floorName
    );

    boolean existsByBranch_IdAndFloorName(
            UUID branchId,
            String floorName
    );
}