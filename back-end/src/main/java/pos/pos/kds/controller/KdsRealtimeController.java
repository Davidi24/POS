package pos.pos.kds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.order.realtime.OrderChangeNotifier;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/kds")
@RequiredArgsConstructor
public class KdsRealtimeController {
    private final OrderChangeNotifier changes;
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('KDS_READ')")
    public SseEmitter events(@PathVariable UUID restaurantId, @PathVariable UUID branchId, Authentication authentication) {
        // KDS workers need not have ORDER_READ. Both workspaces receive committed branch invalidations.
        return changes.subscribe(authentication, restaurantId, branchId);
    }
}
