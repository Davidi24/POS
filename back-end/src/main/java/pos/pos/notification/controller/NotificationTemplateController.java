package pos.pos.notification.controller;

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
import pos.pos.notification.dto.NotificationTemplateRequest;
import pos.pos.notification.dto.NotificationTemplateResponse;
import pos.pos.notification.service.NotificationTemplateService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Notification Templates")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/notifications/templates")
@RequiredArgsConstructor
public class NotificationTemplateController {

    private final NotificationTemplateService notificationTemplateService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @Operation(summary = "List notification templates for one restaurant")
    public ResponseEntity<List<NotificationTemplateResponse>> getTemplates(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationTemplateService.getTemplates(authentication, restaurantId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create one notification template")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody NotificationTemplateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationTemplateService.createTemplate(authentication, restaurantId, request));
    }

    @PutMapping("/{templateId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Replace one notification template")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @PathVariable UUID restaurantId,
            @PathVariable UUID templateId,
            @Valid @RequestBody NotificationTemplateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationTemplateService.updateTemplate(authentication, restaurantId, templateId, request));
    }

    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Delete one notification template")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable UUID restaurantId,
            @PathVariable UUID templateId,
            Authentication authentication
    ) {
        notificationTemplateService.deleteTemplate(authentication, restaurantId, templateId);
        return ResponseEntity.noContent().build();
    }
}
