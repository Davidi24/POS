package pos.pos.device.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.device.entity.Device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByRestaurant_IdAndCode(UUID restaurantId, String code);

    @Query("""
        SELECT DISTINCT d
        FROM Device d
        LEFT JOIN FETCH d.printerProfile printerProfile
        WHERE d.restaurant.id = :restaurantId
          AND (printerProfile IS NOT NULL OR UPPER(d.deviceType) = 'PRINTER')
        ORDER BY d.name ASC
    """)
    List<Device> findRestaurantPrinters(UUID restaurantId);

    @Query("""
        SELECT DISTINCT d
        FROM Device d
        LEFT JOIN FETCH d.printerProfile printerProfile
        WHERE d.restaurant.id = :restaurantId
          AND d.branch.id = :branchId
          AND (printerProfile IS NOT NULL OR UPPER(d.deviceType) = 'PRINTER')
        ORDER BY d.name ASC
    """)
    List<Device> findBranchPrinters(UUID restaurantId, UUID branchId);

    @Query("""
        SELECT DISTINCT d
        FROM Device d
        LEFT JOIN FETCH d.printerProfile printerProfile
        WHERE d.restaurant.id = :restaurantId
          AND d.branch.id = :branchId
          AND printerProfile IS NULL
          AND UPPER(d.deviceType) <> 'PRINTER'
        ORDER BY d.name ASC
    """)
    List<Device> findBranchNonPrinterDevices(UUID restaurantId, UUID branchId);

    Optional<Device> findByIdAndRestaurant_Id(UUID deviceId, UUID restaurantId);

    @Query("""
        SELECT d
        FROM Device d
        LEFT JOIN FETCH d.printerProfile printerProfile
        WHERE d.id = :deviceId
          AND d.restaurant.id = :restaurantId
          AND (printerProfile IS NOT NULL OR UPPER(d.deviceType) = 'PRINTER')
    """)
    Optional<Device> findPrinterByIdAndRestaurantId(UUID deviceId, UUID restaurantId);

    @Query("""
        SELECT d
        FROM Device d
        LEFT JOIN FETCH d.printerProfile printerProfile
        WHERE d.id = :deviceId
          AND d.restaurant.id = :restaurantId
          AND d.branch.id = :branchId
          AND printerProfile IS NULL
          AND UPPER(d.deviceType) <> 'PRINTER'
    """)
    Optional<Device> findBranchNonPrinterById(UUID deviceId, UUID restaurantId, UUID branchId);

    @Query("""
        SELECT d
        FROM Device d
        LEFT JOIN FETCH d.printerProfile printerProfile
        WHERE d.id = :deviceId
          AND d.restaurant.id = :restaurantId
          AND d.branch IS NOT NULL
          AND printerProfile IS NULL
          AND UPPER(d.deviceType) <> 'PRINTER'
    """)
    Optional<Device> findRestaurantNonPrinterById(UUID deviceId, UUID restaurantId);
}
