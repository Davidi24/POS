package pos.pos.device.connect;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.dto.DevicePairingRedeemResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.security.rbac.PinSignIn;
import pos.pos.security.service.PublicRequestRateLimiter;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.ClientInfoNormalizer;
import pos.pos.settings.service.SettingsDomainSupport;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Admin Hub → Devices → Enter a code, typed on the new device, waits for the person who made the code to agree (user,
 * 2026-11-25). See `DeviceConnectRequestService`.
 */
@Tag(name = "Devices")
@Validated
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('DEVICES_CONNECT', 'DEVICES_MANAGE')")
public class DeviceConnectRequestController {

    private static final String BASE = "/restaurants/{restaurantId}/device-connect-requests";

    private final DeviceConnectRequestService service;
    private final ClientInfoExtractor clientInfoExtractor;
    private final PublicRequestRateLimiter rateLimiter;
    private final SettingsDomainSupport settingsDomainSupport;

    @org.springframework.beans.factory.annotation.Value("${app.devices.pairing.max-tries-per-address:30}")
    private int maxTriesPerAddress = 30;

    @org.springframework.beans.factory.annotation.Value("${app.devices.pairing.tries-window-minutes:10}")
    private int triesWindowMinutes = 10;

    @PostMapping(BASE)
    @Operation(summary = "Ask to make the device you're on the till or tablet a code was made for")
    public DeviceConnectDtos.Asked ask(@PathVariable UUID restaurantId, @Valid @RequestBody DeviceConnectDtos.AskRequest body,
                                       Authentication authentication, HttpServletRequest servletRequest) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        PinSignIn.refuse(authentication, PinSignIn.CONNECT_DEVICES);
        String address = clientInfoExtractor.extractIp(servletRequest);
        rateLimiter.check("DEVICE_PAIRING_IP", address, maxTriesPerAddress, Duration.ofMinutes(triesWindowMinutes),
                "Too many tries. Wait a few minutes and try again.");
        try {
            return service.ask(body.pairingToken(), restaurantId, settingsDomainSupport.currentActorId(authentication),
                    ClientInfoNormalizer.normalizeDeviceName(servletRequest.getHeader("X-Device-Name")),
                    ClientInfoNormalizer.normalizeDeviceSystem(servletRequest.getHeader("X-Device-System")), address);
        } catch (AuthException refused) {
            // A wrong code is not a sign-in problem: a 401 here would make the app refresh its sign-in and send it again.
            if (refused.getStatus() != HttpStatus.UNAUTHORIZED) throw refused;
            throw new AuthException("This code doesn't work. It may be used, ended, or typed wrong.", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(BASE + "/waiting")
    @Operation(summary = "Codes you made, typed on another device, waiting for you to agree")
    public List<DeviceConnectDtos.RequestView> waiting(@PathVariable UUID restaurantId, Authentication authentication) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        if (PinSignIn.is(authentication)) return List.of(); // agreeing needs the password
        return service.waitingFor(restaurantId, settingsDomainSupport.currentActorId(authentication));
    }

    @GetMapping(BASE + "/{requestId}")
    public DeviceConnectDtos.RequestView get(@PathVariable UUID restaurantId, @PathVariable UUID requestId, Authentication authentication) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        return service.get(requestId, restaurantId, settingsDomainSupport.currentActorId(authentication));
    }

    @PostMapping(BASE + "/{requestId}/agree")
    public DeviceConnectDtos.RequestView agree(@PathVariable UUID restaurantId, @PathVariable UUID requestId, Authentication authentication) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        PinSignIn.refuse(authentication, PinSignIn.CONNECT_DEVICES);
        return service.agree(requestId, restaurantId, settingsDomainSupport.currentActorId(authentication));
    }

    @PostMapping(BASE + "/{requestId}/refuse")
    public DeviceConnectDtos.RequestView refuse(@PathVariable UUID restaurantId, @PathVariable UUID requestId, Authentication authentication) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        return service.refuse(requestId, restaurantId, settingsDomainSupport.currentActorId(authentication));
    }

    @DeleteMapping(BASE + "/{requestId}")
    public DeviceConnectDtos.RequestView cancel(@PathVariable UUID restaurantId, @PathVariable UUID requestId, Authentication authentication) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        return service.cancel(requestId, restaurantId, settingsDomainSupport.currentActorId(authentication));
    }

    @PostMapping(BASE + "/{requestId}/claim")
    @Operation(summary = "Agreed: the device that asked takes the connection")
    public DevicePairingRedeemResponse claim(@PathVariable UUID restaurantId, @PathVariable UUID requestId,
                                             @Valid @RequestBody DeviceConnectDtos.ClaimRequest body,
                                             Authentication authentication, HttpServletRequest servletRequest) {
        settingsDomainSupport.requireManageableRestaurant(authentication, restaurantId);
        PinSignIn.refuse(authentication, PinSignIn.CONNECT_DEVICES);
        try {
            return service.claim(requestId, restaurantId, settingsDomainSupport.currentActorId(authentication), body.claimToken(),
                    clientInfoExtractor.extractIp(servletRequest));
        } catch (AuthException refused) {
            if (refused.getStatus() != HttpStatus.UNAUTHORIZED) throw refused;
            throw new AuthException("This code doesn't work any more. Ask for a new one.", HttpStatus.BAD_REQUEST);
        }
    }
}
