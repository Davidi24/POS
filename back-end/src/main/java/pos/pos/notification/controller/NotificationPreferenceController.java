package pos.pos.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.notification.dto.NotificationPreferenceRequest;
import pos.pos.notification.dto.NotificationPreferenceResponse;
import pos.pos.notification.service.NotificationPreferenceService;

import java.util.List;

@Tag(name = "Notification Preferences")
@Validated
@RestController
@RequestMapping("/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List current user notification preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> getPreferences(Authentication authentication) {
        return ResponseEntity.ok(notificationPreferenceService.getCurrentPreferences(authentication));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Upsert current user notification preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> upsertPreferences(
            @Valid @RequestBody List<NotificationPreferenceRequest> requests,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationPreferenceService.upsertCurrentPreferences(authentication, requests));
    }
}
