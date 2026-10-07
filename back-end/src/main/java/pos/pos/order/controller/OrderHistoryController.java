package pos.pos.order.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pos.pos.order.dto.OrderHistoryPage;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.service.OrderHistoryService;
import java.time.OffsetDateTime;
import java.util.UUID;
@RestController
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/orders/history/page")
public class OrderHistoryController {
    private final OrderHistoryService service;
    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public OrderHistoryPage history(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
            @RequestParam(required=false) OffsetDateTime from, @RequestParam(required=false) OffsetDateTime to,
            @RequestParam(required=false) OrderStatus status, @RequestParam(required=false) UUID customerId,
            @RequestParam(required=false) UUID staffId, @RequestParam(required=false) String search,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="40") int size, Authentication authentication) {
        return service.history(authentication, restaurantId, branchId, from, to, status, customerId, staffId, search, page, size);
    }
}
