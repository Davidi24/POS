package pos.pos.role.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.service.RoleCatalogService;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleCatalogController {

    private final RoleCatalogService roleCatalogService;

    @GetMapping("/assignable")
    @PreAuthorize("hasAuthority('ROLES_READ')")
    @Operation(summary = "List roles the current user is allowed to assign")
    public ResponseEntity<List<RoleResponse>> getAssignableRoles(Authentication authentication) {
        return ResponseEntity.ok(roleCatalogService.getAssignableRoles(authentication));
    }
}
