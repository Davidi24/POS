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

    // Helps the Super Admin run every restaurant. Starts with every permission; the Super Admin switches single
    // permissions off per person (user_permission_denials). Only the Super Admin hands it out or manages its holders,
    // and a Co-Super-Admin never reaches the Super Admin or other Co-Super-Admins.
    // Shown as a support admin since 2026-11-19 (user: "co-super-admins become admins"): takes care of the restaurants the
    // Super Admin gives them (or all of them, users.admin_all_restaurants), and removes owners only when allowed.
    CO_SUPER_ADMIN(
            "Support admin",
            "Takes care of the restaurants the Super Admin gives them",
            55_000L,
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
            // Everything in the restaurant (2026-11-19: and every new permission by part), but goodwill refunds and creating or
            // removing restaurants.
            EnumSet.complementOf(EnumSet.of(RESTAURANTS_CREATE, RESTAURANTS_DELETE, PAYMENT_GOODWILL_REFUND))
    ),

    ADMIN(
            "Admin",
            "Store administrator - manages staff, inventory, settings and reports",
            30_000L,
            true,
            false,
            // Admin Hub (shifts, users, settings without money and fraud, the audit log), Inventory and Statistics (user,
            // 2026-11-19). POS and the kitchen display come from the roles under it, given with it by default; the owner
            // can add the rest (salary, devices, restaurant info, roles, fraud) with a warning. V127 took the rest away.
            EnumSet.of(
                    RESTAURANTS_READ,
                    USERS_CREATE, USERS_READ, USERS_UPDATE, USERS_DELETE,
                    ROLES_READ,
                    SETTINGS_READ, SETTINGS_UPDATE, SETTINGS_AUDIT,
                    SHIFT_READ, SHIFT_MANAGE,
                    HOURS_UPDATE,
                    ADMIN_ACCESS,
                    // Statistics without Finance (wages and money out are the owner's).
                    STATISTICS_ACCESS, REPORTS_READ, STATS_SALES_READ, STATS_ORDERS_READ, STATS_DISHES_READ, STATS_STOCK_READ,
                    STATS_TABLES_READ, STATS_TEAM_READ,
                    INVENTORY_ACCESS, INVENTORY_READ,
                    INVENTORY_STOCK_READ, INVENTORY_RECEIVE, INVENTORY_WASTE, INVENTORY_TRANSFER, INVENTORY_ADJUST, INVENTORY_PLACES,
                    INVENTORY_ITEMS_READ, INVENTORY_ITEMS_CREATE, INVENTORY_ITEMS_UPDATE, INVENTORY_ITEMS_DELETE,
                    INVENTORY_COUNTS_READ, INVENTORY_COUNT, INVENTORY_COUNTS_APPROVE,
                    INVENTORY_HISTORY_READ,
                    SUPPLIERS_READ, SUPPLIERS_UPDATE
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
                    TABLES_READ, TABLES_UPDATE, TABLES_PAYMENT, TABLES_LAYOUT, OPEN_OUTSIDE_HOURS, HOURS_UPDATE,
                    ORDER_READ, ORDER_UPDATE, ORDER_CREATE, ORDER_CANCEL, ORDER_CLOSE, ORDER_CLOSE_UNPAID, ORDER_VOID,
                    ORDER_TRANSFER, ORDER_DISCOUNT_APPLY, KITCHEN_STATUS_READ, ORDER_KITCHEN_READ,
                    RESERVATION_READ, RESERVATION_MANAGE, RESERVATION_CANCEL, RESERVATION_APPROVE, RESERVATION_CORRECT,
                    MENUS_SOLD_OUT, MENUS_PRICES,
                    SALES_SELF, ORDER_HISTORY_READ, ORDER_REOPEN_PAYMENT, ORDER_REOPEN, PAYMENT_REFUND, ORDER_AUDIT,
                    // No Kitchen display permissions: they'd need "Open Kitchen Display" (PermissionTree, V126).
                    SHIFT_SELF, SHIFT_PAY_SELF, SHIFT_TEAM, SHIFT_READ, SHIFT_MANAGE,
                    POS_ACCESS, ADMIN_ACCESS,
                    INVENTORY_ACCESS, INVENTORY_READ, INVENTORY_STOCK_READ, INVENTORY_ITEMS_READ, INVENTORY_COUNTS_READ,
                    INVENTORY_HISTORY_READ, SUPPLIERS_READ
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
                    TABLES_READ, TABLES_UPDATE, TABLES_PAYMENT,
                    ORDER_READ, ORDER_UPDATE, ORDER_CREATE, ORDER_CANCEL, ORDER_CLOSE, ORDER_TRANSFER, ORDER_DISCOUNT_APPLY,
                    // POS → Kitchen Status reads the kitchen with its own permission; no Kitchen display one (user, 2026-10-30).
                    KITCHEN_STATUS_READ, ORDER_KITCHEN_READ,
                    RESERVATION_READ, RESERVATION_MANAGE, RESERVATION_CANCEL,
                    MENUS_READ, MENUS_SOLD_OUT,
                    // Waiters take the money when a guest comes back to pay an unpaid order.
                    SALES_SELF, ORDER_HISTORY_READ, ORDER_REOPEN_PAYMENT,
                    SHIFT_SELF, SHIFT_PAY_SELF, SHIFT_TEAM,
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
                    TABLES_READ, RESERVATION_READ, ORDER_READ, KITCHEN_STATUS_READ, ORDER_KITCHEN_READ, ORDER_HISTORY_READ,
                    KDS_READ, KDS_UPCOMING_READ, KDS_MENU_READ, KDS_HISTORY_READ,
                    POS_ACCESS, KDS_ACCESS, ADMIN_ACCESS,
                    INVENTORY_ACCESS, INVENTORY_READ, INVENTORY_STOCK_READ, INVENTORY_ITEMS_READ, INVENTORY_COUNTS_READ,
                    INVENTORY_HISTORY_READ, SUPPLIERS_READ
            ),
            ADMIN
    ),

    // Anyone ranked above it adds kitchen staff, like waiters (user, 2026-11-19); startup seeding updates the flag.
    KITCHEN(
            "Kitchen",
            "Kitchen staff - views and updates kitchen display tickets",
            5_000L,
            true,
            false,
            // The Kitchen display (user, 2026-10-30: "Kitchen only KDS"), each of its places with its own permission.
            EnumSet.of(
                    KDS_ACCESS, KDS_READ, KDS_UPDATE, KDS_UPCOMING_READ, KDS_MENU_READ, KDS_HISTORY_READ,
                    // Clocks in from the kitchen display's My shift.
                    SHIFT_SELF, SHIFT_PAY_SELF, SHIFT_TEAM,
                    // Inventory's stock and items, to look only (user, 2026-11-19: "kitchen sees stock and items, not the
                    // overview, counts or history"); the owner can let them receive, waste or add items.
                    INVENTORY_ACCESS, INVENTORY_STOCK_READ, INVENTORY_ITEMS_READ
            )
    ),

    // Cleaning staff: they clock in and out from the POS and are paid in Salary's Cleaning team.
    CLEANER(
            "Cleaner",
            "Keeps the restaurant clean - tables, floors, kitchen and toilets",
            4_000L,
            true,
            false,
            EnumSet.of(
                    SHIFT_SELF, SHIFT_PAY_SELF, SHIFT_TEAM,
                    POS_ACCESS
            )
    ),

    // Temporary help for busy nights and events: clock in from the POS, see the menu and orders, paid as Extra workers.
    EXTRA_WORKER(
            "Extra worker",
            "Temporary help for busy nights and events, paid for the hours they come in",
            3_000L,
            true,
            false,
            EnumSet.of(
                    TABLES_READ,
                    MENUS_READ,
                    ORDER_READ,
                    KITCHEN_STATUS_READ, ORDER_KITCHEN_READ,
                    SHIFT_SELF, SHIFT_PAY_SELF, SHIFT_TEAM,
                    POS_ACCESS
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
