package pos.pos.security.rbac;

// Permission code is the security source of truth, e.g. USERS_CREATE.
// Display fields are metadata used for seeding and admin-facing descriptions.
public enum AppPermission {

    // Laid out as the app is (user, 2026-11-19: "a card for each, Tables, Orders, Menu… and all nav bars with chosen"): each
    // workspace's "Open …", then each place in its bar with its own "See …", then what changes things there. PermissionTree
    // says what needs what and where each one is shown.

    // POS → Tables (user, 2026-11-20: edit the plan, take payments, change tables, and which floors they see)
    TABLES_READ("See tables", "The floor plan, on the floors chosen for them"),
    TABLES_UPDATE("Change tables", "Seat guests, set tables free or out of use, move guests and join tables"),
    TABLES_PAYMENT("Take payments", "Request the bill and take the payment from a table"),
    TABLES_LAYOUT("Edit the floor plan", "Add, move and remove tables, floors and places"),
    // While the branch is closed nobody seats guests or starts orders (user, 2026-11-20); this opens it for now.
    OPEN_OUTSIDE_HOURS("Open outside the hours", "Open the branch for now while it's closed: a late group, an event"),
    // POS → Orders (user, 2026-11-20)
    ORDER_READ("See orders", "The open orders and what's on them"),
    ORDER_UPDATE("Edit orders", "Start orders, add and remove dishes, notes, send to the kitchen, serve, cancel unpaid-for orders"),
    // Come with "Edit orders" (PermissionTree bundles): never shown on their own.
    ORDER_CREATE("Start orders", "Start an order on a table or without one"),
    ORDER_CANCEL("Cancel orders", "Cancel an order nothing was paid on"),
    ORDER_CLOSE("Take payments", "Request the bill and take the payment for an order"),
    ORDER_CLOSE_UNPAID("Close as unpaid", "Close an order whose guests left without paying"),
    ORDER_VOID("Void what was sent", "Void orders or dishes the kitchen already has"),
    ORDER_TRANSFER("Move orders to another table", "Move an order to another table"),
    ORDER_DISCOUNT_APPLY("Give discounts", "Take money off a dish or a whole order"),
    ORDER_KITCHEN_READ("See an order's kitchen status", "The food status of an order and of its dishes"),
    // POS → Reservations: seeing only looks; every change has its own (user, 2026-11-20)
    RESERVATION_READ("See reservations", "Bookings, the overview and free tables for walk-ins"),
    RESERVATION_MANAGE("Make and change bookings", "Book, change time, guests, notes and table, confirm attendance, check in and seat"),
    RESERVATION_CANCEL("Cancel bookings", "Cancel a booking or mark a no-show"),
    RESERVATION_APPROVE("Approve bookings", "Accept or decline booking requests and move bookings to other tables"),
    RESERVATION_CORRECT("Fix past bookings", "Correct bookings after the time limits or from an earlier day, with a reason"),
    PAYMENT_GOODWILL_REFUND("Give goodwill refunds", "Give back part of money the restaurant kept, like a late cancel's deposit"),
    // POS → Menu: seeing only looks (user, 2026-11-20)
    MENUS_READ("See the menu", "Menus, sections, dishes and their prices"),
    MENUS_SOLD_OUT("Mark sold out", "Mark a dish or an option sold out and available again"),
    MENUS_CREATE("Add dishes and menus", "Add menus, sections, dishes, variants and options"),
    MENUS_UPDATE("Change dishes and menus", "Change dishes, photos, ingredients, the order and what waiters see, not prices"),
    MENUS_PRICES("Change prices", "Change the price of dishes, variants and options"),
    MENUS_DELETE("Delete dishes and menus", "Delete menus, sections, dishes, variants and options"),
    // POS → Kitchen Status, My Sales, History (Kitchen Status is the whole board; an order's own is in Orders, user 2026-11-20)
    KITCHEN_STATUS_READ("See Kitchen Status", "The whole kitchen board: where every order is"),
    SALES_SELF("See My Sales", "Their own sales and tips"),
    ORDER_HISTORY_READ("See history", "Closed, cancelled and unpaid orders, their bills, printed again"),
    ORDER_REOPEN_PAYMENT("Reopen for payment", "Reopen an unpaid order to take its payment"),
    ORDER_REOPEN("Reopen orders", "Reopen a closed order"),
    PAYMENT_REFUND("Refund payments", "Give money back on a paid order, with a reason"),
    ORDER_AUDIT("See what changed on orders", "Every change made to an order, by whom and when"),

    // My shift, in the POS and the kitchen display (user, 2026-11-20: pay and the team each their own)
    SHIFT_SELF("My shift", "Their own shifts: clock in and out, take breaks"),
    SHIFT_PAY_SELF("See their pay", "Their hours, wage or salary and tips"),
    SHIFT_TEAM("See others' shifts", "Who works when, for the roles chosen for them"),

    // Kitchen display → Tickets, Upcoming, Menu, History
    KDS_READ("See tickets", "The tickets on the kitchen board"),
    KDS_UPDATE("Move tickets", "Start dishes, mark them ready and send tickets back"),
    KDS_UPCOMING_READ("See Upcoming", "Food ordered ahead for bookings and timed orders"),
    KDS_MENU_READ("See the menu", "The menu, as the POS shows it"),
    KDS_HISTORY_READ("See history", "Tickets that went out"),

    // Inventory → Overview, Stock, Items, Counts, History, Suppliers
    INVENTORY_READ("See the overview", "Stock health, value and the stock in and out chart"),
    INVENTORY_STOCK_READ("See stock", "What's in stock, where, and what's running low"),
    INVENTORY_RECEIVE("Receive deliveries", "Put deliveries in stock"),
    INVENTORY_WASTE("Record waste", "Take out what was thrown away"),
    INVENTORY_TRANSFER("Move stock", "Move stock from one place to another"),
    INVENTORY_ADJUST("Correct stock", "Correct amounts and send things back to suppliers"),
    INVENTORY_PLACES("Manage places", "Add, change and delete places and where dishes take stock from"),
    INVENTORY_ITEMS_READ("See items", "Every item and sub-recipe with its cost, supplier and levels"),
    INVENTORY_ITEMS_CREATE("Add items", "Add items and sub-recipes"),
    INVENTORY_ITEMS_UPDATE("Change items", "Change items, their levels, photos and kinds, and sub-recipes"),
    INVENTORY_ITEMS_DELETE("Delete items", "Delete items; what's left in stock is written off"),
    INVENTORY_COUNTS_READ("See counts", "Stock counts and their differences"),
    INVENTORY_COUNT("Count stock", "Start counts, write what was counted and finish them"),
    INVENTORY_COUNTS_APPROVE("Approve counts", "Approve a count, which changes the stock to what was counted"),
    INVENTORY_HISTORY_READ("See history", "Every stock change and what happened to items"),
    SUPPLIERS_READ("See suppliers", "Suppliers, their places, the items bought from them and orders"),
    SUPPLIERS_UPDATE("Change suppliers", "Add and change suppliers, their items and prices, and make orders"),

    // Admin Hub → Shifts, Salary, Users, Roles, Settings, Audit log, Devices, Restaurant info
    SHIFT_READ("See shifts", "Who works when, and who's clocked in"),
    SHIFT_MANAGE("Plan shifts", "Plan shifts, put people on them and fix clock-ins"),
    SALARY_MANAGE("Salary", "See wages and tips, set how people are paid and record payouts"),
    USERS_READ("See people", "The people working here and their roles"),
    USERS_CREATE("Add people", "Add people and choose their roles"),
    USERS_UPDATE("Change people", "Change details, roles, permissions and PINs, and reset passwords"),
    USERS_DELETE("Remove people", "Remove people from the staff"),
    SESSIONS_MANAGE("Sign people out", "See where people are signed in and sign them out"),
    ROLES_READ("See roles", "The roles and what each can do"),
    ROLES_CREATE("Make roles", "Make the restaurant's own roles"),
    ROLES_UPDATE("Change roles", "Rename roles, change their pay and switch them off"),
    ROLES_DELETE("Delete roles", "Delete the restaurant's own roles"),
    ROLES_ASSIGN_PERMISSIONS("Change what roles can do", "Switch a role's permissions on and off"),
    SETTINGS_READ("See settings", "How the restaurant is set up"),
    SETTINGS_UPDATE("Change settings", "Bookings, tables, orders, shifts, inventory and other settings"),
    SETTINGS_MONEY("Change payment and fraud settings", "Tax, service charge, paying, receipts and fraud checks"),
    SETTINGS_AUDIT("See the audit log", "Every change made, by whom and when"),
    DEVICES_MANAGE("Manage devices", "Add, connect and remove tills, tablets, printers and other devices"),
    RESTAURANTS_READ("See restaurant info", "The restaurant's details, branches, logo and currency"),
    RESTAURANTS_UPDATE("Change restaurant info", "Description, phone, email, addresses, logo, colours and receipt texts"),
    HOURS_UPDATE("Change opening hours", "Each branch's week and its special days"),

    // Statistics → Overview, Sales, Finance, Orders, Dishes, Stock, Tables, Team
    REPORTS_READ("See the overview", "Sales, orders and guests at a glance"),
    STATS_SALES_READ("See Sales", "Sales by day, hour, service and floor"),
    STATS_FINANCE_READ("See Finance", "Money in and out, wages, refunds, tips and tax"),
    STATS_ORDERS_READ("See Orders", "How many orders, of what kind, and when"),
    STATS_DISHES_READ("See Dishes", "Every dish sold, its cost and margin"),
    STATS_STOCK_READ("See Stock", "Stock in and out, food cost and waste"),
    STATS_TABLES_READ("See Tables", "Bookings, guests and how tables were used"),
    STATS_TEAM_READ("See Team", "Sales, hours and tips by person"),

    // Fraud Detection
    FRAUD_READ("See alerts", "Flagged discounts, refunds, removals and other risky actions"),
    FRAUD_REVIEW("Review alerts", "Mark flagged actions as checked, dismissed or confirmed"),

    // Which app workspaces a user can open. These only gate entry; what they can do inside comes from the permissions above.
    POS_ACCESS("Open POS", "Open the POS workspace"),
    KDS_ACCESS("Open Kitchen Display", "Open the kitchen display workspace"),
    ADMIN_ACCESS("Open Admin Hub", "Open the admin hub workspace"),
    STATISTICS_ACCESS("Open Statistics", "Open the statistics workspace"),
    INVENTORY_ACCESS("Open Inventory", "Open the inventory workspace"),
    FRAUD_ACCESS("Open Fraud Detection", "Open the fraud detection workspace"),

    // The Super Admin's own (PermissionTree.isPlatformOnly): never shown inside a restaurant.
    RESTAURANTS_CREATE("Create restaurants", "Add restaurants and review applications"),
    RESTAURANTS_DELETE("Delete restaurants", "Remove restaurants"),
    SETTINGS_EXPORT("Export settings", "Export a restaurant's settings"),
    SETTINGS_IMPORT("Import settings", "Import a restaurant's settings"),
    SETTINGS_TEMPLATE_MANAGE("Manage settings templates", "Create, change and delete settings templates"),
    SETTINGS_TEMPLATE_APPLY("Apply settings templates", "Apply settings templates to restaurants");


    private final String displayName;
    private final String description;

    AppPermission(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String description() { return description; }
}
