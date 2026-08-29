/**
 * CURRENT RELATION: inventory_items.restaurant_id -> restaurants.id
 * CURRENT RELATION: inventory_locations.restaurant_id -> restaurants.id
 * CURRENT RELATION: inventory_locations.branch_id -> branches.id
 * CURRENT RELATION: inventory_levels.location_id -> inventory_locations.id
 * CURRENT RELATION: inventory_levels.inventory_item_id -> inventory_items.id
 * CURRENT RELATION: inventory_movements.order_line_item_id -> order_line_items.id
 * CURRENT RELATION: inventory_counts.location_id -> inventory_locations.id
 * CURRENT RELATION: inventory_count_lines.inventory_item_id -> inventory_items.id
 *
 * FUTURE RELATION: purchase_orders and vendor catalogs should resolve through inventory_items.
 * FUTURE RELATION: automated stock decrements should be emitted from order fulfillment and waste workflows.
 */
package pos.pos.inventory.entity;


