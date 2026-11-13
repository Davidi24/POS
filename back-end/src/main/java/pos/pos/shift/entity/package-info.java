/**
 * CURRENT RELATION: shifts.branch_id -> branches.id
 * CURRENT RELATION: shifts.user_id -> users.id
 * CURRENT RELATION: shifts.device_id -> devices.id
 * CURRENT RELATION: shift_breaks.shift_id -> shifts.id
 *
 * FUTURE RELATION: schedules, timecard approvals, and payroll exports should extend shifts.
 */
package pos.pos.shift.entity;
