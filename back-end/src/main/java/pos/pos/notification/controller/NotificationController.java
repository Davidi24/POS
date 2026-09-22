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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.common.dto.PageResponse;
import pos.pos.notification.dto.CreateNotificationBroadcastRequest;
import pos.pos.notification.dto.NotificationCatalogResponse;
import pos.pos.notification.dto.NotificationResponse;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.service.NotificationCatalogService;
import pos.pos.notification.service.NotificationService;
import pos.pos.notification.service.NotificationStreamService;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Notifications")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationCatalogService notificationCatalogService;
    private final NotificationStreamService notificationStreamService;
    private final RestaurantScopeService restaurantScopeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List notification feed items for one restaurant")
    public ResponseEntity<PageResponse<NotificationResponse>> getFeed(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationTopic topic,
            @RequestParam(required = false) String eventCode,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "false") boolean personalOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String direction,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.getFeed(
                authentication,
                restaurantId,
                branchId,
                channel,
                topic,
                eventCode,
                unreadOnly,
                personalOnly,
                page,
                size,
                direction
        ));
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get notification topic coverage and TODO catalog")
    public ResponseEntity<NotificationCatalogResponse> getCatalog(@PathVariable UUID restaurantId, Authentication authentication) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return ResponseEntity.ok(notificationCatalogService.getCatalog());
    }

    @GetMapping("/stream")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Open an SSE notification stream for one restaurant")
    public SseEmitter stream(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) List<NotificationTopic> topic,
            @RequestParam(required = false, name = "eventCode") List<String> eventCodes,
            Authentication authentication
    ) {
        return notificationStreamService.subscribe(
                authentication,
                restaurantId,
                branchId,
                topic == null ? Set.of() : Set.copyOf(topic),
                eventCodes == null ? Set.of() : Set.copyOf(eventCodes)
        );
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark one personal notification as read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID restaurantId,
            @PathVariable UUID notificationId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.markRead(authentication, restaurantId, notificationId));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    @Operation(summary = "Create a manual operational notification")
    public ResponseEntity<NotificationResponse> broadcast(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateNotificationBroadcastRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.broadcast(authentication, restaurantId, request));
    }
}
