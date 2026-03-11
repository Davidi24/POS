package pos.pos.role.mapper;

import pos.pos.role.entity.Role;
import pos.pos.role.dto.RoleResponse;

public class RoleMapper {

    public static RoleResponse toResponse(Role role) {
        if (role == null) return null;

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .build();
    }

}