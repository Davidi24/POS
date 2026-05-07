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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.settings.dto.OrderRuleSettingsResponse;
import pos.pos.settings.dto.UpdateOrderRuleDiscountPolicyRequest;
import pos.pos.settings.dto.UpdateOrderRuleSettingsRequest;
import pos.pos.settings.dto.UpdateOrderRuleVoidPolicyRequest;
import pos.pos.settings.dto.UpdateOrderRuleWorkflowRequest;
import pos.pos.settings.service.SettingsDetailService;

import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/settings/order-rules")
@RequiredArgsConstructor
public class SettingsOrderRuleController {

    private final SettingsDetailService settingsDetailService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get order rule settings")
    public ResponseEntity<OrderRuleSettingsResponse> getOrderRules(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getOrderRules(authentication, restaurantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace order rule settings")
    public ResponseEntity<OrderRuleSettingsResponse> updateOrderRules(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateOrderRuleSettingsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateOrderRules(authentication, restaurantId, request));
    }

    @PatchMapping("/void-policy")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update order void policy")
    public ResponseEntity<OrderRuleSettingsResponse> updateVoidPolicy(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateOrderRuleVoidPolicyRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateVoidPolicy(authentication, restaurantId, request));
    }

    @PatchMapping("/discount-policy")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update order discount policy")
    public ResponseEntity<OrderRuleSettingsResponse> updateDiscountPolicy(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateOrderRuleDiscountPolicyRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateDiscountPolicy(authentication, restaurantId, request));
    }

    @PatchMapping("/workflow")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Update order workflow settings")
    public ResponseEntity<OrderRuleSettingsResponse> updateWorkflow(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateOrderRuleWorkflowRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateWorkflow(authentication, restaurantId, request));
    }
}
