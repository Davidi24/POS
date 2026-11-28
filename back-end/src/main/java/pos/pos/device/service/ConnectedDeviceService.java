package pos.pos.device.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.security.service.OpaqueTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

/** A till or tablet connected to its restaurant (its id and secret from pairing), for PIN sign-in (user, 2026-11-18). */
@Service
@RequiredArgsConstructor
public class ConnectedDeviceService {

    private final DeviceRepository deviceRepository;
    private final DevicePairingProperties pairingProperties;
    private final OpaqueTokenService opaqueTokenService;

    /** The device and its restaurant when the id and secret match an active, connected device; empty otherwise. */
    public record Connected(UUID deviceId, UUID restaurantId, UUID branchId, String name) {
    }

    @Transactional(readOnly = true)
    public Optional<Connected> check(UUID deviceId, String rawSecret) {
        if (deviceId == null || !StringUtils.hasText(rawSecret)) return Optional.empty();
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || !device.isActive() || device.getStatus() != DeviceStatus.ACTIVE || device.getRestaurant() == null) return Optional.empty();
        String stored = device.getAuthSecretHash();
        String supplied = opaqueTokenService.hash(rawSecret, pairingProperties.getTokenPepper());
        if (!StringUtils.hasText(stored) || !MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        return Optional.of(new Connected(device.getId(), device.getRestaurant().getId(),
                device.getBranch() == null ? null : device.getBranch().getId(), device.getName()));
    }
}
