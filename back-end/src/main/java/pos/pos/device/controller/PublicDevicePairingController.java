package pos.pos.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.dto.DevicePairingRedeemRequest;
import pos.pos.device.dto.DevicePairingRedeemResponse;
import pos.pos.device.service.DevicePairingService;

@Tag(name = "Device Pairing")
@Validated
@RestController
@RequestMapping("/public/devices/pairing")
@RequiredArgsConstructor
public class PublicDevicePairingController {

    private final DevicePairingService devicePairingService;
    private final pos.pos.security.util.ClientInfoExtractor clientInfoExtractor;
    private final pos.pos.security.service.PublicRequestRateLimiter rateLimiter;

    // Codes are short enough to type (user, 2026-11-25), so tries are limited per network address.
    @org.springframework.beans.factory.annotation.Value("${app.devices.pairing.max-tries-per-address:30}")
    private int maxTriesPerAddress = 30;

    @org.springframework.beans.factory.annotation.Value("${app.devices.pairing.tries-window-minutes:10}")
    private int triesWindowMinutes = 10;

    @PostMapping("/redeem")
    @Operation(summary = "Redeem a one-time device pairing token")
    public ResponseEntity<DevicePairingRedeemResponse> redeem(
            @Valid @RequestBody DevicePairingRedeemRequest request,
            HttpServletRequest servletRequest
    ) {
        String address = clientInfoExtractor.extractIp(servletRequest);
        rateLimiter.check("DEVICE_PAIRING_IP", address, maxTriesPerAddress, java.time.Duration.ofMinutes(triesWindowMinutes),
                "Too many tries. Wait a few minutes and try again.");
        return ResponseEntity.ok(devicePairingService.redeem(request.pairingToken(), address));
    }
}
