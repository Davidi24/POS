package pos.pos.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.service.PinService;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.UUID;

/** A manager resets someone's PIN (user, 2026-11-18): it stops working and the person generates a new one in Profile. */
@Tag(name = "Users")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserPinController {

    private final PinService pinService;
    private final RoleHierarchyService roleHierarchyService;

    @PostMapping("/{userId}/pin/reset")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Reset someone's PIN")
    public ResponseEntity<Void> reset(@PathVariable UUID userId, Authentication authentication) {
        roleHierarchyService.assertCanManageUser(authentication, userId);
        pinService.reset(((AuthenticatedUser) authentication.getPrincipal()).getId(), userId);
        return ResponseEntity.noContent().build();
    }
}
