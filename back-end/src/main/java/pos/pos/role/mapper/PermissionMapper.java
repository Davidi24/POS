package pos.pos.role.mapper;

import pos.pos.role.entity.Permission;
import pos.pos.role.dto.PermissionResponse;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.PermissionTree;

public class PermissionMapper {

    public static PermissionResponse toResponse(Permission permission) {
        if (permission == null) return null;
        // The names in AppPermission are the source of truth (startup seeding copies them into the table too).
        AppPermission known = known(permission.getCode());
        PermissionTree.Place place = PermissionTree.placeOf(permission.getCode());

        return PermissionResponse.builder()
                .id(permission.getId())
                .code(permission.getCode())
                .name(known != null ? known.displayName() : permission.getName())
                .description(known != null ? known.description() : permission.getDescription())
                .parentCode(PermissionTree.parentOf(permission.getCode()))
                .careful(PermissionTree.isCareful(permission.getCode()))
                .workspace(place == null ? null : place.workspace())
                .part(place == null ? null : place.part())
                .position(place == null ? null : place.position())
                .platformOnly(PermissionTree.isPlatformOnly(permission.getCode()))
                .bundledInto(PermissionTree.bundledInto(permission.getCode()))
                .scope(PermissionTree.scopeOf(permission.getCode()))
                .build();
    }

    private static AppPermission known(String code) {
        if (code == null) return null;
        try {
            return AppPermission.valueOf(code);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

}
