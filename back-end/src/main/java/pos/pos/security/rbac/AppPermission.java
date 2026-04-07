package pos.pos.security.rbac;

// Permission code is the security source of truth, e.g. USERS_CREATE.
// Display fields are metadata used for seeding and admin-facing descriptions.
public enum AppPermission {

    USERS_CREATE("Create Users", "Create new user accounts"),
    USERS_READ("View Users", "View user accounts"),
    USERS_UPDATE("Update Users", "Update user accounts"),
    USERS_DELETE("Delete Users", "Delete user accounts"),

    ROLES_READ("View Roles", "View available roles"),

    SESSIONS_MANAGE("Manage Sessions", "View and revoke sessions for any user");

    private final String displayName;
    private final String description;

    AppPermission(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
