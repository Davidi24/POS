package pos.pos.device.network;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DeviceNetworkRuleRepository extends JpaRepository<DeviceNetworkRule, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM DeviceNetworkRule r WHERE r.branchId = :branchId")
    Optional<DeviceNetworkRule> findForUpdate(UUID branchId);
}
