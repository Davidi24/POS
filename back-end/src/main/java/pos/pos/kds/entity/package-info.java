/**
 * CURRENT RELATION: kds_stations.branch_id -> branches.id
 * CURRENT RELATION: kds_stations.device_id -> devices.id
 * CURRENT RELATION: kds_station_routings.menu_item_id -> menu-items.id
 * CURRENT RELATION: kds_tickets.order_id -> orders.id
 * CURRENT RELATION: kds_ticket_items.order_line_item_id -> order_line_items.id
 *
 * FUTURE RELATION: printer chits and expo display metrics should attach to kds_tickets.
 */
package pos.pos.kds.entity;
