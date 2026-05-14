package pos.pos.kds.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.kds.dto.KdsActionRequest;
import pos.pos.kds.dto.KdsStationBoardResponse;
import pos.pos.kds.dto.KdsTicketResponse;
import pos.pos.kds.service.KdsTicketQueryService;
import pos.pos.kds.service.KdsTicketWorkflowService;

import java.util.List;
import java.util.UUID;

@Tag(name = "KDS Board")
@Validated
@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/kds")
@RequiredArgsConstructor
public class KdsBoardController {

    private final KdsTicketQueryService kdsTicketQueryService;
    private final KdsTicketWorkflowService kdsTicketWorkflowService;

    @GetMapping("/board")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get the KDS board for one branch")
    public ResponseEntity<List<KdsStationBoardResponse>> getBoard(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(defaultValue = "false") boolean includeCompleted,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketQueryService.getBoard(authentication, restaurantId, branchId, stationId, deviceId, includeCompleted)
        );
    }

    @GetMapping("/display")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get the KDS display board for one device")
    public ResponseEntity<KdsStationBoardResponse> getDisplay(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @RequestParam UUID deviceId,
            @RequestParam(defaultValue = "false") boolean includeCompleted,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketQueryService.getDisplay(authentication, restaurantId, branchId, deviceId, includeCompleted)
        );
    }

    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "Get one KDS ticket")
    public ResponseEntity<KdsTicketResponse> getTicket(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(kdsTicketQueryService.getTicket(authentication, restaurantId, branchId, ticketId));
    }

    @PostMapping("/tickets/{ticketId}/fire")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Fire one KDS ticket")
    public ResponseEntity<KdsTicketResponse> fireTicket(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.fireTicket(authentication, restaurantId, branchId, ticketId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/start")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Start one KDS ticket")
    public ResponseEntity<KdsTicketResponse> startTicket(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.startTicket(authentication, restaurantId, branchId, ticketId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/ready")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Mark one KDS ticket ready")
    public ResponseEntity<KdsTicketResponse> readyTicket(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.readyTicket(authentication, restaurantId, branchId, ticketId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/complete")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Complete one KDS ticket")
    public ResponseEntity<KdsTicketResponse> completeTicket(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.completeTicket(authentication, restaurantId, branchId, ticketId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/items/{ticketItemId}/fire")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Fire one KDS ticket item")
    public ResponseEntity<KdsTicketResponse> fireTicketItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @PathVariable UUID ticketItemId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.fireTicketItem(authentication, restaurantId, branchId, ticketId, ticketItemId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/items/{ticketItemId}/start")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Start one KDS ticket item")
    public ResponseEntity<KdsTicketResponse> startTicketItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @PathVariable UUID ticketItemId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.startTicketItem(authentication, restaurantId, branchId, ticketId, ticketItemId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/items/{ticketItemId}/ready")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Mark one KDS ticket item ready")
    public ResponseEntity<KdsTicketResponse> readyTicketItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @PathVariable UUID ticketItemId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.readyTicketItem(authentication, restaurantId, branchId, ticketId, ticketItemId, request)
        );
    }

    @PostMapping("/tickets/{ticketId}/items/{ticketItemId}/complete")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    @Operation(summary = "Complete one KDS ticket item")
    public ResponseEntity<KdsTicketResponse> completeTicketItem(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID ticketId,
            @PathVariable UUID ticketItemId,
            @RequestBody(required = false) KdsActionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                kdsTicketWorkflowService.completeTicketItem(authentication, restaurantId, branchId, ticketId, ticketItemId, request)
        );
    }
}
