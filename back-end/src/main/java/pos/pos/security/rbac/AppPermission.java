package pos.pos.security.rbac;

// resource + action = permission
// Example: a waiter can act on the "orders" resource with actions like ORDERS_CREATE or ORDERS_UPDATE
// Each role is assigned a set of these permissions
public enum AppPermission {

    USERS_CREATE("users", "create", "Create Users", "Create new user accounts"),
    USERS_READ("users", "read", "View Users", "View user accounts"),
    USERS_UPDATE("users", "update", "Update Users", "Update user accounts"),
    USERS_DELETE("users", "delete", "Delete Users", "Delete user accounts"),

    ROLES_READ("roles", "read", "View Roles", "View available roles"),

    SESSIONS_MANAGE("sessions", "manage", "Manage Sessions", "View and revoke sessions for any user"),

    SALES_CREATE("sales", "create", "Create Sales", "Create sales transactions"),
    SALES_READ("sales", "read", "View Sales", "View sales transactions"),
    SALES_REFUND("sales", "refund", "Process Refunds", "Process refunds on transactions"),

    INVENTORY_CREATE("inventory", "create", "Add Inventory", "Add new inventory items"),
    INVENTORY_READ("inventory", "read", "View Inventory", "View inventory items"),
    INVENTORY_UPDATE("inventory", "update", "Update Inventory", "Update inventory items"),
    INVENTORY_DELETE("inventory", "delete", "Delete Inventory", "Delete inventory items"),

    REPORTS_READ("reports", "read", "View Reports", "View business reports"),

    SETTINGS_READ("settings", "read", "View Settings", "View system settings"),
    SETTINGS_UPDATE("settings", "update", "Update Settings", "Update system settings"),

    SHIFTS_CREATE("shifts", "create", "Open Shifts", "Open new shifts"),
    SHIFTS_READ("shifts", "read", "View Shifts", "View shift records"),
    SHIFTS_CLOSE("shifts", "close", "Close Shifts", "Close active shifts");

    private final String resource;
    private final String action;
    private final String displayName;
    private final String description;

    AppPermission(String resource, String action, String displayName, String description) {
        this.resource = resource;
        this.action = action;
        this.displayName = displayName;
        this.description = description;
    }

    public String resource() { return resource; }
    public String action() { return action; }
    public String displayName() { return displayName; }
    public String description() { return description; }
}