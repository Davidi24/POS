package pos.pos.device.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.device.entity.Device;

import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByRestaurant_IdAndCode(UUID restaurantId, String code);
}
