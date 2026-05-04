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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.settings.dto.ReceiptPreviewResponse;
import pos.pos.settings.dto.ReceiptSettingsResponse;
import pos.pos.settings.dto.ReceiptTestPrintResponse;
import pos.pos.settings.dto.UpdateReceiptSettingsRequest;
import pos.pos.settings.service.SettingsDetailService;

import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/settings/receipt")
@RequiredArgsConstructor
public class SettingsReceiptController {

    private final SettingsDetailService settingsDetailService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Get receipt settings")
    public ResponseEntity<ReceiptSettingsResponse> getReceiptSettings(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.getReceiptSettings(authentication, restaurantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace receipt settings")
    public ResponseEntity<ReceiptSettingsResponse> updateReceiptSettings(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody UpdateReceiptSettingsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.updateReceiptSettings(authentication, restaurantId, request));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "Build a receipt preview from current settings")
    public ResponseEntity<ReceiptPreviewResponse> previewReceipt(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.previewReceipt(authentication, restaurantId));
    }

    @PostMapping("/test-print")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Generate a test print payload for the current receipt settings")
    public ResponseEntity<ReceiptTestPrintResponse> testPrintReceipt(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsDetailService.testPrintReceipt(authentication, restaurantId));
    }
}
