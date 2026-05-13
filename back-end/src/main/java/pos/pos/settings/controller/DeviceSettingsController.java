package pos.pos.settings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.settings.dto.DeviceResponse;
import pos.pos.settings.dto.PrinterRouteResponse;
import pos.pos.settings.dto.PrinterRouteTestResponse;
import pos.pos.settings.dto.UpdateDeviceStatusRequest;
import pos.pos.settings.dto.UpdatePrinterRoutesRequest;
import pos.pos.settings.dto.UpsertDeviceRequest;
import pos.pos.settings.service.DeviceSettingsService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class DeviceSettingsController {

    private final DeviceSettingsService deviceSettingsService;

    @GetMapping("/restaurants/{restaurantId}/settings/printers")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List restaurant printer settings")
    public ResponseEntity<List<DeviceResponse>> getPrinters(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.getPrinters(authentication, restaurantId));
    }

    @PostMapping("/restaurants/{restaurantId}/settings/printers")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create restaurant printer settings")
    public ResponseEntity<DeviceResponse> createPrinter(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpsertDeviceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceSettingsService.createPrinter(authentication, restaurantId, request));
    }

    @PutMapping("/restaurants/{restaurantId}/settings/printers/{printerId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace restaurant printer settings")
    public ResponseEntity<DeviceResponse> updatePrinter(
            @PathVariable UUID restaurantId,
            @PathVariable UUID printerId,
            @Valid @RequestBody UpsertDeviceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.updatePrinter(authentication, restaurantId, printerId, request));
    }

    @DeleteMapping("/restaurants/{restaurantId}/settings/printers/{printerId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete restaurant printer settings")
    public ResponseEntity<Void> deletePrinter(
            @PathVariable UUID restaurantId,
            @PathVariable UUID printerId,
            Authentication authentication
    ) {
        deviceSettingsService.deletePrinter(authentication, restaurantId, printerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/printer-routes")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get branch printer routes")
    public ResponseEntity<PrinterRouteResponse> getPrinterRoutes(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.getPrinterRoutes(authentication, restaurantId, branchId));
    }

    @PutMapping("/restaurants/{restaurantId}/branches/{branchId}/printer-routes")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace branch printer routes")
    public ResponseEntity<PrinterRouteResponse> updatePrinterRoutes(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpdatePrinterRoutesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.updatePrinterRoutes(authentication, restaurantId, branchId, request));
    }

    @PostMapping("/restaurants/{restaurantId}/branches/{branchId}/printer-routes/test")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Generate a test printer-route payload")
    public ResponseEntity<PrinterRouteTestResponse> testPrinterRoutes(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.testPrinterRoutes(authentication, restaurantId, branchId));
    }

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/devices")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch devices")
    public ResponseEntity<List<DeviceResponse>> getDevices(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.getDevices(authentication, restaurantId, branchId));
    }

    @PostMapping("/restaurants/{restaurantId}/branches/{branchId}/devices")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a branch device")
    public ResponseEntity<DeviceResponse> createDevice(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpsertDeviceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(deviceSettingsService.createDevice(authentication, restaurantId, branchId, request));
    }

    @PutMapping("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a branch device")
    public ResponseEntity<DeviceResponse> updateDevice(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpsertDeviceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.updateDevice(authentication, restaurantId, branchId, deviceId, request));
    }

    @PatchMapping("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}/status")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update a branch device status")
    public ResponseEntity<DeviceResponse> updateDeviceStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateDeviceStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(deviceSettingsService.updateDeviceStatus(authentication, restaurantId, branchId, deviceId, request));
    }

    @DeleteMapping("/restaurants/{restaurantId}/branches/{branchId}/devices/{deviceId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete a branch device")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID deviceId,
            Authentication authentication
    ) {
        deviceSettingsService.deleteDevice(authentication, restaurantId, branchId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
