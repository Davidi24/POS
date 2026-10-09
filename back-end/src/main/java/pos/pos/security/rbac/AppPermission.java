package pos.pos.security.rbac;

// Permission code is the security source of truth, e.g. USERS_CREATE.
// Display fields are metadata used for seeding and admin-facing descriptions.
public enum AppPermission {

    SHIFT_SELF("My Shifts", "View own schedule and clock in, take breaks and clock out"),
    SHIFT_READ("View Shifts", "View branch staff schedules and attendance"),
    SHIFT_MANAGE("Manage Shifts", "Schedule shifts and correct staff attendance with an audit trail"),

    USERS_CREATE("Create Users", "Create new user accounts"),
    USERS_READ("View Users", "View user accounts"),
    USERS_UPDATE("Update Users", "Update user accounts"),
    USERS_DELETE("Delete Users", "Delete user accounts"),

    RESTAURANTS_CREATE("Create Restaurants", "Create restaurant records"),
    RESTAURANTS_READ("View Restaurants", "View restaurant records"),
    RESTAURANTS_UPDATE("Update Restaurants", "Update restaurant records"),
    RESTAURANTS_DELETE("Delete Restaurants", "Delete restaurant records"),

    MENUS_CREATE("Create Menus", "Create menus, sections, items, variants and option groups"),
    MENUS_READ("View Menus", "View menus, sections, items, variants and option groups"),
    MENUS_UPDATE("Update Menus", "Update menus, sections, items, variants and option groups"),
    MENUS_DELETE("Delete Menus", "Delete menus, sections, items, variants and option groups"),

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

    RESERVATION_READ("View Reservations", "View bookings, requests and the waitlist"),
    RESERVATION_MANAGE("Manage Reservations", "Create and change bookings, check guests in, seat them and cancel"),
    RESERVATION_APPROVE("Approve Bookings", "Accept or decline booking requests, including big groups"),
    RESERVATION_CORRECT("Correct Reservations", "Fix bookings after the staff time limits or from an earlier day, with a reason"),

    PAYMENT_GOODWILL_REFUND("Goodwill Refunds", "Give back part of money the restaurant kept (e.g. a late cancel), with a reason"),
    PAYMENT_REFUND("Refund Payments", "Give money back on a paid order, with a reason"),

    REPORTS_READ("View Statistics", "See sales, staff, payment and menu statistics and download reports"),
    FRAUD_READ("View Fraud Alerts", "See flagged discounts, refunds, removals and other risky actions"),
    FRAUD_REVIEW("Review Fraud Alerts", "Mark flagged actions as checked, dismissed or confirmed"),

    ORDER_READ("View Orders", "View restaurant orders and order activity"),
    ORDER_CREATE("Create Orders", "Create new restaurant orders"),
    ORDER_UPDATE("Update Orders", "Update order headers, items, and notes"),
    ORDER_CLOSE("Close Orders", "Close and settle restaurant orders"),
    ORDER_CANCEL("Cancel Orders", "Cancel restaurant orders"),
    ORDER_VOID("Void Orders", "Void restaurant orders or items"),
    ORDER_DISCOUNT_APPLY("Apply Order Discounts", "Apply and update order discounts"),
    ORDER_TRANSFER("Transfer Orders", "Transfer or move restaurant orders"),
    ORDER_REOPEN("Reopen Orders", "Reopen previously closed restaurant orders"),
    ORDER_AUDIT("Audit Orders", "View order audit trails and operational history"),

    KDS_READ("View Kitchen Display", "View kitchen display stations, boards, and tickets"),
    KDS_UPDATE("Update Kitchen Display", "Update kitchen display ticket and item workflow state"),

    // Which app workspaces a user can open. These only gate entry; what they can do inside comes from the permissions above.
    POS_ACCESS("Open POS", "Open the POS workspace"),
    KDS_ACCESS("Open Kitchen Display", "Open the kitchen display workspace"),
    ADMIN_ACCESS("Open Admin Hub", "Open the admin hub workspace"),
    STATISTICS_ACCESS("Open Statistics", "Open the statistics workspace"),
    FRAUD_ACCESS("Open Fraud Detection", "Open the fraud detection workspace");


    private final String displayName;
    private final String description;

    AppPermission(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
