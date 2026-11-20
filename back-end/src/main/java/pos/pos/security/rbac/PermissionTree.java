package pos.pos.security.rbac;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How the permissions hang together and where each one is shown (user, 2026-11-19: "someone can't have View POS off and
 * edit the POS on"; "a card for each, Tables, Orders, Menu…, and all nav bars with chosen"). Each workspace's "Open …"
 * comes first, then each place of its bar with its own "See …", then what changes things there. A permission whose parent
 * is missing is dropped when permissions are saved, and the app shows it under its parent, in its place's card.
 */
public final class PermissionTree {

    private PermissionTree() {
    }

    /**
     * Where a permission is shown: its workspace (POS, KDS, MY_SHIFT, INVENTORY, ADMIN, STATISTICS, FRAUD, PLATFORM) and
     * place.
     */
    public record Place(String workspace, String part, int position) {
    }

    private static final Map<String, String> PARENT = new HashMap<>();
    private static final Map<String, Place> PLACES = new LinkedHashMap<>();

    /** Adds a place's permissions in order: the first needs [parent], the rest need the first unless given as "CODE<PARENT". */
    private static void part(String workspace, String part, String parent, String... codes) {
        String head = null;
        for (String entry : codes) {
            String code = entry;
            String needs;
            int split = entry.indexOf('<');
            if (split > 0) {
                code = entry.substring(0, split);
                needs = entry.substring(split + 1);
            } else {
                needs = head == null ? parent : head;
            }
            if (needs != null) PARENT.put(code, needs);
            if (head == null) head = code;
            PLACES.put(code, new Place(workspace, part, PLACES.size()));
        }
    }

    static {
        // POS: one place for each part of its bar (user, 2026-11-20; Payments went into Orders and History; an order's own
        // kitchen status is in Orders, the whole board is Kitchen Status).
        part("POS", null, null, "POS_ACCESS");
        part("POS", "Tables", "POS_ACCESS", "TABLES_READ", "TABLES_UPDATE", "TABLES_PAYMENT", "TABLES_LAYOUT");
        part("POS", "Orders", "POS_ACCESS", "ORDER_READ", "ORDER_UPDATE", "ORDER_CREATE<ORDER_READ", "ORDER_CANCEL<ORDER_READ",
                "ORDER_CLOSE", "ORDER_CLOSE_UNPAID", "ORDER_VOID", "ORDER_TRANSFER", "ORDER_DISCOUNT_APPLY", "ORDER_KITCHEN_READ");
        part("POS", "Reservations", "POS_ACCESS", "RESERVATION_READ", "RESERVATION_MANAGE", "RESERVATION_CANCEL",
                "RESERVATION_APPROVE", "RESERVATION_CORRECT", "PAYMENT_GOODWILL_REFUND");
        part("POS", "Menu", "POS_ACCESS", "MENUS_READ", "MENUS_SOLD_OUT", "MENUS_CREATE", "MENUS_UPDATE", "MENUS_PRICES<MENUS_UPDATE",
                "MENUS_DELETE");
        part("POS", "Kitchen Status", "POS_ACCESS", "KITCHEN_STATUS_READ");
        part("POS", "My Sales", "POS_ACCESS", "SALES_SELF");
        part("POS", "History", "POS_ACCESS", "ORDER_HISTORY_READ", "ORDER_REOPEN_PAYMENT", "ORDER_REOPEN", "PAYMENT_REFUND",
                "ORDER_AUDIT");
        // Kitchen display
        part("KDS", null, null, "KDS_ACCESS");
        part("KDS", "Tickets", "KDS_ACCESS", "KDS_READ", "KDS_UPDATE");
        part("KDS", "Upcoming", "KDS_ACCESS", "KDS_UPCOMING_READ");
        part("KDS", "Menu", "KDS_ACCESS", "KDS_MENU_READ");
        part("KDS", "History", "KDS_ACCESS", "KDS_HISTORY_READ");
        // My shift is everyone's, whatever workspace they work in: the POS and the kitchen display both show it (user,
        // 2026-11-19: "shift is missing"; kitchen staff clock in from the kitchen display).
        part("MY_SHIFT", "My shift", null, "SHIFT_SELF", "SHIFT_PAY_SELF", "SHIFT_TEAM");
        // Inventory: the Inventory page's tabs, then Suppliers.
        part("INVENTORY", null, null, "INVENTORY_ACCESS");
        part("INVENTORY", "Overview", "INVENTORY_ACCESS", "INVENTORY_READ");
        part("INVENTORY", "Stock", "INVENTORY_ACCESS", "INVENTORY_STOCK_READ", "INVENTORY_RECEIVE", "INVENTORY_WASTE",
                "INVENTORY_TRANSFER", "INVENTORY_ADJUST", "INVENTORY_PLACES");
        part("INVENTORY", "Items", "INVENTORY_ACCESS", "INVENTORY_ITEMS_READ", "INVENTORY_ITEMS_CREATE", "INVENTORY_ITEMS_UPDATE",
                "INVENTORY_ITEMS_DELETE");
        part("INVENTORY", "Counts", "INVENTORY_ACCESS", "INVENTORY_COUNTS_READ", "INVENTORY_COUNT", "INVENTORY_COUNTS_APPROVE");
        part("INVENTORY", "History", "INVENTORY_ACCESS", "INVENTORY_HISTORY_READ");
        part("INVENTORY", "Suppliers", "INVENTORY_ACCESS", "SUPPLIERS_READ", "SUPPLIERS_UPDATE");
        // Admin Hub: its bar, with Roles beside Users (they share the Users page).
        part("ADMIN", null, null, "ADMIN_ACCESS");
        part("ADMIN", "Shifts", "ADMIN_ACCESS", "SHIFT_READ", "SHIFT_MANAGE");
        part("ADMIN", "Salary", "ADMIN_ACCESS", "SALARY_MANAGE");
        part("ADMIN", "Users", "ADMIN_ACCESS", "USERS_READ", "USERS_CREATE", "USERS_UPDATE", "USERS_DELETE", "SESSIONS_MANAGE");
        part("ADMIN", "Roles", "ADMIN_ACCESS", "ROLES_READ", "ROLES_CREATE", "ROLES_UPDATE", "ROLES_DELETE", "ROLES_ASSIGN_PERMISSIONS");
        part("ADMIN", "Settings", "ADMIN_ACCESS", "SETTINGS_READ", "SETTINGS_UPDATE", "SETTINGS_MONEY");
        part("ADMIN", "Audit log", "ADMIN_ACCESS", "SETTINGS_AUDIT");
        part("ADMIN", "Devices", "ADMIN_ACCESS", "DEVICES_MANAGE");
        part("ADMIN", "Restaurant info", "ADMIN_ACCESS", "RESTAURANTS_READ", "RESTAURANTS_UPDATE");
        // Statistics: one place for each page.
        part("STATISTICS", null, null, "STATISTICS_ACCESS");
        part("STATISTICS", "Overview", "STATISTICS_ACCESS", "REPORTS_READ");
        part("STATISTICS", "Sales", "STATISTICS_ACCESS", "STATS_SALES_READ");
        part("STATISTICS", "Finance", "STATISTICS_ACCESS", "STATS_FINANCE_READ");
        part("STATISTICS", "Orders", "STATISTICS_ACCESS", "STATS_ORDERS_READ");
        part("STATISTICS", "Dishes", "STATISTICS_ACCESS", "STATS_DISHES_READ");
        part("STATISTICS", "Stock", "STATISTICS_ACCESS", "STATS_STOCK_READ");
        part("STATISTICS", "Tables", "STATISTICS_ACCESS", "STATS_TABLES_READ");
        part("STATISTICS", "Team", "STATISTICS_ACCESS", "STATS_TEAM_READ");
        // Fraud Detection
        part("FRAUD", null, null, "FRAUD_ACCESS");
        part("FRAUD", "Alerts", "FRAUD_ACCESS", "FRAUD_READ", "FRAUD_REVIEW");
        // The Super Admin's own, never shown inside a restaurant.
        part("PLATFORM", "Restaurants", "RESTAURANTS_READ", "RESTAURANTS_CREATE<RESTAURANTS_READ", "RESTAURANTS_DELETE<RESTAURANTS_READ");
        part("PLATFORM", "Settings", "SETTINGS_READ", "SETTINGS_EXPORT<SETTINGS_READ", "SETTINGS_IMPORT<SETTINGS_READ",
                "SETTINGS_TEMPLATE_MANAGE<SETTINGS_READ", "SETTINGS_TEMPLATE_APPLY<SETTINGS_READ");
    }

    /**
     * Permissions that come with another one and are never shown on their own (user, 2026-11-20: starting and cancelling an
     * order come with "Edit orders"). They follow it on save and at sign-in.
     */
    private static final Map<String, String> BUNDLED_INTO = Map.of(
            "ORDER_CREATE", "ORDER_UPDATE",
            "ORDER_CANCEL", "ORDER_UPDATE");

    /** Permissions given for some things only: TABLES_READ for the floors chosen, SHIFT_TEAM for the roles chosen. */
    private static final Map<String, String> SCOPE = Map.of(
            "TABLES_READ", "FLOORS",
            "SHIFT_TEAM", "ROLES");

    public static String bundledInto(String code) {
        return code == null ? null : BUNDLED_INTO.get(code);
    }

    /** FLOORS or ROLES for a permission given for some of them only, else null. */
    public static String scopeOf(String code) {
        return code == null ? null : SCOPE.get(code);
    }

    /** [codes] with each bundled permission present exactly when the one it comes with is. */
    public static Set<String> withBundles(Collection<String> codes) {
        Set<String> out = new LinkedHashSet<>(codes);
        BUNDLED_INTO.forEach((bundled, owner) -> {
            if (out.contains(owner)) out.add(bundled);
            else out.remove(bundled);
        });
        return out;
    }

    /**
     * Owner-level permissions: giving one to someone asks to be careful first (user, 2026-11-19: "when the owner is giving
     * it, it says dangerous, be careful").
     */
    private static final Set<String> CAREFUL = Set.of(
            "FRAUD_ACCESS", "FRAUD_READ", "FRAUD_REVIEW", "SALARY_MANAGE", "DEVICES_MANAGE", "SETTINGS_MONEY",
            "RESTAURANTS_CREATE", "RESTAURANTS_UPDATE", "RESTAURANTS_DELETE", "ROLES_CREATE", "ROLES_UPDATE", "ROLES_DELETE",
            "ROLES_ASSIGN_PERMISSIONS", "SESSIONS_MANAGE", "SETTINGS_IMPORT", "SETTINGS_EXPORT", "SETTINGS_TEMPLATE_MANAGE",
            "SETTINGS_TEMPLATE_APPLY", "PAYMENT_REFUND", "PAYMENT_GOODWILL_REFUND", "ORDER_VOID", "ORDER_REOPEN", "MENUS_PRICES",
            "ORDER_CLOSE_UNPAID",
            "STATS_FINANCE_READ", "INVENTORY_ITEMS_DELETE", "INVENTORY_COUNTS_APPROVE");

    public static boolean isCareful(String code) {
        return code != null && CAREFUL.contains(code);
    }

    /** The Super Admin's own: never shown in a restaurant's permission windows. */
    public static boolean isPlatformOnly(String code) {
        Place place = placeOf(code);
        return place != null && "PLATFORM".equals(place.workspace());
    }

    /** The permission [code] needs, or null for a workspace's "Open …". */
    public static String parentOf(String code) {
        return code == null ? null : PARENT.get(code);
    }

    /** Where [code] is shown, or null when it's unknown. */
    public static Place placeOf(String code) {
        return code == null ? null : PLACES.get(code);
    }

    /** Every permission in the order the app shows them. */
    public static List<String> ordered() {
        return List.copyOf(PLACES.keySet());
    }

    /** [codes] without the ones whose parent (or its parent) is missing, in their order, bundled ones following theirs. */
    public static Set<String> normalize(Collection<String> codes) {
        Set<String> given = withBundles(codes);
        Set<String> kept = new LinkedHashSet<>();
        for (String code : given) {
            if (hasAncestors(code, given)) kept.add(code);
        }
        return kept;
    }

    private static boolean hasAncestors(String code, Set<String> given) {
        String parent = PARENT.get(code);
        int guard = 0;
        while (parent != null && guard++ < 10) {
            if (!given.contains(parent)) return false;
            parent = PARENT.get(parent);
        }
        return true;
    }
}
