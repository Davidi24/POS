package pos.pos.security.rbac;

import java.util.EnumSet;
import java.util.Set;

import static pos.pos.security.rbac.AppPermission.*;

// Rank controls delegation power.
// Higher rank = more privilege; non-root actors may only assign or manage lower-ranked roles/users.
public enum AppRole {

    SUPER_ADMIN(
            "Super Admin",
            "Full system access",
            100_000L,
            false,
            true,
            EnumSet.allOf(AppPermission.class)
    ),

    ADMIN(
            "Admin",
            "Store administrator - manages staff, inventory, settings and reports",
            70_000L,
            false,
            true,
            EnumSet.of(
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    ROLES_READ,
                    SESSIONS_MANAGE,
                    SALES_CREATE, SALES_READ, SALES_REFUND,
                    INVENTORY_CREATE, INVENTORY_READ, INVENTORY_UPDATE, INVENTORY_DELETE,
                    REPORTS_READ,
                    SETTINGS_READ, SETTINGS_UPDATE,
                    SHIFTS_CREATE, SHIFTS_READ, SHIFTS_CLOSE
            )
    ),

    MANAGER(
            "Manager",
            "Store manager - oversees operations, inventory and staff activity",
            40_000L,
            true,
            false,
            EnumSet.of(
                    USERS_CREATE, USERS_READ, USERS_UPDATE,
                    ROLES_READ,
                    SESSIONS_MANAGE,
                    SALES_CREATE, SALES_READ, SALES_REFUND,
                    INVENTORY_CREATE, INVENTORY_READ, INVENTORY_UPDATE, INVENTORY_DELETE,
                    REPORTS_READ,
                    SHIFTS_CREATE, SHIFTS_READ, SHIFTS_CLOSE
            )
    ),

    CASHIER(
            "Cashier",
            "POS operator - processes sales and manages own shifts",
            10_000L,
            true,
            false,
            EnumSet.of(
                    SALES_CREATE, SALES_READ, SALES_REFUND,
                    INVENTORY_READ,
                    SHIFTS_CREATE, SHIFTS_READ, SHIFTS_CLOSE
            )
    );

    private final String displayName;
    private final String description;
    private final long rank;
    private final boolean assignable;
    private final boolean protectedRole;
    private final Set<AppPermission> permissions;

    AppRole(
            String displayName,
            String description,
            long rank,
            boolean assignable,
            boolean protectedRole,
            Set<AppPermission> permissions
    ) {
        this.displayName = displayName;
        this.description = description;
        this.rank = rank;
        this.assignable = assignable;
        this.protectedRole = protectedRole;
        this.permissions = permissions;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public long rank() { return rank; }
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
