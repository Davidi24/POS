package pos.pos.device.network;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceNetworkRepository extends JpaRepository<DeviceNetwork, UUID> {

    List<DeviceNetwork> findByBranchIdOrderByAddedAtAsc(UUID branchId);

    Optional<DeviceNetwork> findByBranchIdAndAddress(UUID branchId, String address);

    Optional<DeviceNetwork> findByIdAndBranchId(UUID id, UUID branchId);

    long countByBranchId(UUID branchId);
}
