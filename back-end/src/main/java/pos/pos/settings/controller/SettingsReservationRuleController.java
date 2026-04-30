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
import pos.pos.settings.dto.ReorderReservationRulesRequest;
import pos.pos.settings.dto.ReservationRuleResponse;
import pos.pos.settings.dto.UpdateReservationRulePriorityRequest;
import pos.pos.settings.dto.UpdateReservationRuleStatusRequest;
import pos.pos.settings.dto.UpsertReservationRuleRequest;
import pos.pos.settings.service.SettingsDetailService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/settings/reservation-rules")
@RequiredArgsConstructor
public class SettingsReservationRuleController {

    private final SettingsDetailService settingsDetailService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List reservation rules")
    public ResponseEntity<List<ReservationRuleResponse>> getReservationRules(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getReservationRules(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a reservation rule")
    public ResponseEntity<ReservationRuleResponse> createReservationRule(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpsertReservationRuleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settingsDetailService.createReservationRule(authentication, restaurantId, request));
    }

    @GetMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get one reservation rule")
    public ResponseEntity<ReservationRuleResponse> getReservationRule(
            @PathVariable UUID restaurantId,
            @PathVariable UUID ruleId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getReservationRule(authentication, restaurantId, ruleId));
    }

    @PutMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace a reservation rule")
    public ResponseEntity<ReservationRuleResponse> updateReservationRule(
            @PathVariable UUID restaurantId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpsertReservationRuleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateReservationRule(authentication, restaurantId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/status")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update reservation rule active status")
    public ResponseEntity<ReservationRuleResponse> updateReservationRuleStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateReservationRuleStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateReservationRuleStatus(authentication, restaurantId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/priority")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update reservation rule priority")
    public ResponseEntity<ReservationRuleResponse> updateReservationRulePriority(
            @PathVariable UUID restaurantId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdateReservationRulePriorityRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateReservationRulePriority(authentication, restaurantId, ruleId, request));
    }

    @DeleteMapping("/{ruleId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete a reservation rule")
    public ResponseEntity<Void> deleteReservationRule(
            @PathVariable UUID restaurantId,
            @PathVariable UUID ruleId,
            Authentication authentication
    ) {
        settingsDetailService.deleteReservationRule(authentication, restaurantId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reorder")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace reservation rule order")
    public ResponseEntity<List<ReservationRuleResponse>> reorderReservationRules(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody ReorderReservationRulesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.reorderReservationRules(authentication, restaurantId, request));
    }
}
