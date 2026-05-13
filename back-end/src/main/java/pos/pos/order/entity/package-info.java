/**
 * CURRENT RELATION: orders.restaurant_id -> restaurants.id
 * CURRENT RELATION: orders.branch_id -> branches.id
 * CURRENT RELATION: orders.table_id -> tables.id
 * CURRENT RELATION: orders.reservation_id -> reservations.id
 * CURRENT RELATION: orders.customer_id -> customers.id
 * CURRENT RELATION: orders.created_by -> users.id
 * CURRENT RELATION: orders.updated_by -> users.id
 *
 * CURRENT RELATION: order_line_items.order_id -> orders.id
 * CURRENT RELATION: order_line_items.menu_item_id -> menu-items.id
 * CURRENT RELATION: order_line_items.variant_id -> menu-variants.id
 *
 * CURRENT RELATION: order_item_options.order_line_item_id -> order_line_items.id
 * CURRENT RELATION: order_item_options.option_item_id -> option-items.id
 *
 * CURRENT RELATION: order_discounts.order_id -> orders.id
 * CURRENT RELATION: order_discounts.applied_by -> users.id
 *
 * CURRENT RELATION: order_events.order_id -> orders.id
 * CURRENT RELATION: order_events.created_by -> users.id
 *
 * FUTURE RELATION: payments.order_id -> orders.id
 * FUTURE RELATION: kitchen_tickets.order_id -> orders.id
 * FUTURE RELATION: receipts.order_id -> orders.id
 * FUTURE RELATION: order_transfers.source_order_id -> orders.id
 * FUTURE RELATION: split_bills.parent_order_id -> orders.id
 */
package pos.pos.order.entity;
