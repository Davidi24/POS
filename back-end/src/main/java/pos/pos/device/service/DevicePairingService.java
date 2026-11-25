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
    private final pos.pos.auth.service.AccountAudit accountAudit;
    private final pos.pos.device.network.DeviceNetworkService deviceNetworks;
    private final pos.pos.user.repository.UserRepository userRepository;

    /**
     * Redeem a one-time enrollment token and return a device secret exactly once. A till, tablet or screen is connected only
     * from its restaurant's network while that's switched on (`DeviceNetworkService.beforeConnect`; a refusal is kept).
     */
    @Transactional(noRollbackFor = pos.pos.device.network.OutsideNetworkException.class)
    public DevicePairingRedeemResponse redeem(String rawPairingToken, String remoteAddress) {
        return redeem(rawPairingToken, remoteAddress, null, null);
    }

    /**
     * A code typed on the new till by someone signed in on it ([enteredBy], in [restaurantId]; user, 2026-11-25: "the person
     * who puts the code logs in and puts it in the Devices section, so I know who set it up"). A code of another restaurant is
     * as good as none.
     */
    @Transactional(noRollbackFor = pos.pos.device.network.OutsideNetworkException.class)
    public DevicePairingRedeemResponse redeem(String rawPairingToken, String remoteAddress, java.util.UUID enteredBy, java.util.UUID restaurantId) {
        // A typed code in any case, with or without its dash (user, 2026-11-25); older long codes as they are.
        String tokenHash = opaqueTokenService.hash(PairingCodes.typed(rawPairingToken), pairingProperties.getTokenPepper());
        DevicePairingToken token = pairingTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidPairingToken);
        Device observedDevice = token.getDevice();
        if (restaurantId != null && !restaurantId.equals(observedDevice.getRestaurant().getId())) throw invalidPairingToken();
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
        pos.pos.device.network.DeviceNetworkService.ConnectCheck network = deviceNetworks.beforeConnect(device, token.getCreatedBy(), enteredBy, remoteAddress);

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
        // Connecting a till or tablet is what lets staff sign in on it with a PIN (user, 2026-11-18), so it's in the log.
        String typedBy = enteredBy == null || enteredBy.equals(token.getCreatedBy()) ? "" : userRepository.findById(enteredBy)
                .map(u -> ", typed in by " + pos.pos.auth.service.AccountAudit.nameOf(u)).orElse("");
        String madeBy = token.getCreatedBy() == null ? "" : userRepository.findById(token.getCreatedBy())
                .map(u -> " made by " + pos.pos.auth.service.AccountAudit.nameOf(u)).orElse("");
        accountAudit.inRestaurant(device.getRestaurant().getId(), device.getId(), enteredBy != null ? enteredBy : token.getCreatedBy(), "DEVICE_CONNECTED",
                device.getName() + " was connected with a pairing code" + madeBy + typedBy
                        + (StringUtils.hasText(remoteAddress) ? " from " + remoteAddress : "")
                        + (network.outside() ? " (outside the restaurant's network, by an owner)" : ""));

        return new DevicePairingRedeemResponse(
                device.getId(),
                device.getRestaurant().getId(),
                device.getBranch() == null ? null : device.getBranch().getId(),
                device.getName(),
                device.getDeviceType().name(),
                issuedSecret.rawToken(),
                network.learned()
        );
    }

    private AuthException invalidPairingToken() {
        // Do not distinguish unknown, expired, revoked, used, or disabled-device tokens to callers.
        return new AuthException("Pairing token is invalid or expired", HttpStatus.UNAUTHORIZED);
    }
}
