/**
 * CURRENT RELATION: report_definitions.branch_id -> branches.id
 * CURRENT RELATION: report_executions.report_definition_id -> report_definitions.id
 * CURRENT RELATION: report_executions.requested_by -> users.id
 *
 * FUTURE RELATION: generated files and analytics snapshots should resolve through report_executions.
 */
package pos.pos.report.entity;
