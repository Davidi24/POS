package pos.pos.device.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeviceNetworkRefusalRepository extends JpaRepository<DeviceNetworkRefusal, UUID> {

    List<DeviceNetworkRefusal> findTop200ByBranchIdAndRefusedAtAfterOrderByRefusedAtDesc(UUID branchId, OffsetDateTime after);

    boolean existsByBranchIdAndAddressAndRefusedAtAfter(UUID branchId, String address, OffsetDateTime after);

    @Modifying
    @Query("DELETE FROM DeviceNetworkRefusal r WHERE r.branchId = :branchId AND r.refusedAt < :before")
    int forgetBefore(UUID branchId, OffsetDateTime before);

    @Modifying
    @Query("DELETE FROM DeviceNetworkRefusal r WHERE r.branchId = :branchId AND r.address = :address")
    int forgetAddress(UUID branchId, String address);
}
