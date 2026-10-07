# Codex-only TODO

Owner: Codex. These items are assigned by the user to Codex, not Claude.
Status: Completed on 2026-09-28. All three items are implemented and verified with targeted app/backend tests and isolated live API checks.

Scope: State management, business logic, backend connections and relevant tests only. Do not create or redesign screens, change layouts or restyle navigation. Preserve the existing reservation/settings work owned by Claude.

- [x] **POS History:** Finish the separate history state/data module using the existing order-history APIs and models; add bounded server pagination, filtering, selection/details, loading/error/refresh handling and preservation of loaded pages.
- [x] **Kitchen Status in POS:** Connect the POS kitchen-status data/state to the KDS backend/state just completed. Replace reliance on sample orders with real branch/station ticket data, live updates, permissions and connection/error handling, without redesigning the screen.
- [x] **My Sales:** Implement state and backend aggregation for actual waiter sales, tips, orders served and shift totals. Replace hard-coded figures with authoritative data, with staff/branch/date/shift scope and permission checks. Use available transaction records honestly; missing payment data is a dependency, not permission to invent totals or expand into building payment processing.

Only these three screenshot-selected items are queued. Payments, receipts, table ordering, offline ordering and inventory deductions are not part of this deferred request.
