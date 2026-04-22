package pos.pos.security.rbac;

import java.util.EnumSet;
import java.util.Set;

import static pos.pos.security.rbac.AppPermission.*;

public enum AppRole {

    // protected means you can not manage it (except by Super Admin)
    // This role cannot be assigned at all (except by Super Admin)

    SUPER_ADMIN(
            "Super Admin",
            "System-level control across all tenants",
            false,
            true,
            EnumSet.allOf(AppPermission.class)
    ),

    OWNER(
            "Owner",
            "Business owner - full control over the restaurant",
            false,
            true,
            EnumSet.allOf(AppPermission.class)
    ),

    CO_OWNER(
            "Co-Owner",
            "Shares ownership with limited restrictions",
            true,
            false,
            EnumSet.of(
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    MENUS_READ, MENUS_CREATE, MENUS_UPDATE, MENUS_DELETE,
                    ROLES_READ, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE, ROLES_ASSIGN_PERMISSIONS,
                    SESSIONS_MANAGE
            )
    ),

    ADMIN(
            "Admin",
            "Store administrator - manages staff, inventory, settings and reports",
            false,
            false,
            EnumSet.of(
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    MENUS_READ, MENUS_CREATE, MENUS_UPDATE, MENUS_DELETE,
                    ROLES_READ, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE, ROLES_ASSIGN_PERMISSIONS,
                    SESSIONS_MANAGE
            )
    ),

    MANAGER(
            "Manager",
            "Store manager - oversees operations and staff",
            true,
            false,
            EnumSet.of(
                    USERS_CREATE, USERS_READ, USERS_UPDATE,
                    MENUS_READ, MENUS_CREATE, MENUS_UPDATE, MENUS_DELETE,
                    ROLES_READ
            )
    ),

    WAITER(
            "Waiter",
            "Handles orders and customer service",
            true,
            false,
            EnumSet.noneOf(AppPermission.class)
    );

    private static final long RANK_STEP = 10_000L;

    private final String displayName;
    private final String description;
    private final boolean assignable;
    private final boolean protectedRole;
    private final Set<AppPermission> permissions;

    AppRole(
            String displayName,
            String description,
            boolean assignable,
            boolean protectedRole,
            Set<AppPermission> permissions
    ) {
        this.displayName = displayName;
        this.description = description;
        this.assignable = assignable;
        this.protectedRole = protectedRole;
        this.permissions = permissions;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public long rank() { return (values().length - ordinal()) * RANK_STEP; }
    public boolean assignable() { return assignable; }
    public boolean protectedRole() { return protectedRole; }
    public Set<AppPermission> permissions() { return permissions; }

    public static AppRole fromCode(String code) {
        for (AppRole role : values()) {
            if (role.name().equals(code)) {
                return role;
            }
        }
        return null;
    }
}
