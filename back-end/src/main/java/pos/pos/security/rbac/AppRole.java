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
            60_000L,
            false,
            true,
            EnumSet.allOf(AppPermission.class)
    ),

    OWNER(
            "Owner",
            "Business owner - full control over the restaurant",
            50_000L,
            false,
            true,
            EnumSet.allOf(AppPermission.class)
    ),

    CO_OWNER(
            "Co-Owner",
            "Shares ownership with limited restrictions",
            40_000L,
            true,
            false,
            EnumSet.of(
                    RESTAURANTS_READ, RESTAURANTS_UPDATE,
                    MENUS_CREATE, MENUS_READ, MENUS_UPDATE, MENUS_DELETE,
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    ROLES_READ, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE, ROLES_ASSIGN_PERMISSIONS,
                    SESSIONS_MANAGE,
                    SETTINGS_READ, SETTINGS_UPDATE, SETTINGS_AUDIT, SETTINGS_EXPORT, SETTINGS_IMPORT,
                    SETTINGS_TEMPLATE_MANAGE, SETTINGS_TEMPLATE_APPLY,
                    RESERVATION_READ, RESERVATION_MANAGE, RESERVATION_APPROVE, RESERVATION_CORRECT,
                    ORDER_READ, ORDER_CREATE, ORDER_UPDATE, ORDER_CLOSE, ORDER_CANCEL,
                    ORDER_VOID, ORDER_DISCOUNT_APPLY, ORDER_TRANSFER, ORDER_REOPEN, ORDER_AUDIT,
                    KDS_READ, KDS_UPDATE,
                    SHIFT_SELF, SHIFT_READ, SHIFT_MANAGE,
                    POS_ACCESS, KDS_ACCESS, ADMIN_ACCESS
            )
    ),

    ADMIN(
            "Admin",
            "Store administrator - manages staff, inventory, settings and reports",
            30_000L,
            true,
            false,
            EnumSet.of(
                    RESTAURANTS_READ, RESTAURANTS_UPDATE,
                    MENUS_CREATE, MENUS_READ, MENUS_UPDATE, MENUS_DELETE,
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    ROLES_READ, ROLES_CREATE, ROLES_UPDATE, ROLES_DELETE, ROLES_ASSIGN_PERMISSIONS,
                    SESSIONS_MANAGE,
                    SETTINGS_READ, SETTINGS_UPDATE, SETTINGS_AUDIT, SETTINGS_EXPORT, SETTINGS_IMPORT,
                    SETTINGS_TEMPLATE_MANAGE, SETTINGS_TEMPLATE_APPLY,
                    RESERVATION_READ, RESERVATION_MANAGE, RESERVATION_APPROVE, RESERVATION_CORRECT,
                    ORDER_READ, ORDER_CREATE, ORDER_UPDATE, ORDER_CLOSE, ORDER_CANCEL,
                    ORDER_VOID, ORDER_DISCOUNT_APPLY, ORDER_TRANSFER, ORDER_REOPEN, ORDER_AUDIT,
                    KDS_READ, KDS_UPDATE,
                    SHIFT_SELF, SHIFT_READ, SHIFT_MANAGE,
                    POS_ACCESS, KDS_ACCESS, ADMIN_ACCESS
            )
    ),

    // Only Owner and Co-Owner hand out or manage Managers, unless the restaurant lets Admins do it too.
    MANAGER(
            "Manager",
            "Store manager - oversees operations and staff",
            20_000L,
            true,
            false,
            EnumSet.of(
                    RESTAURANTS_READ,
                    MENUS_CREATE, MENUS_READ, MENUS_UPDATE,
                    USERS_CREATE, USERS_READ, USERS_UPDATE,
                    ROLES_READ,
                    SETTINGS_READ,
                    RESERVATION_READ, RESERVATION_MANAGE, RESERVATION_APPROVE, RESERVATION_CORRECT,
                    ORDER_READ, ORDER_CREATE, ORDER_UPDATE, ORDER_CLOSE, ORDER_CANCEL,
                    ORDER_VOID, ORDER_DISCOUNT_APPLY, ORDER_TRANSFER, ORDER_REOPEN, ORDER_AUDIT,
                    KDS_READ, KDS_UPDATE,
                    SHIFT_SELF, SHIFT_READ, SHIFT_MANAGE,
                    POS_ACCESS, ADMIN_ACCESS
            ),
            CO_OWNER
    ),

    WAITER(
            "Waiter",
            "Handles orders and customer service",
            10_000L,
            true,
            false,
            EnumSet.of(
                    MENUS_READ,
                    RESERVATION_READ,
                    RESERVATION_MANAGE,
                    ORDER_READ,
                    ORDER_CREATE,
                    ORDER_UPDATE,
                    ORDER_CLOSE,
                    ORDER_DISCOUNT_APPLY,
                    ORDER_TRANSFER,
                    SHIFT_SELF,
                    POS_ACCESS
            )
    ),

    // Read-only everywhere; only Owner, Co-Owner and Admin hand it out.
    VIEWER(
            "Viewer",
            "Read-only access - sees POS, kitchen display and admin hub without changing anything",
            7_500L,
            true,
            false,
            EnumSet.of(
                    RESTAURANTS_READ,
                    MENUS_READ,
                    USERS_READ,
                    ROLES_READ,
                    SETTINGS_READ,
                    RESERVATION_READ,
                    ORDER_READ,
                    KDS_READ,
                    POS_ACCESS, KDS_ACCESS, ADMIN_ACCESS
            ),
            ADMIN
    ),

    KITCHEN(
            "Kitchen",
            "Kitchen staff - views and updates kitchen display tickets",
            5_000L,
            false,
            false,
            EnumSet.of(
                    MENUS_READ,
                    ORDER_READ,
                    KDS_READ,
                    KDS_UPDATE,
                    KDS_ACCESS
            )
    );

    private final String displayName;
    private final String description;
    private final long rank;
    private final boolean assignable;
    private final boolean protectedRole;
    private final Set<AppPermission> permissions;
    // The lowest role allowed to hand this role out or manage people who hold it; null means any higher-ranked role.
    private final AppRole lowestManagingRole;

    AppRole(
            String displayName,
            String description,
            long rank,
            boolean assignable,
            boolean protectedRole,
            Set<AppPermission> permissions
    ) {
        this(displayName, description, rank, assignable, protectedRole, permissions, null);
    }

    AppRole(
            String displayName,
            String description,
            long rank,
            boolean assignable,
            boolean protectedRole,
            Set<AppPermission> permissions,
            AppRole lowestManagingRole
    ) {
        this.displayName = displayName;
        this.description = description;
        this.rank = rank;
        this.assignable = assignable;
        this.protectedRole = protectedRole;
        this.permissions = permissions;
        this.lowestManagingRole = lowestManagingRole;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public long rank() { return rank; }
    public boolean assignable() { return assignable; }
    public boolean protectedRole() { return protectedRole; }
    public Set<AppPermission> permissions() { return permissions; }
    public AppRole lowestManagingRole() { return lowestManagingRole; }

    public static AppRole fromCode(String code) {
        for (AppRole role : values()) {
            if (role.name().equals(code)) {
                return role;
            }
        }
        return null;
    }
}
