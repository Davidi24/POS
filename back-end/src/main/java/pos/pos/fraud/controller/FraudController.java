package pos.pos.fraud.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.fraud.FraudReviewStatus;
import pos.pos.fraud.FraudRule;
import pos.pos.fraud.FraudSeverity;
import pos.pos.fraud.dto.FraudDtos.ActivityPage;
import pos.pos.fraud.dto.FraudDtos.Alert;
import pos.pos.fraud.dto.FraudDtos.AlertPage;
import pos.pos.fraud.dto.FraudDtos.Overview;
import pos.pos.fraud.dto.FraudDtos.ReviewRequest;
import pos.pos.fraud.dto.FraudDtos.Rules;
import pos.pos.fraud.service.FraudService;

import java.time.LocalDate;
import java.util.UUID;

/** The Fraud Detection workspace. Periods are restaurant-local days {@code from}..{@code to}, at most 92 days. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/fraud")
@Tag(name = "Fraud detection", description = "Flagged discounts, refunds, removals and other risky staff actions")
public class FraudController {

    private final FraudService fraudService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('FRAUD_READ')")
    @Operation(summary = "Alert counts per check, riskiest staff, alerts per day and the latest open alerts")
    public ResponseEntity<Overview> overview(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(fraudService.overview(authentication, restaurantId, branchId, from, to));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('FRAUD_READ')")
    @Operation(summary = "Flagged actions, newest first, filtered by check, severity, review status or person")
    public ResponseEntity<AlertPage> alerts(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) FraudRule rule,
            @RequestParam(required = false) FraudSeverity severity,
            @RequestParam(required = false) FraudReviewStatus status,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(fraudService.alerts(authentication, restaurantId, branchId, from, to, rule, severity, status,
                staffId, page, size));
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('FRAUD_READ')")
    @Operation(summary = "Every discount, removal, void, cancel, refund, cancelled payment and reopen, newest first")
    public ResponseEntity<ActivityPage> activity(
            @PathVariable UUID restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(fraudService.activity(authentication, restaurantId, branchId, from, to, type, staffId, page, size));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('FRAUD_READ')")
    @Operation(summary = "The checks, whether each is on and its threshold (changed in Settings → Fraud checks)")
    public ResponseEntity<Rules> rules(@PathVariable UUID restaurantId, Authentication authentication) {
        return ResponseEntity.ok(fraudService.rules(authentication, restaurantId));
    }

    @PutMapping("/alerts/{alertKey}/review")
    @PreAuthorize("hasAuthority('FRAUD_REVIEW')")
    @Operation(summary = "Mark a flagged action as reviewed, dismissed or confirmed (or open again), with a note")
    public ResponseEntity<Alert> review(
            @PathVariable UUID restaurantId,
            @PathVariable String alertKey,
            @Valid @RequestBody ReviewRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(fraudService.review(authentication, restaurantId, alertKey, request));
    }
}
