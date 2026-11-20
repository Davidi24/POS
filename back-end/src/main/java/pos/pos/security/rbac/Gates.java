package pos.pos.security.rbac;

/**
 * Shared @PreAuthorize expressions. Inventory's places each have their own "See …" (PermissionTree): every inventory
 * place reads the same items, places and levels, so reading takes any of them, and the app shows only the places allowed.
 */
public final class Gates {

    private Gates() {
    }

    public static final String INVENTORY_ANY_READ = "hasAnyAuthority('INVENTORY_READ', 'INVENTORY_STOCK_READ', "
            + "'INVENTORY_ITEMS_READ', 'INVENTORY_COUNTS_READ', 'INVENTORY_HISTORY_READ')";

    /** The kitchen board: any kitchen place, or the POS's orders and Kitchen Status (waiters follow their orders there). */
    public static final String KDS_ANY_READ = "hasAnyAuthority('KDS_READ', 'KDS_UPCOMING_READ', 'KDS_HISTORY_READ', "
            + "'ORDER_READ', 'KITCHEN_STATUS_READ')";

    /** Statistics: every page reads the same figures; the app shows only the pages allowed. */
    public static final String STATISTICS_ANY_READ = "hasAnyAuthority('REPORTS_READ', 'STATS_SALES_READ', 'STATS_FINANCE_READ', "
            + "'STATS_ORDERS_READ', 'STATS_DISHES_READ', 'STATS_STOCK_READ', 'STATS_TABLES_READ', 'STATS_TEAM_READ')";
}
