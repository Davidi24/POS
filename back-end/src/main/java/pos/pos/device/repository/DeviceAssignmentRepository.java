package pos.pos.device.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.device.entity.DeviceAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceAssignmentRepository extends JpaRepository<DeviceAssignment, UUID> {

    @EntityGraph(attributePaths = {"branch", "user", "assignedByUser"})
    List<DeviceAssignment> findAllByDevice_IdOrderByAssignedAtDesc(UUID deviceId);

    @EntityGraph(attributePaths = {"branch", "user", "assignedByUser"})
    Optional<DeviceAssignment> findByIdAndDevice_Id(UUID assignmentId, UUID deviceId);

    Optional<DeviceAssignment> findByDevice_IdAndActiveTrue(UUID deviceId);
}
