package pos.pos.role.mapper;

import pos.pos.role.entity.Permission;
import pos.pos.role.dto.PermissionResponse;

public class PermissionMapper {

    public static PermissionResponse toResponse(Permission permission) {
        if (permission == null) return null;

        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(permission.getName())
                .description(permission.getDescription())
                .build();
    }

}
