package pos.pos.device.signin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DeviceAllowedRoleRepository extends JpaRepository<DeviceAllowedRole, UUID> {
    List<DeviceAllowedRole> findByDeviceId(UUID deviceId);

    @Modifying
    @Query("delete from DeviceAllowedRole r where r.deviceId = :deviceId")
    void deleteByDevice(UUID deviceId);
}
