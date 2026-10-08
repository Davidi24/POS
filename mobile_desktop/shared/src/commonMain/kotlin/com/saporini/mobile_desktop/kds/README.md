# KDS state and backend integration

This package supplies the KDS state and actions. The screens are in `ui/`: `KdsContent.kt` picks the tab (Tickets board, Upcoming with booking pre-orders, Menu availability, History) and `StationsDialog.kt` edits stations. `KdsScreen.kt` only hosts them.

## Ownership and lifecycle

`KdsScreenModel.state` is the shared mobile/desktop `StateFlow`. Koin supplies the existing authenticated HTTP client and session. The existing screen forwards tab changes and lifecycle start/stop; disposal cancels outstanding work. User, restaurant, branch or permission changes reset state, subscriptions and selections. Late responses from a prior session cannot populate the new session.

`setActive` starts a branch-scoped event stream plus a 30-second transport reconciliation, and stops them in the background. `connected` and `orders-changed` refresh the current data; reconnect always reloads it. Periodic refresh covers station/menu changes that do not emit order events. This interval is connection housekeeping, not a restaurant policy.

## Data and actions

- `refresh`, `selectStation`, `selectDevice`: active station boards or one device-bound display. Loading, empty/setup, last refresh, stale data, reconnect and errors are explicit state.
- `setFilter`: ticket search, status, priority and course. `visibleTickets` orders active work by priority and arrival; `upcomingTickets` selects held/scheduled tickets. Already-fired food remains visible even when its due time is in the future.
- `allDay`: remaining quantities by dish, variant, modifiers, notes and option quantity interpretation. Distinct preparation instructions are never combined.
- `selectTicket`, `closeTicket`: ticket detail plus related tickets for the same order across stations.
- `perform`: ticket-level or item-level fire, start, ready and complete. Forward-state guards prevent completing a mixed ticket before its remaining items are ready. Busy keys suppress repeated taps on the same ticket.
- `syncOrder`: explicit synchronization through the existing order-to-KDS route.
- `setHistoryFilter`, `loadMoreHistory`: completed/cancelled history with exclusive end date, optional station/device/status and bounded server pagination. Refresh reloads every page already loaded and removes duplicate IDs.
- `setMenuFilter`, `setAvailability`: active kitchen dishes from every menu page, using the existing menu repository and availability endpoint.
- `loadStationSettings`, `saveStation`: station type, order, device binding, active/scheduled flags and item routing, preserving editable fields when updating.

## Permissions and failures

KDS reads require `KDS_READ`; actions require `KDS_UPDATE`. Menu reads/writes require `MENUS_READ`/`MENUS_UPDATE`; station configuration uses `SETTINGS_READ`/`SETTINGS_UPDATE`. Workspace access continues to use the existing `KDS_ACCESS` navigation gate. No roles are broadened. In particular, a kitchen account without `MENUS_UPDATE` can view but cannot change availability.

Reads and writes are serialized. Failed reads retain the last data and mark it stale. If a write's outcome is uncertain, it is **not retried automatically**: `needsReconciliation`, `reconciliationKey` and `actionError` require a refresh and staff review. After reviewing fresh data, the later UI may call `acknowledgeReconciliation`. A menu/station failure must refresh that resource before acknowledgement. The backend remains the final authority on permissions and transitions.

## Backend contract

`KdsApi` reuses all existing board/display/ticket/order/action/station/device endpoints. Added endpoints:

- `GET /restaurants/{restaurantId}/branches/{branchId}/kds/events`: existing committed order/KDS invalidations, authorized with `KDS_READ`.
- `GET /restaurants/{restaurantId}/branches/{branchId}/kds/history`: `from`, `to`, `status`, `stationId`, `deviceId`, `page`, `size` (1–100). Returns items and page metadata. Includes inactive stations' historical work. Dates describe completion/cancellation, not order creation. Pagination does not fetch-join item collections.

Ticket item responses now include `variantNameSnapshot`, `optionsPerUnit` and `modifiers` (name, quantity, notes). No schema migration is required.

## Scope and verification

Upcoming state covers actual KDS tickets already supplied by the backend. Reservation pre-orders not yet dispatched to KDS remain owned by the existing reservation/pre-order module. Recall/undo cooking is not offered because the existing backend has no such action. The existing in-process event notifier is retained; multi-server broadcasts would require shared event transport.

Checks: `./gradlew :shared:jvmTest --tests '*kds.*' :desktopApp:compileKotlin --max-workers=2` from `mobile_desktop`; `./mvnw -o test -Dtest='Kds*Test' -DfailIfNoTests=false` from `back-end`. Tests cover state/session races, duplicate/uncertain writes, permissions, loaded-page refresh, modifiers, HTTP contracts and streaming through a real local HTTP connection. A disposable PostgreSQL clone was also used to verify the order → station → preparing → ready → completed → history flow, menu availability and live invalidation without modifying real restaurant data.
