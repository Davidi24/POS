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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.settings.dto.SettingsExportResponse;
import pos.pos.settings.dto.SettingsTemplateRequest;
import pos.pos.settings.dto.SettingsTemplateResponse;
import pos.pos.settings.service.SettingsTemplateService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Settings")
@Validated
@RestController
@RequestMapping
@RequiredArgsConstructor
public class SettingsTemplateController {

    private final SettingsTemplateService settingsTemplateService;

    @GetMapping("/settings/templates")
    @PreAuthorize("hasAuthority('SETTINGS_TEMPLATE_MANAGE')")
    @Operation(summary = "List settings templates")
    public ResponseEntity<List<SettingsTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(settingsTemplateService.listTemplates());
    }

    @PostMapping("/settings/templates")
    @PreAuthorize("hasAuthority('SETTINGS_TEMPLATE_MANAGE')")
    @Operation(summary = "Create a settings template")
    public ResponseEntity<SettingsTemplateResponse> createTemplate(
            @Valid @RequestBody SettingsTemplateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settingsTemplateService.createTemplate(authentication, request));
    }

    @GetMapping("/settings/templates/{templateId}")
    @PreAuthorize("hasAuthority('SETTINGS_TEMPLATE_MANAGE')")
    @Operation(summary = "Get a settings template")
    public ResponseEntity<SettingsTemplateResponse> getTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok(settingsTemplateService.getTemplate(templateId));
    }

    @PutMapping("/settings/templates/{templateId}")
    @PreAuthorize("hasAuthority('SETTINGS_TEMPLATE_MANAGE')")
    @Operation(summary = "Replace a settings template")
    public ResponseEntity<SettingsTemplateResponse> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody SettingsTemplateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsTemplateService.updateTemplate(authentication, templateId, request));
    }

    @DeleteMapping("/settings/templates/{templateId}")
    @PreAuthorize("hasAuthority('SETTINGS_TEMPLATE_MANAGE')")
    @Operation(summary = "Delete a settings template")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        settingsTemplateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restaurants/{restaurantId}/settings/apply-template/{templateId}")
    @PreAuthorize("hasAuthority('SETTINGS_TEMPLATE_APPLY')")
    @Operation(summary = "Apply a settings template to a restaurant")
    public ResponseEntity<SettingsExportResponse> applyTemplate(
            @PathVariable UUID restaurantId,
            @PathVariable UUID templateId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(settingsTemplateService.applyTemplate(authentication, restaurantId, templateId));
    }
}
