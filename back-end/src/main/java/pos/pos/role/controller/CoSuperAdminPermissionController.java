package pos.pos.role.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.role.dto.CoSuperAdminPermissionResponse;
import pos.pos.role.dto.ReplaceCoSuperAdminPermissionsRequest;
import pos.pos.role.service.CoSuperAdminPermissionService;

import java.util.List;
import java.util.UUID;

// Co-Super-Admins are added and removed through /users (role CO_SUPER_ADMIN); this is only their permission ticks.
@Tag(name = "Co-Super-Admins")
@RestController
@RequestMapping("/co-super-admins")
@RequiredArgsConstructor
public class CoSuperAdminPermissionController {

    private final CoSuperAdminPermissionService coSuperAdminPermissionService;

    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List every permission a Co-Super-Admin can hold, with the ones ticked for this person")
    public ResponseEntity<List<CoSuperAdminPermissionResponse>> getPermissions(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(coSuperAdminPermissionService.getPermissions(authentication, userId));
    }

    @PutMapping("/{userId}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Set which permissions stay ticked for a Co-Super-Admin")
    public ResponseEntity<List<CoSuperAdminPermissionResponse>> replacePermissions(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplaceCoSuperAdminPermissionsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(coSuperAdminPermissionService.replacePermissions(authentication, userId, request));
    }
}
