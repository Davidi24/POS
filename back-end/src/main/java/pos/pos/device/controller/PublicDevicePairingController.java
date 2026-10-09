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

    @PostMapping("/redeem")
    @Operation(summary = "Redeem a one-time device pairing token")
    public ResponseEntity<DevicePairingRedeemResponse> redeem(
            @Valid @RequestBody DevicePairingRedeemRequest request,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(devicePairingService.redeem(request.pairingToken(), servletRequest.getRemoteAddr()));
    }
}
