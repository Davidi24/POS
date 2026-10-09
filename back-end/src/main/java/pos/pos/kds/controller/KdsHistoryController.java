package pos.pos.kds.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pos.pos.kds.dto.KdsHistoryResponse;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.kds.service.KdsHistoryService;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/restaurants/{restaurantId}/branches/{branchId}/kds")
@RequiredArgsConstructor
public class KdsHistoryController {
    private final KdsHistoryService service;
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('KDS_READ')")
    public KdsHistoryResponse history(@PathVariable UUID restaurantId, @PathVariable UUID branchId,
            @RequestParam(required = false) UUID stationId, @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) KdsTicketStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        return service.history(authentication, restaurantId, branchId, stationId, deviceId, from, to, status, page, size);
    }
}
