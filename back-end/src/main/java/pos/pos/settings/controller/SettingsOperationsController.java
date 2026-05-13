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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.common.dto.PageResponse;
import pos.pos.settings.dto.BranchEffectiveSettingsResponse;
import pos.pos.settings.dto.EffectiveBusinessHoursResponse;
import pos.pos.settings.dto.EffectiveReservationRulesResponse;
import pos.pos.settings.dto.SettingsAuditLogResponse;
import pos.pos.settings.dto.SettingsExportResponse;
import pos.pos.settings.dto.SettingsTransferRequest;
import pos.pos.settings.dto.SettingsValidationResponse;
import pos.pos.settings.dto.SpecialHourCalendarResponse;
import pos.pos.settings.dto.TodayBusinessHoursResponse;
import pos.pos.settings.service.SettingsOperationsService;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class SettingsOperationsController {

    private final SettingsOperationsService settingsOperationsService;

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/settings/effective")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get effective branch settings")
    public ResponseEntity<BranchEffectiveSettingsResponse> getEffectiveBranchSettings(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getEffectiveBranchSettings(authentication, restaurantId, branchId));
    }

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/reservation-rules/effective")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get effective reservation rules for a branch")
    public ResponseEntity<EffectiveReservationRulesResponse> getEffectiveReservationRules(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getEffectiveReservationRules(authentication, restaurantId, branchId));
    }

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/business-hours/today")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get today's effective business hours for a branch")
    public ResponseEntity<TodayBusinessHoursResponse> getTodayBusinessHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getTodayBusinessHours(authentication, restaurantId, branchId));
    }

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/business-hours/effective")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get weekly business hours and upcoming special hours for a branch")
    public ResponseEntity<EffectiveBusinessHoursResponse> getEffectiveBusinessHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getEffectiveBusinessHours(authentication, restaurantId, branchId));
    }

    @GetMapping("/restaurants/{restaurantId}/branches/{branchId}/special-hours/calendar")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get special hours for a branch in a date range")
    public ResponseEntity<SpecialHourCalendarResponse> getSpecialHoursCalendar(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getSpecialHoursCalendar(
                authentication,
                restaurantId,
                branchId,
                startDate,
                endDate
        ));
    }

    @PostMapping("/restaurants/{restaurantId}/settings/validate")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Validate a settings transfer payload against the target restaurant")
    public ResponseEntity<SettingsValidationResponse> validateTransferPayload(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody SettingsTransferRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.validateTransferPayload(authentication, restaurantId, request));
    }

    @GetMapping("/restaurants/{restaurantId}/settings/history")
    @PreAuthorize("hasAuthority('SETTINGS_AUDIT')")
    @Operation(summary = "Get settings history timeline")
    public ResponseEntity<PageResponse<SettingsAuditLogResponse>> getSettingsHistory(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getSettingsHistory(authentication, restaurantId, page, size));
    }

    @GetMapping("/restaurants/{restaurantId}/settings/audit-logs")
    @PreAuthorize("hasAuthority('SETTINGS_AUDIT')")
    @Operation(summary = "Get settings audit logs")
    public ResponseEntity<PageResponse<SettingsAuditLogResponse>> getSettingsAuditLogs(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.getSettingsAuditLogs(authentication, restaurantId, page, size));
    }

    @GetMapping("/restaurants/{restaurantId}/settings/export")
    @PreAuthorize("hasAuthority('SETTINGS_EXPORT')")
    @Operation(summary = "Export restaurant settings as a portable payload")
    public ResponseEntity<SettingsExportResponse> exportSettings(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.exportSettings(authentication, restaurantId));
    }

    @PostMapping("/restaurants/{restaurantId}/settings/import")
    @PreAuthorize("hasAuthority('SETTINGS_IMPORT')")
    @Operation(summary = "Import restaurant settings from a portable payload")
    public ResponseEntity<SettingsExportResponse> importSettings(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody SettingsTransferRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.importSettings(authentication, restaurantId, request));
    }

    @PostMapping("/restaurants/{restaurantId}/settings/clone-from-restaurant/{sourceRestaurantId}")
    @PreAuthorize("hasAuthority('SETTINGS_IMPORT')")
    @Operation(summary = "Clone settings from another restaurant")
    public ResponseEntity<SettingsExportResponse> cloneSettings(
            @PathVariable UUID restaurantId,
            @PathVariable UUID sourceRestaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsOperationsService.cloneSettings(authentication, restaurantId, sourceRestaurantId));
    }
}
