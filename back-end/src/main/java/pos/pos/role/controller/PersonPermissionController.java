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
import pos.pos.role.dto.PersonPermissionsResponse;
import pos.pos.role.dto.ReplacePersonPermissionsRequest;
import pos.pos.role.service.PersonPermissionService;

import java.util.UUID;

// One person's own permissions on top of (or less than) what their roles give.
@Tag(name = "Users")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class PersonPermissionController {

    private final PersonPermissionService personPermissionService;

    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "What a person's roles give them and what they can actually do")
    public ResponseEntity<PersonPermissionsResponse> getPermissions(@PathVariable UUID userId, Authentication authentication) {
        return ResponseEntity.ok(personPermissionService.getPermissions(authentication, userId));
    }

    @PutMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('USERS_UPDATE')")
    @Operation(summary = "Set everything one person can do; the difference from their roles is kept for them alone")
    public ResponseEntity<PersonPermissionsResponse> replacePermissions(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplacePersonPermissionsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(personPermissionService.replacePermissions(authentication, userId, request));
    }
}
