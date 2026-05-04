package pos.pos.device.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.device.entity.Device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByRestaurant_IdAndCode(UUID restaurantId, String code);

    List<Device> findAllByRestaurant_IdOrderByNameAsc(UUID restaurantId);

    List<Device> findAllByRestaurant_IdAndBranch_IdOrderByNameAsc(UUID restaurantId, UUID branchId);

    Optional<Device> findByIdAndRestaurant_Id(UUID deviceId, UUID restaurantId);
}
