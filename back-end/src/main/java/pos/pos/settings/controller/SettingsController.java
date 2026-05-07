package pos.pos.settings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.settings.dto.SettingsResponse;
import pos.pos.settings.dto.UpdateRestaurantSettingsRequest;
import pos.pos.settings.dto.UpdateSettingsBillingRequest;
import pos.pos.settings.dto.UpdateSettingsDefaultBranchRequest;
import pos.pos.settings.dto.UpdateSettingsLocalizationRequest;
import pos.pos.settings.dto.UpdateSettingsOrderChannelsRequest;
import pos.pos.settings.dto.UpdateSettingsSequencePrefixesRequest;
import pos.pos.settings.service.SettingsService;

import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get restaurant settings")
    public ResponseEntity<SettingsResponse> getSettings(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.getSettings(authentication, restaurantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace restaurant settings core fields")
    public ResponseEntity<SettingsResponse> updateSettings(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateRestaurantSettingsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.updateSettings(authentication, restaurantId, request));
    }

    @PatchMapping("/default-branch")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update the default branch for restaurant settings")
    public ResponseEntity<SettingsResponse> updateDefaultBranch(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateSettingsDefaultBranchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.updateDefaultBranch(authentication, restaurantId, request));
    }

    @PatchMapping("/localization")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update localization and calendar-related settings")
    public ResponseEntity<SettingsResponse> updateLocalization(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateSettingsLocalizationRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.updateLocalization(authentication, restaurantId, request));
    }

    @PatchMapping("/sequence-prefixes")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update sequence prefixes used by orders and invoices")
    public ResponseEntity<SettingsResponse> updateSequencePrefixes(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateSettingsSequencePrefixesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.updateSequencePrefixes(authentication, restaurantId, request));
    }

    @PatchMapping("/billing")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update billing-related settings")
    public ResponseEntity<SettingsResponse> updateBilling(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateSettingsBillingRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.updateBilling(authentication, restaurantId, request));
    }

    @PatchMapping("/order-channels")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update order channel and ticket behavior settings")
    public ResponseEntity<SettingsResponse> updateOrderChannels(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateSettingsOrderChannelsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.updateOrderChannels(authentication, restaurantId, request));
    }

    @PostMapping("/reset")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Reset restaurant settings core fields to defaults")
    public ResponseEntity<SettingsResponse> resetSettings(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsService.resetSettings(authentication, restaurantId));
    }
}
