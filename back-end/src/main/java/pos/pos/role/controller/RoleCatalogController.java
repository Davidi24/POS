package pos.pos.role.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.service.RoleCatalogService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Roles")
@RestController
@RequiredArgsConstructor
public class RoleCatalogController {

    private final RoleCatalogService roleCatalogService;

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "List all active roles")
    public ResponseEntity<List<RoleResponse>> getRoles() {
        return ResponseEntity.ok(roleCatalogService.getRoles());
    }

    @GetMapping("/roles/system")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "List all active system roles")
    public ResponseEntity<List<RoleResponse>> getSystemRoles() {
        return ResponseEntity.ok(roleCatalogService.getSystemRoles());
    }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "Get one role by id")
    public ResponseEntity<RoleResponse> getRole(@PathVariable UUID roleId) {
        return ResponseEntity.ok(roleCatalogService.getRole(roleId));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "List all available permissions")
    public ResponseEntity<List<PermissionResponse>> getPermissions() {
        return ResponseEntity.ok(roleCatalogService.getPermissions());
    }

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "List permissions assigned to one role")
    public ResponseEntity<List<PermissionResponse>> getRolePermissions(@PathVariable UUID roleId) {
        return ResponseEntity.ok(roleCatalogService.getRolePermissions(roleId));
    }

    @GetMapping("/roles/assignable")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "List roles the current user is allowed to assign")
    public ResponseEntity<List<RoleResponse>> getAssignableRoles(Authentication authentication) {
        return ResponseEntity.ok(roleCatalogService.getAssignableRoles(authentication));
    }
}
