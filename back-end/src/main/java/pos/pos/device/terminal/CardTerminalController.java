package pos.pos.device.terminal;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceStatus;
import pos.pos.device.enums.DeviceType;
import pos.pos.device.repository.DeviceRepository;
import pos.pos.settings.service.SettingsDomainSupport;

import java.util.UUID;

/**
 * The card terminal of the till or tablet the app runs on (user, 2026-11-27): a card terminal works only through the till or
 * tablet it's connected to, by Bluetooth or cable; a device with none takes no card payments on a terminal.
 */
@RestController
@RequiredArgsConstructor
public class CardTerminalController {

    /** The terminal a till sends card payments to: its name and how it's connected. */
    public record CardTerminal(UUID id, String name, String linkType, String linkAddress) {
    }

    private final DeviceRepository devices;
    private final SettingsDomainSupport settingsDomainSupport;

    @GetMapping("/restaurants/{restaurantId}/card-terminal")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    @Operation(summary = "The card terminal connected to this till or tablet (none: 204)")
    public ResponseEntity<CardTerminal> terminal(@PathVariable UUID restaurantId, @RequestParam UUID deviceId, Authentication authentication) {
        settingsDomainSupport.requireAccessibleRestaurant(authentication, restaurantId);
        return devices.findByRestaurant_IdAndDeviceTypeAndLinkedDeviceIdOrderByNameAsc(restaurantId, DeviceType.PAYMENT_TERMINAL, deviceId).stream()
                .filter(device -> device.isActive() && (device.getStatus() == DeviceStatus.ACTIVE || device.getStatus() == DeviceStatus.PROVISIONING))
                .findFirst()
                .map(CardTerminalController::view)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private static CardTerminal view(Device device) {
        return new CardTerminal(device.getId(), device.getName(), device.getLinkType(), device.getLinkAddress());
    }
}
