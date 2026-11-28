# Orders

The Orders module now connects a responsive list/detail screen, menu picker, item customization and nonpayment action forms to the backend. Payments remain excluded.

## What to review

1. `domain/model/OrderInputs.kt`: information supplied when creating or changing an order.
2. `ui/OrderOperations.kt`: available order actions, including items/options, kitchen actions, split/merge/transfer, discounts and lifecycle changes.
3. `ui/OrdersUiState.kt` and `ui/OrdersScreenModel.kt`: observable state, selection, refresh, errors and saving behavior.

## File responsibilities

- `data/api`: authenticated HTTP calls matching the current backend contract; no automatic mutation retries.
- `data/dto`: serialization contracts and explicit domain mapping.
- `data/repository`: invokes domain validation and converts transport responses to domain models.
- `domain/model`: order inputs/results, exact decimal values and menu selection validation.
- `domain/validation`: request validation shared by repository operations.
- `domain/repository`: interfaces for orders and read-only menu catalog access.
- `ui`: screen model/state, shared controls, detail panel, catalog composer and action forms.
- `OrdersScreen.kt`: responsive list/detail layout and lifecycle ownership.
- Existing Koin app module: registers API clients, repositories and the screen model factory.

The layer supports order lists/history/detail, creation linked to tables, item and option changes, kitchen progress, discounts, cancellation/void/reopen/close, split previews and splits, merges, table/branch transfers, events, audit, totals, metrics and export. Catalog queries supply menus, sections, available items, variants, actual option choices and branch tables.

Money is transported using OrderDecimal, preserving the backend decimal representation without Double rounding. Prices and totals remain server-owned. Do not calculate authoritative totals in the screen.

## Status and progress

`OrderStatus` controls the order lifecycle (Draft/Open/Closed/Cancelled/Voided). `OrderFulfillmentStatus` contains only Pending, In preparation, Ready, Partially fulfilled and Fulfilled. Payment and individual item statuses are separate and unchanged.

The backend derives fulfillment from active items. Completed delivery orders also use Fulfilled. Cancelling or voiding an order preserves its last service progress internally; the app hides the progress badge and excludes these orders from service-progress filters. Only the lifecycle cancellation/void badge is displayed.

Migration V38 maps legacy Delivered to Fulfilled and legacy Cancelled fulfillment to the neutral Pending default, because previous service progress was overwritten. Original values are retained in `order_fulfillment_legacy`. The client accepts these two legacy names only when decoding saved/replayed responses; it exposes and writes only the five current values.

## Screen integration

OrdersScreen obtains OrdersScreenModel through Koin, collects `state`, and owns its disposal. Call `setActive(true)` only when the screen is visible and the app is foregrounded, and `setActive(false)` on background/navigation. Dispose the model when its owner exits. A branch-scoped authenticated SSE subscription invalidates the list and selected detail after committed changes. It reconnects automatically and reloads on every connection, including after missed events. Subscriptions stop on background/navigation or session changes; there is no periodic list polling.

Use `operations` for typed order queries/mutations and `catalog` for menu/table reads. The app DI supplies both catalog dependencies. Load item choices and use `toLineItemInput` to enforce available variants/options and required/minimum/maximum selections before creating a line. Server-side validation is still required.

Create server drafts explicitly; this is not an offline order queue. Local list search is separate from the server list/history filters. Session/branch/permission changes clear state; late responses from an older session are discarded. The backend remains the permission authority.

Mutations return Result and are serialized per screen model. Disable saving actions while `isSaving`. A confirmed write followed by a failed refresh remains successful, with `refreshWarning`; do not repeat the write. An uncertain write sets `needsReconciliation` and blocks further writes. Refresh and inspect current server data before explicitly calling `acknowledgeReconciliation`. Cancellation propagates to the caller. Do not automatically acknowledge or retry an uncertain operation.

## Historical verification

The results below were recorded during earlier implementation stages. Later desktop dialog and editor refinements were compiled and reloaded without rerunning tests. Some test cases still describe the earlier polling behavior and need review against the current SSE implementation.

The complete desktop shared compilation and Android debug APK build passed with one Gradle worker and a capped heap. Twenty-one Orders JVM tests passed against the built production classes, including decimal/API/validation contracts, polling, session isolation, stale responses, write serialization/reconciliation and four responsive UI runs at 360, 390, 768 and 1440 pixels. The UI runs interact with production composables using fixture data and capture list, details, activity, action menus, discount/create forms, catalog and item customization. They are not screenshots of live restaurant data.

Tests are under `shared/src/jvmTest/kotlin/com/saporini/mobile_desktop/orders`. Standard project task: `bash gradlew :shared:jvmTest --tests 'com.saporini.mobile_desktop.orders.*'` from mobile_desktop. The JVM tests were run with a focused compiler/JUnit harness to limit memory; the complete Android build used Gradle.

Backend regression tests cover server-owned fields, permission checks, price/tax snapshots, option rules, split discount allocation, cancellation consistency and transactional request replay. All 18 focused backend tests passed. Live local API checks passed for concurrent duplicate-create replay, server-managed fields, keyed payload conflicts, protected currency, adding items, reopening details, discounts, split preview/split conservation, merge conservation, kitchen send/ready/fulfill and cancellation cleanup. Temporary orders were cancelled or voided by merge. Remaining configuration is recorded in AGENT_MEMORY.md.

## Backend safeguards and configuration

Order number, currency and opening time are assigned by the server. POS inputs no longer expose them, and updates cannot change them. Validation lives in domain/validation. Variant price adjustments are snapshotted, selected option rules are enforced, and option price adjustments scale with item quantity on new lines. Migration preserves the older once-per-line option pricing on existing lines. Splits apportion saved discounts rather than copying a full fixed discount to both orders. Merges preserve applied discount amounts without extending percentage discounts to the other order. Line-level rounding allocates the entire order discount. Order numbers use a random suffix with a uniqueness check, avoiding repeated timestamp prefixes during rapid creation.

Authenticated order writes are serialized per restaurant with a PostgreSQL transaction advisory lock, plus row locks when changing existing orders. A successful keyed request and its replay response commit together; sending the same key/body again replays the response, while changing the body returns 409. The mobile API supplies a fresh Idempotency-Key for each action. Do not automatically retry an uncertain action using a new key. Replay records are durable and contain order responses; include this table in the database's access and retention policies.

Restaurant billing settings expose orderTaxRate (0–100) and orderTaxInclusive. New orders snapshot the policy; changing settings does not reprice existing orders. The calculation applies a restaurant-wide rate to discounted item totals, rounds each line to two decimals and reports inclusive tax without adding it again. Service charges are calculated separately and are not taxed by this single-rate calculation. Existing orders retain their prior zero-tax policy. No tax rate is assumed or configured for the user's restaurant. Multiple item-specific rates and service-charge tax rules require a matching configuration before use in a restaurant that needs them.

Orders use `/restaurants/{restaurantId}/branches/{branchId}/orders/events` for committed-change SSE invalidations (including KDS changes); the backend emitter is local to this application instance. Multi-instance deployments require a shared event broker. The add-item menu persists real catalog choices through the existing item API, and the header and empty-state Add item controls open it directly. Inventory consumption and payment processing remain separate work.

## Screen behavior

- Desktop: order list with selected-order details alongside it; phone/tablet: cards and a separate details view.
- Create a saved server draft, choose table/takeaway and guests, then add items from menus. Adding an item persists immediately; desktop item and order-information edits persist when Save changes is selected. There is no offline write queue.
- Customer and today's reservation lookups use the backend's existing SETTINGS_READ permission. Staff without it can create walk-in orders; they cannot browse restricted profiles.
- Item customization validates quantity, active variant and option group rules. Already-fired items cannot be structurally edited; use permitted void actions with a reason.
- Activity, history/date/status filters, notes and kitchen actions use server data. The compact details view also exposes discounts, transfers, split preview, merge, cancellation, void and reopen actions according to permission and order state; these additional actions are not yet exposed by the desktop action menu.
- Desktop Print and Payment buttons are visible but are not connected yet. Delivery order creation is not exposed. The backend still enforces restaurant settings and lifecycle constraints.
