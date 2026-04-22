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

    ROLES_READ("View Roles", "View available roles"),
    ROLES_CREATE("Create Roles", "Create custom roles"),
    ROLES_UPDATE("Update Roles", "Update custom roles"),
    ROLES_DELETE("Delete Roles", "Delete custom roles"),
    ROLES_ASSIGN_PERMISSIONS("Assign Role Permissions", "Replace permissions assigned to a role"),

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
