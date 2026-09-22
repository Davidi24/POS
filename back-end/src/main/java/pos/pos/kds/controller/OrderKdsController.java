package pos.pos.kds.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.kds.dto.KdsTicketResponse;
import pos.pos.kds.service.KdsTicketQueryService;
import pos.pos.kds.service.KdsTicketWorkflowService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Order KDS")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/orders/{orderId}/kds")
@RequiredArgsConstructor
public class OrderKdsController {

    private final KdsTicketQueryService kdsTicketQueryService;
    private final KdsTicketWorkflowService kdsTicketWorkflowService;

    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('KDS_READ')")
    @Operation(summary = "List KDS tickets for one order")
    public ResponseEntity<List<KdsTicketResponse>> getOrderTickets(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(kdsTicketQueryService.getOrderTickets(authentication, restaurantId, orderId));
    }

    @PostMapping("/tickets/sync")
    @PreAuthorize("hasAuthority('KDS_UPDATE')")
    @Operation(summary = "Sync one order into KDS tickets")
    public ResponseEntity<List<KdsTicketResponse>> syncOrderTickets(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(kdsTicketWorkflowService.syncOrderTickets(authentication, restaurantId, orderId));
    }
}
