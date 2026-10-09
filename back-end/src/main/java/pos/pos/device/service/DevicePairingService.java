package pos.pos.device.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.config.properties.DevicePairingProperties;
import pos.pos.device.dto.DevicePairingRedeemResponse;
import pos.pos.device.entity.Device;
import pos.pos.device.entity.DevicePairingToken;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.repository.DevicePairingTokenRepository;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.device.DeviceNotFoundException;
import pos.pos.security.service.OpaqueTokenService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class DevicePairingService {

    private final DevicePairingTokenRepository pairingTokenRepository;
    private final DeviceRepository deviceRepository;
    private final EntityManager entityManager;
    private final DevicePairingProperties pairingProperties;
    private final OpaqueTokenService opaqueTokenService;

    /** Redeem a one-time enrollment token and return a device secret exactly once. */
    @Transactional
    public DevicePairingRedeemResponse redeem(String rawPairingToken, String remoteAddress) {
        String tokenHash = opaqueTokenService.hash(rawPairingToken, pairingProperties.getTokenPepper());
        DevicePairingToken token = pairingTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidPairingToken);
        Device observedDevice = token.getDevice();
        Device device = deviceRepository.findForUpdateByIdAndRestaurantId(
                        observedDevice.getId(), observedDevice.getRestaurant().getId())
                .orElseThrow(DeviceNotFoundException::new);
        // All token issue/revoke/retire operations lock the parent device first. Refresh both rows
        // after acquiring that lock so concurrent redeemers observe the winning transaction.
        entityManager.refresh(device, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(token);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!token.isActiveAt(now)
                || !device.isActive()
                || (device.getStatus() != DeviceStatus.PROVISIONING && device.getStatus() != DeviceStatus.ACTIVE)) {
            throw invalidPairingToken();
        }

        OpaqueTokenService.IssuedToken issuedSecret = opaqueTokenService.issue(pairingProperties.getTokenPepper());
        device.setAuthSecretHash(issuedSecret.tokenHash());
        device.setAuthSecretRotatedAt(now);
        device.setStatus(DeviceStatus.ACTIVE);
        device.setOnline(true);
        device.setLastSeenAt(now);
        if (StringUtils.hasText(remoteAddress)) {
            device.setIpAddress(remoteAddress);
        }
        token.setUsedAt(now);

        deviceRepository.saveAndFlush(device);
        pairingTokenRepository.saveAndFlush(token);

        return new DevicePairingRedeemResponse(
                device.getId(),
                device.getRestaurant().getId(),
                device.getBranch() == null ? null : device.getBranch().getId(),
                device.getName(),
                device.getDeviceType().name(),
                issuedSecret.rawToken()
        );
    }

    private AuthException invalidPairingToken() {
        // Do not distinguish unknown, expired, revoked, used, or disabled-device tokens to callers.
        return new AuthException("Pairing token is invalid or expired", HttpStatus.UNAUTHORIZED);
    }
}
