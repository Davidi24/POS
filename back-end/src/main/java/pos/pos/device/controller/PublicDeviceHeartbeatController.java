package pos.pos.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.dto.DeviceHeartbeatResponse;
import pos.pos.device.service.DeviceHeartbeatService;

import java.util.UUID;

@Tag(name = "Device Pairing")
@RestController
@RequestMapping("/public/devices")
@RequiredArgsConstructor
public class PublicDeviceHeartbeatController {

    private final DeviceHeartbeatService deviceHeartbeatService;

    @PostMapping("/{deviceId}/heartbeat")
    @Operation(summary = "Authenticate a paired device and record its heartbeat")
    public ResponseEntity<DeviceHeartbeatResponse> heartbeat(
            @PathVariable UUID deviceId,
            @RequestHeader(value = "X-Device-Secret", required = false) String deviceSecret,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(deviceHeartbeatService.heartbeat(
                deviceId,
                deviceSecret,
                servletRequest.getRemoteAddr()
        ));
    }
}
