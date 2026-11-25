package pos.pos.device.signin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin Hub → Devices → a till or tablet → Who can sign in here. */
@Tag(name = "Devices")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/devices/{deviceId}/sign-in")
@RequiredArgsConstructor
public class DeviceSignInController {

    private final DeviceSignInService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS_READ', 'DEVICES_CONNECT', 'DEVICES_MANAGE')")
    @Operation(summary = "Who can sign in on a till or tablet")
    public ResponseEntity<DeviceSignInDtos.DeviceSignInView> get(@PathVariable UUID restaurantId, @PathVariable UUID deviceId,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(service.view(authentication, restaurantId, deviceId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('DEVICES_MANAGE')")
    @Operation(summary = "Choose who can sign in on a till or tablet (nothing = everyone at its branch)")
    public ResponseEntity<DeviceSignInDtos.DeviceSignInView> set(@PathVariable UUID restaurantId, @PathVariable UUID deviceId,
                                                                 @Valid @RequestBody DeviceSignInDtos.SetDeviceSignInRequest request,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(service.set(authentication, restaurantId, deviceId, request));
    }
}
