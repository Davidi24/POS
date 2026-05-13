/**
 * CURRENT RELATION: payments.order_id -> orders.id
 * CURRENT RELATION: payments.shift_id -> shifts.id
 * CURRENT RELATION: payments.customer_id -> customers.id
 * CURRENT RELATION: payment_transactions.payment_id -> payments.id
 *
 * FUTURE RELATION: settlement batches, chargeback cases, and payout reconciliations should extend payments.
 */
package pos.pos.payment.entity;
