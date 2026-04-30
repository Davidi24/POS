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
import pos.pos.settings.dto.BusinessHourResponse;
import pos.pos.settings.dto.CopyBusinessHoursRequest;
import pos.pos.settings.dto.CopyBusinessHoursResponse;
import pos.pos.settings.dto.ReplaceBusinessHoursRequest;
import pos.pos.settings.dto.UpsertBusinessHourRequest;
import pos.pos.settings.service.SettingsDetailService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/business-hours")
@RequiredArgsConstructor
public class BranchBusinessHourSettingsController {

    private final SettingsDetailService settingsDetailService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch business hours")
    public ResponseEntity<List<BusinessHourResponse>> getBusinessHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getBusinessHours(authentication, restaurantId, branchId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace all branch business hours")
    public ResponseEntity<List<BusinessHourResponse>> replaceBusinessHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody ReplaceBusinessHoursRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.replaceBusinessHours(authentication, restaurantId, branchId, request));
    }

    @GetMapping("/{dayOfWeek}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get branch business hours for one weekday")
    public ResponseEntity<BusinessHourResponse> getBusinessHour(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable int dayOfWeek,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getBusinessHour(authentication, restaurantId, branchId, dayOfWeek));
    }

    @PatchMapping("/{dayOfWeek}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update branch business hours for one weekday")
    public ResponseEntity<BusinessHourResponse> updateBusinessHour(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable int dayOfWeek,
            @Valid @RequestBody UpsertBusinessHourRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateBusinessHour(authentication, restaurantId, branchId, dayOfWeek, request));
    }

    @PostMapping("/copy-to-other-branches")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Copy business hours to other branches in the same restaurant")
    public ResponseEntity<CopyBusinessHoursResponse> copyBusinessHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody CopyBusinessHoursRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.copyBusinessHours(authentication, restaurantId, branchId, request));
    }
}
