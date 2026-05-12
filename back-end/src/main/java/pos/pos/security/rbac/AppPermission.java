package pos.pos.security.rbac;

// Permission code is the security source of truth, e.g. USERS_CREATE.
// Display fields are metadata used for seeding and admin-facing descriptions.
public enum AppPermission {

    USERS_CREATE("Create Users", "Create new user accounts"),
    USERS_READ("View Users", "View user accounts"),
    USERS_UPDATE("Update Users", "Update user accounts"),
    USERS_DELETE("Delete Users", "Delete user accounts"),

    RESTAURANTS_CREATE("Create Restaurants", "Create restaurant records"),
    RESTAURANTS_READ("View Restaurants", "View restaurant records"),
    RESTAURANTS_UPDATE("Update Restaurants", "Update restaurant records"),
    RESTAURANTS_DELETE("Delete Restaurants", "Delete restaurant records"),

    MENUS_READ("View Menus", "View restaurant menus"),
    MENUS_CREATE("Create Menus", "Create restaurant menus"),
    MENUS_UPDATE("Update Menus", "Update restaurant menus"),
    MENUS_DELETE("Delete Menus", "Delete restaurant menus"),

    ROLES_READ("View Roles", "View available roles"),
    ROLES_CREATE("Create Roles", "Create custom roles"),
    ROLES_UPDATE("Update Roles", "Update custom roles"),
    ROLES_DELETE("Delete Roles", "Delete custom roles"),
    ROLES_ASSIGN_PERMISSIONS("Assign Role Permissions", "Replace permissions assigned to a role"),

    SESSIONS_MANAGE("Manage Sessions", "View and revoke sessions for any user"),

    SETTINGS_READ("View Settings", "View restaurant settings"),
    SETTINGS_UPDATE("Update Settings", "Update restaurant settings"),
    SETTINGS_AUDIT("Audit Settings", "View settings history and audit logs"),
    SETTINGS_EXPORT("Export Settings", "Export restaurant settings"),
    SETTINGS_IMPORT("Import Settings", "Import restaurant settings"),
    SETTINGS_TEMPLATE_MANAGE("Manage Settings Templates", "Create, update and delete settings templates"),
    SETTINGS_TEMPLATE_APPLY("Apply Settings Templates", "Apply settings templates to restaurants"),

    ORDER_READ("View Orders", "View restaurant orders and order activity"),
    ORDER_CREATE("Create Orders", "Create new restaurant orders"),
    ORDER_UPDATE("Update Orders", "Update order headers, items, and notes"),
    ORDER_CLOSE("Close Orders", "Close and settle restaurant orders"),
    ORDER_CANCEL("Cancel Orders", "Cancel restaurant orders"),
    ORDER_VOID("Void Orders", "Void restaurant orders or items"),
    ORDER_DISCOUNT_APPLY("Apply Order Discounts", "Apply and update order discounts"),
    ORDER_TRANSFER("Transfer Orders", "Transfer or move restaurant orders"),
    ORDER_REOPEN("Reopen Orders", "Reopen previously closed restaurant orders"),
    ORDER_AUDIT("Audit Orders", "View order audit trails and operational history");


    private final String displayName;
    private final String description;

    AppPermission(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
