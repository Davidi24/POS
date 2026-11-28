package pos.pos.device.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.dto.DeviceHeartbeatResponse;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.security.service.OpaqueTokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceHeartbeatService {

    private final DeviceRepository deviceRepository;
    private final EntityManager entityManager;
    private final DevicePairingProperties pairingProperties;
    private final OpaqueTokenService opaqueTokenService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private pos.pos.settings.repository.SettingsRepository settingsRepository;

    /** Authenticate a paired device and record its latest successful contact. */
    @Transactional
    public DeviceHeartbeatResponse heartbeat(UUID deviceId, String rawDeviceSecret, String remoteAddress) {
        return heartbeat(deviceId, rawDeviceSecret, remoteAddress, null);
    }

    /** The same, keeping what the app says it runs on ([about], when sent). */
    @Transactional
    public DeviceHeartbeatResponse heartbeat(UUID deviceId, String rawDeviceSecret, String remoteAddress,
                                             pos.pos.device.dto.DeviceHeartbeatRequest about) {
        if (!StringUtils.hasText(rawDeviceSecret)) {
            throw unauthorized();
        }

        Device device = deviceRepository.findForUpdateById(deviceId).orElseThrow(this::unauthorized);
        entityManager.refresh(device, LockModeType.PESSIMISTIC_WRITE);
        String storedHash = device.getAuthSecretHash();
        String suppliedHash = opaqueTokenService.hash(rawDeviceSecret, pairingProperties.getTokenPepper());
        if (!device.isActive()
                || device.getStatus() != DeviceStatus.ACTIVE
                || !StringUtils.hasText(storedHash)
                || !MessageDigest.isEqual(
                        storedHash.getBytes(StandardCharsets.US_ASCII),
                        suppliedHash.getBytes(StandardCharsets.US_ASCII)
                )) {
            throw unauthorized();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        device.setOnline(true);
        device.setLastSeenAt(now);
        if (StringUtils.hasText(remoteAddress)) {
            device.setIpAddress(remoteAddress);
        }
        if (about != null) {
            if (StringUtils.hasText(about.platform())) device.setPlatform(about.platform().trim());
            if (StringUtils.hasText(about.osVersion())) device.setOsVersion(about.osVersion().trim());
            if (StringUtils.hasText(about.appVersion())) device.setAppVersion(about.appVersion().trim());
        }
        deviceRepository.saveAndFlush(device);
        pos.pos.settings.entity.Settings rules = settingsRepository == null || device.getRestaurant() == null ? null
                : settingsRepository.findByRestaurant_Id(device.getRestaurant().getId()).orElse(null);
        return new DeviceHeartbeatResponse(device.getId(), true, now,
                rules == null ? null : rules.getTillLockSeconds(), rules == null ? null : rules.getTillLockImportantSeconds());
    }

    private AuthException unauthorized() {
        return new AuthException("Device authentication failed", HttpStatus.UNAUTHORIZED);
    }
}
