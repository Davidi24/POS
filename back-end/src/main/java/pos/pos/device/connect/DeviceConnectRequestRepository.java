package pos.pos.device.connect;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceConnectRequestRepository extends JpaRepository<DeviceConnectRequest, UUID> {

    Optional<DeviceConnectRequest> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    List<DeviceConnectRequest> findByPairingTokenIdAndStatusIn(UUID pairingTokenId, Collection<String> statuses);

    List<DeviceConnectRequest> findByRestaurantIdAndCodeMadeByAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(
            UUID restaurantId, UUID codeMadeBy, String status, OffsetDateTime after);

    @Modifying
    @Query("DELETE FROM DeviceConnectRequest r WHERE r.createdAt < :before")
    int forgetBefore(OffsetDateTime before);
}
