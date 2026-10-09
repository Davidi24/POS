/**
 * CURRENT RELATION: pre_orders.restaurant_id -> restaurants.id
 * CURRENT RELATION: pre_orders.reservation_id -> reservations.id
 * CURRENT RELATION: pre_orders.order_id -> orders.id (set once the pre-order is sent to the kitchen)
 * CURRENT RELATION: pre_orders.created_by -> users.id
 * CURRENT RELATION: pre_orders.updated_by -> users.id
 *
 * CURRENT RELATION: pre_order_items.pre_order_id -> pre_orders.id
 * CURRENT RELATION: pre_order_items.menu_item_id -> menu-items.id
 * CURRENT RELATION: pre_order_items.variant_id -> menu-variants.id
 *
 * CURRENT RELATION: pre_order_item_options.pre_order_item_id -> pre_order_items.id
 * CURRENT RELATION: pre_order_item_options.option_item_id -> option-items.id
 *
 * FUTURE RELATION: payments.pre_order_id -> pre_orders.id
 */
package pos.pos.preorder.entity;
