package pos.pos.role.mapper;

import pos.pos.role.entity.Role;
import pos.pos.role.dto.RoleResponse;

public class RoleMapper {

    public static RoleResponse toResponse(Role role) {
        if (role == null) return null;

        return RoleResponse.builder()
                .id(role.getId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .rank(role.getRank())
                .isSystem(role.isSystem())
                .isActive(role.isActive())
                .isAssignable(role.isAssignable())
                .isProtected(role.isProtectedRole())
                .build();
    }

}
