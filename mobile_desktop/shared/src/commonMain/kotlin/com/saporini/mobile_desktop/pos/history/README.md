# POS History

The existing Orders layout is reused by the POS History navigation entry through a
separate `OrderHistoryScreenModel`. It is read-only, including when the signed-in
user can edit live orders. Its existing toolbar shows past-order statuses, search
and All/Mine; live preparation controls remain with normal Orders.

`GET /restaurants/{restaurant}/branches/{branch}/orders/history/page` requires
`ORDER_READ` and access to the branch. Pages contain at most 100 records (the app
uses 40), ordered by `openedAt DESC, id DESC`. History contains CLOSED, CANCELLED
and VOIDED orders. Optional filters: `from` inclusive / `to` exclusive (opened-at
instants, matching the existing history contract), terminal status, customer ID,
staff/creator ID, and case-insensitive text search. Search is executed before
paging and treats `%` and `_` as literal text. The old unpaged endpoint remains
compatible for existing callers.

The state module owns filters, selected details, paging, errors and refreshes.
Refresh reloads every loaded page before replacing the list; transient failures
retain the existing rows. Scope/filter changes clear incompatible data, late
responses are discarded, and permission failures clear protected data. The
existing list loads near its end and offers a retry on failure. SSE invalidations
and a 30-second reconciliation read operate only while the screen is active.

Tests: `PosHistoryStateTest`, `PosReadApiTest`, backend `OrderHistoryServiceTest`,
and `back-end/scripts/verify_pos_read_modules.py` against a disposable local clone.
