package pos.pos.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.inventory.dto.InventorySalesSourceRequest;
import pos.pos.inventory.dto.InventorySalesSourceResponse;
import pos.pos.inventory.service.InventorySalesSourceService;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/restaurants/{restaurantId}/inventory/sale-sources")
public class InventorySalesSourceController {

    private final InventorySalesSourceService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    public ResponseEntity<List<InventorySalesSourceResponse>> list(
            @PathVariable UUID restaurantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.list(authentication, restaurantId));
    }

    @PutMapping("/{branchId}/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ResponseEntity<InventorySalesSourceResponse> set(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID itemId,
            @Valid @RequestBody InventorySalesSourceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.set(authentication, restaurantId, branchId, itemId, request));
    }

    @DeleteMapping("/{branchId}/{itemId}")
    @PreAuthorize("hasAuthority('SETTINGS_UPDATE')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID restaurantId,
            @PathVariable UUID branchId,
            @PathVariable UUID itemId,
            Authentication authentication
    ) {
        service.delete(authentication, restaurantId, branchId, itemId);
        return ResponseEntity.noContent().build();
    }
}
