package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.mapper.RoleMapper;
import pos.pos.security.rbac.RoleHierarchyService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleCatalogService {

    private final RoleHierarchyService roleHierarchyService;

    public List<RoleResponse> getAssignableRoles(Authentication authentication) {
        return roleHierarchyService.getAssignableRoles(authentication)
                .stream()
                .map(RoleMapper::toResponse)
                .toList();
    }
}
