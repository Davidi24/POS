package pos.pos.device.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.device.entity.DevicePairingToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevicePairingTokenRepository extends JpaRepository<DevicePairingToken, UUID> {

    @EntityGraph(attributePaths = "createdByUser")
    List<DevicePairingToken> findAllByDevice_IdOrderByCreatedAtDesc(UUID deviceId);

    @EntityGraph(attributePaths = "createdByUser")
    Optional<DevicePairingToken> findByIdAndDevice_Id(UUID pairingTokenId, UUID deviceId);

    List<DevicePairingToken> findAllByDevice_IdAndUsedAtIsNullAndRevokedAtIsNull(UUID deviceId);
}
