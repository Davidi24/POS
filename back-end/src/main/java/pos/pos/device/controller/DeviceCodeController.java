package pos.pos.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.dto.DevicePairingRedeemRequest;
import pos.pos.device.dto.DevicePairingRedeemResponse;
import pos.pos.device.service.DevicePairingService;
import pos.pos.security.service.PublicRequestRateLimiter;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.settings.service.SettingsDomainSupport;

import java.util.UUID;

/**
 * Admin Hub → Devices → Enter a code (user, 2026-11-25: "the person who puts the code logs in and puts it in the Devices section,
 * so I know who set it up"): the code made for a till or tablet, typed on it by someone signed in there.
 */
@Tag(name = "Devices")
@Validated
@RestController
@RequiredArgsConstructor
public class DeviceCodeController {

    private final DevicePairingService devicePairingService;
    private final ClientInfoExtractor clientInfoExtractor;
    private final PublicRequestRateLimiter rateLimiter;
    private final SettingsDomainSupport settingsDomainSupport;

    @org.springframework.beans.factory.annotation.Value("${app.devices.pairing.max-tries-per-address:30}")
    private int maxTriesPerAddress = 30;

    @org.springframework.beans.factory.annotation.Value("${app.devices.pairing.tries-window-minutes:10}")
    private int triesWindowMinutes = 10;

    @PostMapping("/restaurants/{restaurantId}/device-pairing/redeem")
    @PreAuthorize("hasAnyAuthority('DEVICES_CONNECT', 'DEVICES_MANAGE')")
    @Operation(summary = "Use this device: connect the device you are signed in on with a code you just made for it")
    public ResponseEntity<DevicePairingRedeemResponse> redeem(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody DevicePairingRedeemRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        pos.pos.security.rbac.PinSignIn.refuse(authentication, pos.pos.security.rbac.PinSignIn.CONNECT_DEVICES);
        String address = clientInfoExtractor.extractIp(servletRequest);
        rateLimiter.check("DEVICE_PAIRING_IP", address, maxTriesPerAddress, java.time.Duration.ofMinutes(triesWindowMinutes),
                "Too many tries. Wait a few minutes and try again.");
        try {
            return ResponseEntity.ok(devicePairingService.redeemOwn(request.pairingToken(), address,
                    settingsDomainSupport.currentActorId(authentication), restaurantId));
        } catch (pos.pos.exception.auth.AuthException refused) {
            // A wrong code is not a sign-in problem: a 401 here would make the app refresh its sign-in and send it again.
            if (refused.getStatus() != org.springframework.http.HttpStatus.UNAUTHORIZED) throw refused;
            throw new pos.pos.exception.auth.AuthException("This code doesn't work. It may be used, ended, or typed wrong.",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }
}
