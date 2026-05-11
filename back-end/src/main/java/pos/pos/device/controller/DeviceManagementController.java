package pos.pos.device.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.dto.CreateDeviceAssignmentRequest;
import pos.pos.device.dto.CreateDevicePairingTokenRequest;
import pos.pos.device.dto.DeviceAssignmentResponse;
import pos.pos.device.dto.DevicePairingTokenResponse;
import pos.pos.device.service.DeviceManagementService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Devices")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/devices/{deviceId}")
@RequiredArgsConstructor
public class DeviceManagementController {

    private final DeviceManagementService deviceManagementService;

    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List device assignment history")
    public ResponseEntity<List<DeviceAssignmentResponse>> getAssignments(
            @PathVariable UUID restaurantId,
            @PathVariable UUID deviceId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceManagementService.getAssignments(authentication, restaurantId, deviceId));
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create or replace the active device assignment")
    public ResponseEntity<DeviceAssignmentResponse> createAssignment(
            @PathVariable UUID restaurantId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody CreateDeviceAssignmentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceManagementService.createAssignment(authentication, restaurantId, deviceId, request));
    }

    @PostMapping("/assignments/{assignmentId}/unassign")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Close an active device assignment")
    public ResponseEntity<DeviceAssignmentResponse> unassign(
            @PathVariable UUID restaurantId,
            @PathVariable UUID deviceId,
            @PathVariable UUID assignmentId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceManagementService.unassign(authentication, restaurantId, deviceId, assignmentId));
    }

    @GetMapping("/pairing-tokens")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List device pairing token history")
    public ResponseEntity<List<DevicePairingTokenResponse>> getPairingTokens(
            @PathVariable UUID restaurantId,
            @PathVariable UUID deviceId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceManagementService.getPairingTokens(authentication, restaurantId, deviceId));
    }

    @PostMapping("/pairing-tokens")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Issue a new device pairing token")
    public ResponseEntity<DevicePairingTokenResponse> createPairingToken(
            @PathVariable UUID restaurantId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody CreateDevicePairingTokenRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceManagementService.createPairingToken(authentication, restaurantId, deviceId, request));
    }

    @PostMapping("/pairing-tokens/{pairingTokenId}/revoke")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Revoke a device pairing token")
    public ResponseEntity<DevicePairingTokenResponse> revokePairingToken(
            @PathVariable UUID restaurantId,
            @PathVariable UUID deviceId,
            @PathVariable UUID pairingTokenId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                deviceManagementService.revokePairingToken(authentication, restaurantId, deviceId, pairingTokenId)
        );
    }
}
