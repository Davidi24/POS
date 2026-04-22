/**
 * FUTURE FK: restaurants.owner_id -> users.id
 * FUTURE FK: restaurants.created_by -> users.id
 * FUTURE FK: restaurants.updated_by -> users.id
 *
 * FUTURE FK: branches.manager_user_id -> users.id
 * FUTURE FK: branches.created_by -> users.id
 * FUTURE FK: branches.updated_by -> users.id
 *
 * FUTURE RELATION: orders.branch_id -> branches.id
 * FUTURE RELATION: orders.created_by -> users.id
 * FUTURE RELATION: orders.restaurant_id should usually be derived from branch, not stored separately unless needed for analytics
 *
 * FUTURE RELATION: tables.branch_id -> branches.id
 *
 * FUTURE RELATION: menu-categories.restaurant_id -> restaurants.id
 * FUTURE RELATION: menu-items.category_id -> menu-categories.id
 * FUTURE RELATION: branch_menu_items.branch_id -> branches.id
 * FUTURE RELATION: branch_menu_items.menu_item_id -> menu-items.id
 *
 * FUTURE RELATION: inventory-locations.branch_id -> branches.id
 * FUTURE RELATION: inventory-items.restaurant_id or branch_id depending on central-vs-branch stock model
 *
 * FUTURE RELATION: payments.order_id -> orders.id
 * FUTURE RELATION: receipts.order_id -> orders.id
 * FUTURE RELATION: shifts.branch_id -> branches.id
 * FUTURE RELATION: shifts.user_id -> users.id
 * CURRENT RELATION: settings.restaurant_id -> restaurants.id
 * CURRENT RELATION: devices.restaurant_id -> restaurants.id
 * CURRENT RELATION: devices.branch_id -> branches.id
 * CURRENT RELATION: menus.restaurant_id -> restaurants.id
 * CURRENT RELATION: option-groups.restaurant_id -> restaurants.id
 */
package pos.pos.restaurant.entity;
