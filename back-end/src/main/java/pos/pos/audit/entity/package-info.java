/**
 * CURRENT RELATION: audit_logs.restaurant_id -> restaurants.id
 * CURRENT RELATION: audit_logs.branch_id -> branches.id
 * CURRENT RELATION: audit_logs.actor_user_id -> users.id
 *
 * FUTURE RELATION: granular before/after JSON diffing and retention policies should extend audit_logs.
 */
package pos.pos.audit.entity;
