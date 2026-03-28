package pos.pos.security.rbac;

import java.util.EnumSet;
import java.util.Set;

import static pos.pos.security.rbac.AppPermission.*;

public enum AppRole {

    SUPER_ADMIN(
            "Super Admin",
            "Full system access",
            EnumSet.allOf(AppPermission.class)
    ),

    ADMIN(
            "Admin",
            "Store administrator — manages staff, inventory, settings and reports",
            EnumSet.of(
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    ROLES_READ,
                    SALES_CREATE, SALES_READ, SALES_REFUND,
                    INVENTORY_CREATE, INVENTORY_READ, INVENTORY_UPDATE, INVENTORY_DELETE,
                    REPORTS_READ,
                    SETTINGS_READ, SETTINGS_UPDATE,
                    SHIFTS_CREATE, SHIFTS_READ, SHIFTS_CLOSE
            )
    ),

    MANAGER(
            "Manager",
            "Store manager — oversees operations, inventory and staff activity",
            EnumSet.of(
                    USERS_READ,
                    ROLES_READ,
                    SALES_CREATE, SALES_READ, SALES_REFUND,
                    INVENTORY_CREATE, INVENTORY_READ, INVENTORY_UPDATE, INVENTORY_DELETE,
                    REPORTS_READ,
                    SHIFTS_CREATE, SHIFTS_READ, SHIFTS_CLOSE
            )
    ),

    CASHIER(
            "Cashier",
            "POS operator — processes sales and manages own shifts",
            EnumSet.of(
                    SALES_CREATE, SALES_READ, SALES_REFUND,
                    INVENTORY_READ,
                    SHIFTS_CREATE, SHIFTS_READ, SHIFTS_CLOSE
            )
    );

    private final String displayName;
    private final String description;
    private final Set<AppPermission> permissions;

    AppRole(String displayName, String description, Set<AppPermission> permissions) {
        this.displayName = displayName;
        this.description = description;
        this.permissions = permissions;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
    public Set<AppPermission> permissions() { return permissions; }
}