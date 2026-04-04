package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleCatalogService {

    private final RoleHierarchyService roleHierarchyService;

    public List<RoleResponse> getAssignableRoles(Authentication authentication) {
        UUID userId = currentUser(authentication).getId();

        return roleHierarchyService.getAssignableRoles(userId)
                .stream()
                .map(RoleMapper::toResponse)
                .toList();
    }

    private User currentUser(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
