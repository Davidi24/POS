# My Sales

`MySalesScreenModel` connects the existing My Sales layout to
`GET /restaurants/{restaurant}/branches/{branch}/sales/mine`. It handles the
current branch/staff, date, optional shift and selected currency, loading,
connection failures, permission changes and stale responses. Active screens
refresh after order SSE events and every 30 seconds; inactive screens stop.

## Report contract

- `ORDER_READ` and branch access are required. The default staff member is the
  caller. Reading another staff member additionally requires `ORDER_AUDIT` and
  `SHIFT_READ`; cross-restaurant/branch selections are rejected.
- Staff attribution uses the order's `createdBy`, the existing authoritative
  association. Payments belong to that order creator even when another cashier
  records them. This is not a cashier reconciliation report.
- Sales, orders served, distinct tables, guests, hourly sales and popular items
  use CLOSED orders whose `closedAt` falls inside the selected period.
- A date means the restaurant's local calendar day, including daylight-saving
  changes. A selected shift uses its actual attendance window, including an
  overnight shift. Worked minutes exclude recorded breaks. Shift collections
  additionally require the payment's explicit `shiftId`; missing assignments
  are not guessed.
- Collections include CAPTURED, PARTIALLY_REFUNDED and REFUNDED ledger records.
  Net collected is amount + tip + surcharge - recorded refund. Pending, failed,
  authorized and voided payments are excluded. Split payments never multiply
  order sales. Payment time bounds are inclusive/exclusive like order bounds.
- Recorded tips are gross, before refunds. Refunds are associated with the
  original payment date; the schema cannot reliably allocate a refund between
  tips and principal. No estimated allocation is made.
- Currencies stay separate, including a currency containing only open orders.
  The client never adds currencies or converts money through floating point.
- Closed orders without a captured payment are explicitly counted and explained
  in the screen. Zero payment totals mean no corresponding ledger records,
  not that payment processing has been implemented.
- Top items use line totals before order-wide discounts; tables are grouped by
  their stored floor. Top items/floors and recent payments are bounded to five.

The API computes aggregates in a read-only repeatable-read database transaction.
There are no schema changes, payment-processing actions or invented sample
figures. Shared Kotlin behavior is tested on JVM; physical Android/iOS testing
is separate.

## Verification

Targeted tests: `MySalesStateTest`, `PosReadApiTest`,
`PosReadScreensScreenshotTest`, and backend `MySalesServiceTest`.
`back-end/scripts/verify_pos_read_modules.py` exercises actual SQL and HTTP with
known split/refunded/unsettled payments, multiple currencies, staff/shift/date
boundaries and bounded history. Run it only with a freshly cloned disposable
`pos_codex_todo_verify_*` database and an isolated backend on localhost:18080;
never point the verifier at the working restaurant database. Drop the clone
afterwards; each invocation seeds its own fixture.
