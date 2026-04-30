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
import pos.pos.settings.dto.BulkDeleteSpecialHoursRequest;
import pos.pos.settings.dto.BulkUpsertSpecialHoursRequest;
import pos.pos.settings.dto.SpecialHourResponse;
import pos.pos.settings.dto.UpdateSpecialHourStatusRequest;
import pos.pos.settings.dto.UpsertSpecialHourRequest;
import pos.pos.settings.service.SettingsDetailService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/special-hours")
@RequiredArgsConstructor
public class BranchSpecialHourSettingsController {

    private final SettingsDetailService settingsDetailService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List branch special hours")
    public ResponseEntity<List<SpecialHourResponse>> getSpecialHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getSpecialHours(authentication, restaurantId, branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create branch special hours for one date")
    public ResponseEntity<SpecialHourResponse> createSpecialHour(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody UpsertSpecialHourRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settingsDetailService.createSpecialHour(authentication, restaurantId, branchId, request));
    }

    @GetMapping("/{specialHourId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get branch special hours for one date entry")
    public ResponseEntity<SpecialHourResponse> getSpecialHour(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID specialHourId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getSpecialHour(authentication, restaurantId, branchId, specialHourId));
    }

    @PutMapping("/{specialHourId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace branch special hours for one date entry")
    public ResponseEntity<SpecialHourResponse> updateSpecialHour(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID specialHourId,
            @Valid @RequestBody UpsertSpecialHourRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateSpecialHour(authentication, restaurantId, branchId, specialHourId, request));
    }

    @PatchMapping("/{specialHourId}/status")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update closed or open status for a special hours entry")
    public ResponseEntity<SpecialHourResponse> updateSpecialHourStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID specialHourId,
            @Valid @RequestBody UpdateSpecialHourStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateSpecialHourStatus(authentication, restaurantId, branchId, specialHourId, request));
    }

    @DeleteMapping("/{specialHourId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete one special hours entry")
    public ResponseEntity<Void> deleteSpecialHour(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID specialHourId,
            Authentication authentication
    ) {
        settingsDetailService.deleteSpecialHour(authentication, restaurantId, branchId, specialHourId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Bulk create or replace branch special hours")
    public ResponseEntity<List<SpecialHourResponse>> bulkUpsertSpecialHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody BulkUpsertSpecialHoursRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.bulkUpsertSpecialHours(authentication, restaurantId, branchId, request));
    }

    @DeleteMapping("/bulk")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Bulk delete branch special hours")
    public ResponseEntity<Void> bulkDeleteSpecialHours(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @Valid @RequestBody BulkDeleteSpecialHoursRequest request,
            Authentication authentication
    ) {
        settingsDetailService.bulkDeleteSpecialHours(authentication, restaurantId, branchId, request);
        return ResponseEntity.noContent().build();
    }
}
