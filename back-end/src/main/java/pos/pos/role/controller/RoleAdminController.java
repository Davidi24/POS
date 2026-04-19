package pos.pos.role.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.role.dto.CloneRoleRequest;
import pos.pos.role.dto.CreateRoleRequest;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.role.dto.ReplaceRolePermissionsRequest;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.dto.UpdateRoleRequest;
import pos.pos.role.dto.UpdateRoleStatusRequest;
import pos.pos.role.service.RoleAdminService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Roles")
@RestController
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLES_CREATE')")
    @Operation(summary = "Create a custom role")
    public ResponseEntity<RoleResponse> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleAdminService.createRole(authentication, request));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLES_UPDATE')")
    @Operation(summary = "Update a custom role")
    public ResponseEntity<RoleResponse> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleAdminService.updateRole(authentication, roleId, request));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLES_ASSIGN_PERMISSIONS')")
    @Operation(summary = "Replace the permissions assigned to a role")
    public ResponseEntity<List<PermissionResponse>> replaceRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleAdminService.replaceRolePermissions(authentication, roleId, request));
    }

    @PatchMapping("/roles/{roleId}/status")
    @PreAuthorize("hasAuthority('ROLES_UPDATE')")
    @Operation(summary = "Activate or deactivate a custom role")
    public ResponseEntity<RoleResponse> updateRoleStatus(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleStatusRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(roleAdminService.updateRoleStatus(authentication, roleId, request));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLES_DELETE')")
    @Operation(summary = "Soft delete a custom role")
    public ResponseEntity<Void> deleteRole(@PathVariable UUID roleId, Authentication authentication) {
        roleAdminService.deleteRole(authentication, roleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/roles/{roleId}/clone")
    @PreAuthorize("hasAuthority('ROLES_CREATE')")
    @Operation(summary = "Clone an existing role into a custom role")
    public ResponseEntity<RoleResponse> cloneRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody CloneRoleRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleAdminService.cloneRole(authentication, roleId, request));
    }
}
