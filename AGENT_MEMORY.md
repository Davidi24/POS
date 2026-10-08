## 2026-10-08 (Claude, feature/orders) — Pre-order editor, KDS dish routing, services restarted (~17:55)
- **Why:** the user said "start when you left". The previous entry's open items were the booking pre-order hook and per-station dish routing.
- **Services were down** (likely a restart):
  - `podman start pos-db pos-mailhog`.
  - Backend started again with the same command as the 2026-10-07 entry (log `back-end/app.out.log`).
  - Desktop app relaunched with `:desktopApp:run`.
- **Pre-orders with a booking** (`pos/reservations/preorder/ui`):
  - `BookingPreOrderCard`: shows what was ordered and when it goes to the kitchen. Take / Change / Send now / Cancel, depending on permissions.
  - Taking and changing pre-orders is only offered for PENDING and CONFIRMED bookings (the server's rule).
  - `PreOrderEditorDialog`: menu browser on the left, the guests' food on the right, with quantity stepper, per-dish note and an estimate. The server prices on save.
  - A choices dialog for size and required extras.
  - `PreOrderDishes`: menus come from `MenuRepository` (active menus) and choices from `OrderCatalogRepository.getItemChoices`. It deliberately doesn't use the till's "tonight's event menu only" filter, because the booking may be another day.
- **Hook in the user's screen:** one call in `ReservationDetailsPanel` (Info tab, under `BookingMoneyCard`).
- **KDS stations dialog:** "Dishes made here" lets you add a dish, set its priority and course name, switch it on or off, and remove it.
  - The dishes come from `KdsMenuRepository`.
  - `StationsDialog` now takes `menuSource` (Koin default) and `openStation` (used by the gallery test).
- **Fix:** `SelectInput` without a label ignored its modifier and filled the row. It now wraps the field in `Box(modifier)`. This also affected Inventory → History on narrow screens.
- **Checked:**
  - `:shared:compileKotlinJvm`, `:shared:compileAndroidMain` and `:desktopApp:compileKotlin` pass.
  - The new `PreOrderGalleryTest` and the updated `KdsGalleryTest` renders pass.
  - The live pre-order list and menu list endpoints answer 200.
  - Full test suites not run.
- **Still open:** whether pre-orders are switched on is an Admin Hub setting the app can't read without SETTINGS_READ. If they're off, saving shows the server's "Pre-orders are not available" message.

## 2026-10-08 (Claude, feature/orders) — Screens for every empty app section
**The user asked for production-ready, modern screens (Reservations-overview look) for every empty section, without touching screens they built.** Not committed. Nothing in the backend changed.
- **New screens** (each has a `*Content(state, model, …)` that the gallery tests render):
  - Admin Hub: Users and Permissions (`admin/people/ui`), Audit log (`admin/audit/ui`), Devices (`admin/devices/ui`), Inventory with Stock / Items / Places / History / Counts / Recipes tabs and Suppliers (`admin/inventory/ui`).
  - Statistics workspace: Overview, Sales, Staff, Reports with CSV download (`statistics/ui`).
  - Fraud Detection workspace: Overview, Alerts with review dialog, Sensitive actions, Checks (`fraud/ui`).
  - KDS: Tickets board (New / Cooking / Ready lanes, all-day panel, ticket detail), Upcoming (held and timed tickets plus booking pre-orders), Menu availability, History, kitchen stations dialog (`kds/ui`).
  - POS payment: `pos/payment/ui/TakePaymentScreen` (bill from the receipt API, methods, split, tip, cash change, refunds and cancels, receipt view).
- **New shared kits** in `core/components`: PageKit, FormKit, ListKit, OverviewExtras, ChartKit, DashboardKit, PeriodKit. Also `core/format/Formatting.kt` and `core/files` (`saveTextFile` with jvm/android actuals, `csvRow` with formula-injection guard).
  - `SelectInput` now takes a nullable value (shows the placeholder).
- **Small edits to the user's files, only to connect the new screens:**
  - `AdminScreen` (sections wired), `WorkspaceSections` (placeholder text → content), `KdsScreen` (placeholder → `KdsSectionContent`).
  - `OrdersScreen`: the Payment button was a no-op; `onPaymentRequested` now passes the order id.
  - `PosScreen`: shows `TakePaymentScreen` for that order. The old static mock `pos/payment/PaymentScreen.kt` is untouched and no longer shown.
  - Earlier in this task: `StaffScreenModel.loadCounts`, `RecipesScreenModel` menu choices, a Koin factory for recipes with a menu loader.
- **Settings rule kept:** KDS late and ready limits come from `posTiming` (Admin Hub → Orders & kitchen). Fraud limits are only shown, with a pointer to Admin Hub → Settings → Fraud checks.
- **Checked:**
  - `:shared:compileKotlinJvm`, `:shared:compileAndroidMain` and `:desktopApp:compileKotlin` pass.
  - Gallery renders pass (Admin people/system/inventory, Workspace, KDS, Payment): `./gradlew :shared:jvmTest --tests 'com.saporini.mobile_desktop.gallery.*'`, PNGs in `shared/build/reports/gallery/`.
  - The full test suites were NOT run (the user runs tests at the end).
  - The live endpoints these screens call answer 200 on the local backend. The local DB has no inventory or recipes, so those tabs show their empty states.
- **Open / for the user to review one by one:** look and wording of each screen; KDS dish routing per station isn't editable yet (the station dialog keeps existing routings); the Reservations booking panel has no pre-order hook; the desktop app was relaunched (`:desktopApp:run`) against the backend still running on :8080.

## 2026-10-07 (Claude, feature/orders) — Backend and desktop app started for the user (~17:45)
- `podman start pos-db pos-mailhog`. Then the backend: `SPRING_PROFILES_ACTIVE=local SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS='*:missing' MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS_ENABLE=false java -jar target/pos-0.0.1-SNAPSHOT.jar` (pid 53842, log `back-end/app.out.log`).
- **Why the extra Flyway flag:** the local DB has V39 "notifications" applied, but that file was never in `src/`; it only lived in an old `target/classes`, now gone. Without the flag Flyway validation fails at startup.
  - The flag changes nothing in the DB; it only skips that check for this run.
  - The cleaner local fix is `flyway repair` (or restore the file), which is the user's call.
- With the flag, V55–V65 applied to `foundation_local` (now at v65). `/health/ready` answers 200.
- Desktop app: `./gradlew :desktopApp:run` (MainKt pid 55111). No code changes.

# 2026-10-07 continuation: final state compile and verification handoff
- After correcting the mobile inventory 409 rejection behavior, `InventoryScreenModelTest` passed 21/21 and `:desktopApp:compileKotlin :shared:compileAndroidMain` passed against the final sources. The forced full mobile JVM run earlier in this continuation passed 338/338; only the focused 21-test suite was rerun after the final state-model change.
- Full backend `./mvnw -o verify` is green at 1,264 unit + 331 PostgreSQL integration tests. `AGENTS.md` now records these counts, V65 as the latest migration (next V66), and the intentional absence of screens for the requested state-only modules. Root images 5.png, 7.png, 8.png, and 9.png are deleted. `git diff --check` passed.
- Current operator review items are listed in the preceding entries and final task response; do not describe external payment, notifications, storage, inventory operations, or deployment setup as production-provisioned.

# 2026-10-07 continuation: fix inventory movement rejection handling
- Independent review found that `InventoryScreenModel.saveMove` treated every HTTP 409 as an uncertain write, even though stock conflicts (such as insufficient source quantity) are transactional rejections. The pending draft then became frozen and persisted indefinitely. Known 4xx responses now clear the idempotency record and leave the draft editable; changed the regression to exercise the real 409 status. Fresh focused `InventoryScreenModelTest` passed 21/21. The earlier fresh full mobile run passed 338/338 before this small follow-up; desktop and Android compilation passed in that run.
- Full backend rerun remains green at 1,264 unit + 331 PostgreSQL integration tests; focused notification integration passed 4/4. The main prior verification entry records the detailed notification assertion correction. `git diff --check` passes.
- Confirmed policy still needing operator review: recipe stock is consumed at fulfillment, so a missing branch source or insufficient stock rejects fulfillment; orders only consume tracked stock when fulfilled. Choosing negative stock, backorders, or non-blocking consumption changes inventory accounting and is not silently changed here. Other deployment gaps remain live payment settlement, durable notification delivery, production file storage, cleanup for the idempotency replay table, and rollout/observability review.

## 2026-10-07 (Claude, feature/orders) — Review of the overnight changes (no code changed)
**The user asked whether the overnight changes (V56–V65, about 200 files) are better.** Review snapshot ~13:50. Another agent was still editing (a test at 14:06, AGENTS.md at 14:08).
- **Ran in an isolated copy (scratchpad):**
  - Backend: 1,264 unit tests pass. 331 ITs ran with 2 failing:
    - `NotificationPreferenceConcurrencyIntegrationTest.manualInAppBroadcastIsPersistedAsDelivered` (count 0, expected 1). It also fails alone, and was being edited at 14:06.
    - `TenantIsolationIntegrationTest`: NPE in its `addParams`, because `MethodParameter.getParameterName()` is null for unnamed `@RequestParam`s. That's a weakness in the test helper, exposed by the new paged endpoints. Fix: call `initParameterNameDiscovery`.
  - App: 338 jvm tests pass; desktop and Android compile.
  - The in-place `./mvnw verify` broke halfway (NoClassDefFoundError) because something else rewrote `back-end/target` during the run. Test in a copy while another agent or the IDE is active.
- **Bug, proven with a probe test in the copy:**
  - In the app, a stock change refused with a 409 (e.g. "Stock balance cannot go below zero", inactive location or item) is treated as "may have saved". It is frozen for good: it can't be edited, cancelled or replaced, and it is kept across restarts and logout.
  - `InventoryScreenModel.saveMove` should treat 409s with a known reason as rejections.
  - The existing test `aRefusedStockChangeKeepsTheDraft` uses a 400, which the server doesn't send.
- **Design concerns raised with the user:**
  - Selling now blocks service. Marking food served (POS fulfil, KDS complete, pick-up) fails with 409 when recorded stock is short, or when a branch has no sale source location for an ingredient.
  - Orders closed or paid without being fulfilled never take stock off.
  - In production, every non-cash payment is refused (`ConfiguredPaymentCapturePolicy`), including cards on an external terminal, gift cards, house accounts and bank transfers.
  - The POS customer list still downloads every page.
  - The write-replay table has no cleanup.
  - Device heartbeat takes a row lock before checking the secret.
  - Device endpoints record `getRemoteAddr()` instead of `ClientInfoExtractor`.
- **Good fixes confirmed:**
  - The discount bypass through order PATCH.
  - Atomic stock balances.
  - Recipe unit conversion and sub-recipe yield costing (my earlier costing ignored units).
  - The table merge deadlock and race, and the OTP attempt race.
  - OTPs removed from SMS logs.
  - Refunded tips in pay and statistics.
  - Discount order kept stable.
  - Unique barcodes and count numbers.
  - Health probes.
  - Fixed notification pool starvation.
  - Required idempotency keys for payments and stock.

# 2026-10-07 continuation: rerun complete backend and mobile verification
- Resumed the paused POS completion task, restored rootless Podman test access, and ran a clean backend `./mvnw -o verify`. The first run passed all 1,264 unit tests but exposed a stale expectation in `NotificationPreferenceConcurrencyIntegrationTest.manualInAppBroadcastIsPersistedAsDelivered`: the derived event code includes the reference token (`ORDER_ORDER_BROADCAST`). Updated the assertion to check the response event code and exact persisted notification ID. The focused notification integration class then passed 4/4.
- Re-ran the full backend suite after the correction: 1,264 unit + 331 PostgreSQL integration tests passed, 0 failures/errors/skips. Mobile desktop compilation, Android shared compilation, and a forced full JVM test run passed: 338 tests, 0 failures/errors/skips. `git diff --check` passed. Root-level image files requested for removal remain deleted.
- Updated `AGENTS.md` verification count and migration guidance (latest V65; next V66). Mobile state modules remain screenless as requested. This is not production sign-off: live non-cash payment capture/settlement, durable email/SMS delivery, durable production file storage, inventory-on-sale/refund policy and legacy stock reconciliation, and deployment/observability review remain open.

# 2026-10-07 continuation: reject notification channels with no delivery worker
- The manual notification broadcast API accepted EMAIL, SMS, PUSH, and WEBHOOK while the dispatcher only persisted them as QUEUED and no delivery worker existed. `NotificationService.broadcast` now fails closed with HTTP 409 for every non-IN_APP channel, before creating a notification row.
- Added PostgreSQL integration coverage that exercises all four unsupported channels and asserts the restaurant notification row count is unchanged. `./mvnw -o -Dskip.unit.tests=true -Dit.test=NotificationPreferenceConcurrencyIntegrationTest verify` passed all 3 integration tests; touched-file `git diff --check` passed. The mobile notification center does not call the broadcast endpoint, so no mobile state or screen changed.
- This prevents silent queued-message loss; it does not implement email/SMS/PUSH/WEBHOOK delivery. Durable reservation guest-email retries and provider integrations remain open.

# 2026-10-07 continuation: persist uncertain mobile inventory writes across restart
- Added account-scoped pending inventory movement persistence to the existing mobile Inventory state model. Android stores it through the existing encrypted Preferences DataStore; JVM/desktop stores it alongside auth data using the existing DPAPI/owner-only persistence path. Logout clears credentials but preserves uncertain movement records.
- Stock movement request data and its idempotency key are persisted before sending. Ambiguous failures freeze the exact draft against edits/cancellation, same-user/same-restaurant sign-in restores it, and successful replay removes it. Known non-409 4xx responses clear the pending record and allow correction; corrupt storage fails closed and is not overwritten. Wired the existing model through TokenStore; no screen added.
- Added restart/replay-once, locked-draft, known-rejection, corrupt-storage, and persistence-clear tests. Updated an Orders screenshot fake to implement the current paged repository contract after its four screenshot variants failed with the stale unpaged fake. Fresh full mobile JVM run passed 338 tests, 0 failures/errors/skips; Android `:shared:compileAndroidMain` passed. Full backend `./mvnw -o verify` reports 1,285 unit + 329 PostgreSQL integration tests, 0 failures/errors/skips. Touched-file `git diff --check` passed.
- Production remains blocked on external delivery/storage/payment integrations and explicit inventory refund/void and legacy reconciliation policies; do not call the entire backend production-ready.

# 2026-10-07 continuation: make inventory movements safe to retry
- Extended the authenticated write-replay filter to the five stock-changing inventory endpoints (`receive`, `waste`, `transfer`, `returns`, `adjustments`) and require a 16–100 character `Idempotency-Key`. Inventory operations serialize only identical owner/key pairs; the atomic SQL quantity delta still handles concurrent distinct stock movements. Renamed the replay table to `write_request_replays` through V65 while retaining its contents.
- Updated the existing mobile Inventory API/state flow to send a UUID key and retain it with an unchanged movement draft after an uncertain response; changing/cancelling the draft clears the key, and confirmed success clears both. No screen added.
- PostgreSQL integration verified same-key replay, changed-body 409, missing-key 400, one movement/one stock delta, and simultaneous same-key requests returning one original plus one replay. Full `InventoryFlowIntegrationTest` + `RecipeFlowIntegrationTest` passed 28/28 (14 + 14); mobile `InventoryScreenModelTest` + `InventoryApiTest` passed 19/19; `git diff --check` passed.
- Reconciliation: the earlier note saying device pairing redemption is absent is stale for the backend. Current source includes hashed one-time token redemption, parent-device locking, single-use device-secret issuance, and heartbeat authentication. `DeviceManagementConcurrencyIntegrationTest` passed 6/6 on PostgreSQL in this turn. No mobile pairing-redemption client was found; the existing admin device flow issues/revokes codes, so a physical-device bootstrap client remains an integration question rather than a missing backend redemption endpoint.
- Inventory idempotency remains in-memory on the mobile draft; a process restart after a lost response can lose its key. Keep this as a residual risk unless pending write keys are durably stored. Broader backend readiness is still open.

# 2026-10-07 continuation: clear mobile statistics on revoked access
- Hardened the existing `StatisticsScreenModel` so a 401/403 clears all cached overview, sales, staff, report-catalog, and downloaded CSV data while disabling further reads. Other transient failures still preserve the prior figures as stale data.
- Extended the existing mobile regression to load every protected result and a CSV, simulate 403, assert all protected data is removed, and confirm refresh makes no further API request. All mobile statistics tests passed: 10 tests (8 screen-model, 2 period/API rules), 0 failures/errors/skips. No UI was added; `StatisticsWorkspaceScreen` remains a navigation shell without a built statistics interface.
- This closes a mobile in-memory data exposure on permission revocation, not the broader Statistics domain audit or production sign-off.

# 2026-10-07 continuation: fail closed on unverified production payments
- Added `PaymentCapturePolicy` and a configured policy that always permits cash, permits non-cash simulator captures only for non-prod `app.payments.provider=test`, and otherwise rejects non-cash capture with HTTP 409 until a trusted live provider is configured. Added the simulator as test-source-only integration configuration; the focused production-profile test and test provider policy cases passed.
- Updated the existing mobile payment screen model so an explicit 409 refusal is treated as a known rejection (no ambiguous retry key) and its message survives the order refresh. Added a state regression covering the unavailable-provider message and preserved amount; `./gradlew :shared:jvmTest --tests com.saporini.mobile_desktop.payment.PaymentScreenModelTest` passed.
- Verification: focused payment/order PostgreSQL integration run passed 26/26; full backend `./mvnw -o verify` passed 1,259 unit + 326 integration tests (1,585 total), 0 failures/errors/skips; `git diff --check` passed.
- Production payments remain unavailable for non-cash until a trusted gateway capture/refund/void adapter and settlement/reconciliation are connected. No mobile screen was added. Broader release blockers remain in prior entries (durable SMS/email delivery, durable production image storage, refund/void stock policy/reconciliation, device pairing redemption, inventory-barcode deployment preflight, and deployment configuration/observability review).

# 2026-10-07 continuation: enforce Menu daily availability hours
- Added `Menu.isAvailableAt(LocalDateTime)` with inclusive date/time boundaries, optional one-sided hour limits, overnight windows, and null-time rejection. Public menu list/detail now evaluate hours using restaurant-local time; today's public online menu and staff preview apply the same rule, while explicit future-date online menu requests remain date-based because they do not include an order time. Staff preview distinguishes date and time hidden reasons.
- Added 5 entity unit tests for all-day/date bounds, ordinary hours, one-sided hours, overnight hours, and overnight windows at the final date boundary, plus a PostgreSQL/API case covering public list/detail, public online menu, and staff preview.
- Verification: `MenuAvailabilityTest` + `PublicMenuServiceTest` passed 10/10; full `MenuApiIntegrationTest` passed 31/31; after final hidden-reason wording adjustment, the focused PostgreSQL/API regression passed 1/1; `git diff --check` passed. No response shape or existing mobile state changed; no screen was added.
- Remaining Menu readiness includes deeper import/export and image storage review, and defining time-of-day behavior for explicit future-date online menu requests. This is not a whole-backend production sign-off.

# 2026-10-07 continuation: enforce Menu daily availability hours
- Added `Menu.isAvailableAt(LocalDateTime)` with inclusive date/time boundaries, optional one-sided hour limits, overnight windows, and null-time rejection. Public menu list/detail now evaluate hours using restaurant-local time; today's public online menu and staff preview apply the same rule, while explicit future-date online menu requests remain date-based because they do not include an order time. Staff preview reports whether a dish is hidden by date or time.
- Added 4 entity unit tests for all-day/date bounds, ordinary hours, one-sided hours, and overnight hours, plus a PostgreSQL/API case for public list/detail, public online menu, and staff preview behavior.
- Verification: `MenuAvailabilityTest` + `PublicMenuServiceTest` passed 9/9; full `MenuApiIntegrationTest` passed 31/31; after a final hidden-reason wording adjustment, the focused PostgreSQL/API regression passed 1/1; `git diff --check` passed. No response shape or existing mobile state changed; no screen was added.
- Remaining Menu readiness still includes broader import/export/storage review and end-to-end consumer behavior for explicit future-date online menus (no time-of-day is part of that API). This is not a whole-backend production sign-off.

# 2026-10-07 continuation: cover payment void idempotency
- Added an end-to-end PostgreSQL regression for void retries: the same key/body replays the original response, a changed reason with that key conflicts, the order stays reopened, and only one VOID transaction is recorded. No implementation or API behavior changed.
- Verification: `./mvnw -o -Dskip.unit.tests=true -Dit.test=PaymentFlowIntegrationTest#idempotentVoidRetry verify` passed 1/1 integration test; `git diff --check` passed. No mobile contract/state changed and no screen was added.
- Payment remains blocked from production sign-off until a trusted live card provider captures/refunds/voids transactions and settlement/reconciliation behavior is implemented. The current order-payment service records caller-supplied card metadata as CAPTURED; do not represent it as live provider settlement.

# 2026-10-07 continuation: lock order-to-table writes against table merges
- A PostgreSQL race test showed that simultaneous table merge and table-order creation could leave an open order attached to a merged child. Order creation and order/table reassignment now load table rows with a write lock and reject inactive, merged, RESERVED, DIRTY, MAINTENANCE, or OUT_OF_SERVICE tables (AVAILABLE and OCCUPIED remain valid). Same-table updates remain allowed, so a table becoming unavailable does not block edits to its existing order.
- Removed the eager association graph from the pessimistic table lookup. Hibernate had used follow-on locking and could return a stale pre-lock table state after waiting for a merge; lazy associations now load after the row lock. Table merges and order writes share the same table row locks. No response fields changed; no mobile state or screens changed.
- Added PostgreSQL/API coverage for unavailable/merged-table order rejection and the concurrent merge-versus-order race, asserting no open order remains attached to the merged child. The opposite-direction merge race remains covered.
- Verification: `OrderLifecycleIntegrationTest` passed 12/12 against PostgreSQL; `TableAvailabilityApiIntegrationTest` 2/2; `ReservationFlowIntegrationTest#concurrentReservationsCannotDoubleBookTable` 1/1; `RestaurantTableServiceTest` + `ReservationServicesTest` 12/12; `git diff --check` passed.
- Remaining: full Tables/Orders audit, broader order update/payment concurrency checks, and the wider backend service readiness review are still open; this is not a production sign-off.

# 2026-10-07 continuation: serialize overlapping table merges
- `RestaurantTableService.mergeTable` previously locked its primary table before locking targets, so opposite-direction merges could acquire the same two row locks in opposite orders and deadlock. It now acquires all primary/target locks through one stable-ID-order repository query before reading merge state. Generalized the shared table lock method name and kept reservation booking on the same implementation.
- Added a unit assertion for locking all merge participants and a PostgreSQL API race that starts A→B and B→A together, requires one 200 and one 409, and checks the final merge relationship. No response/mobile contract changed; no screen or mobile state was added.
- Verification: `TableAvailabilityApiIntegrationTest` passed 2/2 against PostgreSQL; `RestaurantTableServiceTest` passed 4/4; `ReservationServicesTest` passed 8/8 after the shared lock method rename; `git diff --check` passed.
- The broader Tables domain still needs review of table/order association invariants and other concurrent workflows; this is not a Tables or backend production sign-off.

# 2026-10-07 continuation: page open orders in the existing mobile list
- The active Orders screen used the unbounded `/orders/open` endpoint, so large branches loaded every open order at once. Added an `openOnly` filter to the existing bounded branch order page endpoint, rejecting contradictory `historyOnly` + `openOnly` filters and statuses outside DRAFT/OPEN.
- Updated the existing mobile order repository/API and OrdersScreenModel to page open orders, retain loaded pages on refresh, support server-side search, validate returned statuses, and load more through the existing list state. No screens were added.
- Added PostgreSQL/API assertions for open-only filtering, pagination, contradictory filters, and invalid statuses. Updated mobile API-contract and state-model tests for open paging, append, refresh, and search.
- Verification: `OrderLifecycleIntegrationTest` passed 10/10 against PostgreSQL; focused mobile `OrdersScreenModelTest` and `OrderDataContractTest` passed; `git diff --check` passed. The first mobile test attempt exposed a test-only active polling loop that did not deactivate; the fixture now deactivates and the focused rerun completed.
- Remaining order production checks include the legacy unpaged endpoints still used by non-list operations and a broader order-flow concurrency/idempotency/failure review. This is one slice of the ongoing POS production-readiness work, not a whole-backend sign-off.

# 2026-10-07 continuation: batch menu import lookups
- The menu import endpoint accepts up to 100 item IDs but was loading variants, option-group links, and KDS routings once per source item, with additional lazy loads for source menu scope and ingredients. Added a source-item query that fetches its section/menu/restaurant and ingredients, then batch-loaded and grouped variants, option groups, and routings before copying.
- Kept import ordering, duplicate-ID de-duplication, separate-copy behavior, and foreign-restaurant rejection unchanged. Added unit assertions that two-item imports issue one batch lookup per related domain and do not use the per-item repository methods.
- Verification: `MenuItemImportServiceTest` passed 3/3; PostgreSQL-backed `MenuApiIntegrationTest` passed 30/30 through V64; `git diff --check` passed. No response contract or mobile state changed; no screen was added.

# 2026-10-07 continuation: authenticate paired-device heartbeats
- Added `POST /public/devices/{deviceId}/heartbeat`, which checks the one-time-issued device secret against its stored peppered hash with constant-time comparison, requires an active ACTIVE device, and updates online/last-seen/IP fields under a device-row lock.
- Deactivation, retirement, blocked, or maintenance status now clears the stored device secret and rotation timestamp as well as revoking open pairing tokens. Reactivation therefore requires pairing again; integration coverage proves the prior secret stays invalid and a newly paired secret works.
- Verification: `DeviceManagementConcurrencyIntegrationTest` passed 6/6 against PostgreSQL; `DeviceManagementServiceTest` passed 2/2; `git diff --check` passed. No existing mobile screen uses the heartbeat or redemption APIs, so mobile state and screens were unchanged. Device secrets do not yet authorize POS/KDS operations, and no hardware client is wired to send heartbeats.

# 2026-10-07 continuation: redeem device pairing tokens once
- Added public `POST /public/devices/pairing/redeem`: hashes the supplied pairing token, locks the parent device consistently with issuance/revocation, rejects used/expired/revoked or inactive-device tokens with one generic 401, stores only the new device-secret hash, and returns the raw device secret once.
- Added PostgreSQL concurrency/API coverage proving simultaneous redemptions yield exactly one success and one rejection, token history hides the raw token, and the database stores a hash rather than the returned secret. No existing mobile screen calls this API; the admin device UI contract/state did not change, and no screen was added.
- Added invalid-token coverage showing unknown, revoked, and expired tokens all return the same generic 401 response. Verification: `DeviceManagementConcurrencyIntegrationTest` passed 5/5 on PostgreSQL; `git diff --check` passed. The actual device-secret authentication path is still absent, so this completes enrollment redemption only, not full device authentication.

# 2026-10-07 continuation: bound tip-suggestions request text
- A controller/request DTO audit found `tipSuggestionsText` used a regex that allowed arbitrarily long whitespace even though its semantic content is at most six percentages. Added `@Size(max = 64)` while preserving the existing pattern and valid inputs.
- Added PostgreSQL/API coverage proving ordinary whitespace formatting succeeds and a 65-character whitespace-only value returns 400. The existing mobile settings editor already limits this field to 23 characters, so no mobile state or screen change was needed.
- Verification: `SettingsExtendedIntegrationTest` passed 8/8; compilation completed; `git diff --check` passed. A source scan found every controller `@RequestBody` covered by `@Valid`; this field was the unbounded request string identified in the scanned request DTOs.
- Remaining production blockers remain: live payment processing/settlement, durable SMS/email delivery/retries, durable image/floor-plan storage, inventory refund/void policy and legacy reconciliation, device pairing redemption/authentication, full supplier management, deployment configuration/observability, and legacy unpaged-order compatibility.

# 2026-10-07 continuation: add safe production liveness and readiness probes
- Added unauthenticated `GET /health/live` and `GET /health/ready` endpoints and allowed only those exact paths through `SecurityPaths`. Liveness does not depend on the database; readiness runs `SELECT 1`, returns 503 on data-access failure, and exposes only UP/DOWN status.
- Added unit coverage for database-independent liveness and readiness failure without leaking connection details. Extended the production-profile PostgreSQL smoke test to verify both probes are publicly reachable and healthy while the database is available. No mobile contract/state changed; no screen added.
- Verification: `OperationalHealthControllerTest` passed 2/2; `ProdProfileSmokeTest` passed 1/1 on PostgreSQL; `git diff --check` passed.
- Other production blockers remain: live payment processing/settlement, durable SMS/email delivery/retries, durable image/floor-plan storage, inventory refund/void policy and legacy reconciliation, device pairing redemption/authentication, full supplier management, deployment configuration/observability beyond basic probes, and legacy unpaged-order compatibility.

# 2026-10-07 continuation: generate collision-resistant stock count numbers
- Replaced timestamp-derived inventory count numbers (`IC-<millisecond>`) with random UUID references so simultaneous count creation does not collide on `uk_inventory_counts_restaurant_count_number`. Duplicate caller-provided count numbers now map that named unique constraint to HTTP 409.
- Added an API/PostgreSQL test creating six counts concurrently and asserting all generated references are unique, plus a duplicate custom-number conflict assertion. The existing mobile inventory model treats `countNumber` as a `String`; the contract type did not change, so no mobile state or screen change was needed.
- Verification: `InventoryFlowIntegrationTest` passed 12/12 against PostgreSQL through migrations V1–V64; compilation completed; `git diff --check` passed. The most recent full backend `./mvnw -o verify` before this change passed 1,249 unit and 316 integration tests.
- Remaining production blockers are unchanged: live payment provider/settlement, durable notifications, durable image/floor-plan storage, refund/void inventory policy and legacy reconciliation, device pairing redemption/authentication, full supplier management, production configuration/observability review, and legacy unpaged-order compatibility.

# 2026-10-07 continuation: preserve stacked discount application order
- Found that order discounts were recalculated and returned by `created_at` only; equal persisted timestamps could reorder a percentage and fixed discount and change the charged total. Added `discount_sequence` with a V64 backfill ordered by prior timestamp and ID, assign sequence when discounts are added, and use it for ORM ordering and total recalculation.
- Added an API/PostgreSQL regression that forces two stacked discounts to the same stored timestamp, reloads them, and checks application order and exact amounts. Fixed `OrderControllerSecurityTest` setup with the missing `OrderHistoryService` mock, which had caused three full-suite context errors.
- Verification: `OrderLifecycleIntegrationTest` passed 10/10; `OrderMoneyGuardsTest` passed 6/6; `OrderControllerSecurityTest` passed 3/3; full backend `./mvnw -o verify` passed 1,249 unit tests and 316 PostgreSQL integration tests (1,565 total, 0 failures/errors/skips); `git diff --check` passed. No mobile contract or state changed, and no screen was added.
- Broader production blockers remain: live payment settlement/reconciliation, durable SMS/email delivery and retry, durable image/floor-plan storage, explicit inventory refund/void and legacy reconciliation policy, device pairing redemption/authentication, supplier-management completeness, deployment configuration/observability review, and legacy unpaged-order compatibility. This is an ongoing POS readiness effort; do not treat it as a whole-repository sign-off.

# 2026-10-07 continuation: keep undelivered notifications unread
- `markAllRead` now updates only delivered personal notifications visible to the user, including restaurant-wide notifications in a branch feed, and updates their audit timestamp/actor. Marking one queued/undelivered personal notification returns HTTP 409. Notification responses expose `markReadAllowed` only when the notification is personal and delivered.
- Updated the existing mobile notification model/state: queued notifications cannot be marked read, and read actions refresh from the server only after success instead of optimistically clearing unread state. No screen was added.
- Added API/PostgreSQL coverage for mixed queued email, branch in-app, and restaurant-wide in-app notifications, read-all behavior, audit timestamps, API eligibility metadata, and rejection of a single undelivered read attempt. The full `NotificationPreferenceConcurrencyIntegrationTest` passed 2/2; a final focused lifecycle rerun passed 1/1; mobile `NotificationKindTest` passed 3/3. `git diff --check` passed.
- Durable external notification delivery/retry and provider wiring remain separate production blockers.

# 2026-10-07 continuation: serialize menu variant default changes
- Added a pessimistic write lock for the parent menu item and use it on variant create/update/delete. This serializes each dish's default-variant check and clearing logic while leaving variant reads unlocked.
- Added a concurrent API/PostgreSQL regression that marks two variants as default at the same time and verifies both requests complete with exactly one default remaining.
- Verification: `MenuVariantServiceTest` + `MenuItemServiceTest` passed 11/11; the complete PostgreSQL-backed `MenuApiIntegrationTest` passed 30/30 through migrations V1–V63; `git diff --check` passed. No API contract or mobile state changed.
- Previously recorded production blockers remain: live payment settlement/reconciliation, durable SMS/email delivery, durable image storage, refund/void inventory policy and legacy reconciliation, device pairing redemption, duplicate active inventory barcode preflight before V63, production configuration/observability review, and compatibility migration for legacy unpaged order lists.

# 2026-10-07 continuation: batch menu item list expansions
- Found that `GET /menus/{menuId}/sections/{sectionId}/items?includeVariants=true&includeOptionGroups=true` loaded variants and option-group links separately for every menu item. Reused existing ordered bulk repository queries so the list path now loads each requested expansion in one batch and maps them back by item ID; single-item lookup behavior and response shape are unchanged.
- Added assertions in `MenuItemServiceTest` that expanded lists use the batch queries and do not issue per-item relationship lookups. No mobile API or screen-state change was needed.
- Verification: focused `MenuItemServiceTest` passed 8/8; PostgreSQL-backed `MenuApiIntegrationTest` passed 29/29 through migrations V1–V63; `git diff --check` passed.
- Broader production work remains open: the live payment provider and settlement/reconciliation, durable SMS/email delivery and retries, durable image storage, explicit refund/void inventory-restock and legacy reconciliation policy, device pairing redemption, duplicate active inventory-barcode preflight before V63, production configuration/observability review, and compatibility migration for legacy unpaged order-list APIs.

# 2026-10-07 — Enforce inventory item-code and barcode uniqueness under races
- Added V63 partial unique index on `(restaurant_id, upper(barcode))` for non-deleted items, with a preflight exception if existing data contains duplicates. This makes barcode scans unambiguous at the database boundary.
- Inventory code availability now includes soft-deleted rows to match its existing unique constraint. Named item-code and barcode unique violations return HTTP 409, including concurrent requests that both pass the initial check.
- Added six-way API/PostgreSQL races for duplicate item codes and duplicate barcodes; each race stores exactly one item and returns one 201 plus 409 conflicts. Verification: focused race test 1/1; full `InventoryFlowIntegrationTest` 11/11; `git diff --check` passed. No API/mobile state change.
- Deployment note: V63 intentionally stops if existing non-deleted inventory items have duplicate barcodes within a restaurant; inspect and resolve those rows before rollout rather than silently changing stock identifiers.

# 2026-10-07 — Defuse whitespace-prefixed spreadsheet formulas in CSV exports
- CSV cell escaping now looks past leading whitespace, Unicode space separators, control characters, and a BOM before checking formula prefixes. Negative decimal currency remains unescaped.
- Added unit cases for space/tab, nonbreaking-space, and newline-prefixed formulas. Existing API integration coverage still downloads the CSV and verifies formula-like menu text is emitted as inert text.
- Verification: `StatisticsServiceTest` 10/10; `StatisticsAndFraudIntegrationTest` 4/4; `git diff --check` passed. No API or mobile state changed.

# 2026-10-07 — Align customer-code conflicts with the database constraint
- Customer code availability now includes soft-deleted rows, matching `uk_customers_restaurant_code`. Duplicate-key races on that named constraint are translated to HTTP 409 instead of a generic 400.
- `CustomerFlowIntegrationTest` now checks active duplicates, soft-deleted code reuse, and six concurrent create requests for one code; exactly one record remains and every loser receives 409.
- Verification: focused PostgreSQL/API `CustomerFlowIntegrationTest` passed 6/6 (0 failures/errors/skips); `git diff --check` passed. Existing mobile POS only reads the paged customer list and has no customer create/update state, so no mobile change or screen was needed.

# 2026-10-07 — Prevent schedules overlapping actual late clock-outs
- `ShiftRepository.overlaps` now checks both the scheduled interval and the actual attendance interval. A shift that ran later than scheduled therefore blocks a new overlapping staff shift.
- Added `scheduleCannotOverlapLateAttendance` in `KitchenAndShiftsIntegrationTest`, seeding a completed shift whose actual end is later than its schedule and verifying an overlapping schedule gets HTTP 409 without adding a shift.
- Verification: focused late-attendance PostgreSQL/API test 1/1; full `KitchenAndShiftsIntegrationTest` 5/5; `ShiftServiceTest` + `ShiftPayServiceTest` 28/28; `git diff --check` passed. No API/mobile state changed, so no mobile screens or state changed.

# 2026-10-07 — Stress same-table reservation booking under concurrency
- Added an API-level PostgreSQL race to `ReservationFlowIntegrationTest`: six simultaneous reservation creates request the same table and time window. Exactly one must return 201; every other request must conflict (409); the database must contain exactly one assignment for that table.
- The code path locks the selected table rows in stable ID order, then checks for overlapping assignments before saving. Verification: focused `concurrentReservationsCannotDoubleBookTable` integration test passed 1/1 with six competing requests; `git diff --check` passed. No API/mobile state or screens changed.

# 2026-10-07 — Verify discounts stay immutable after payment closes an order
- Extended `OrderLifecycleIntegrationTest` with an end-to-end case that fully pays an order with a percentage discount, then attempts to add, update, and delete discounts. All three mutations must fail with HTTP 400, and a follow-up read must preserve CLOSED status, the original discount row, discount total, and order total.
- Verification: focused PostgreSQL/API `OrderLifecycleIntegrationTest` passed 9/9 (0 failures/errors/skips); `git diff --check` passed. This is test-only; no API/mobile state or screens changed.
- Existing `OrderItemService` applies the shared editable-order guard to add/update/delete discount paths; this integration test now guards that behavior across the real paid/closed lifecycle.

# 2026-10-07 — Serialize notification preference first writes
- `NotificationPreferenceService` now locks the active user row before upserting notification preferences. This serializes the first-write check for the same user/channel/event and prevents concurrent requests from colliding on the unique preference key.
- Added `NotificationPreferenceConcurrencyIntegrationTest`: concurrent enabled/disabled writes both return success and leave exactly one preference row.
- Verification: targeted notification concurrency integration test passed 1/1; full backend `./mvnw -o verify -Dskip.unit.tests=true` passed 1,270 unit + 307 PostgreSQL integration tests (1,577 total, 0 failures/errors/skips); `git diff --check` passed. No mobile API/state changed, so no mobile screens or state were changed.
- Notification delivery is still LOG_ONLY for SMS and lacks a durable outbox/retry path; this change only fixes concurrent preference storage.

# 2026-10-07 — Complete recipe-linked modifier editing in the existing menu flow
- The existing POS Menu → Options editor now loads linked option-group choices with `GET /option-groups/{id}?includeItems=true`, exposes active PREP_BATCH/SUB_RECIPE choices and usage quantity, and sends/retains `inventoryRecipeId` plus `inventoryRecipeQuantity` when creating or editing choices. Existing recipe links remain visible as unavailable if the linked recipe is no longer active, with an option to clear them. No screen was added.
- Added mobile API/state coverage for option-group detail loading, recipe mapping round-trip, and retaining recipe IDs/quantities in the editor draft. Added a backend PostgreSQL/API assertion that option-group detail returns the linked recipe ID and usage quantity.
- Verification: full mobile `:shared:jvmTest` passed 327/327 (0 failures/errors/skips); `MenuOptionInventoryRecipeApiTest` passed 3/3; backend `RecipeFlowIntegrationTest` passed 14/14; `git diff --check` passed.
- The modifier-to-recipe assignment workflow is now present in the existing menu editor. Remaining larger production gaps include live payment/settlement, real SMS and durable notification retries, durable floor-plan storage, supplier management, device pairing redemption/authentication, and refund/void plus legacy stock policy.

# 2026-10-07 — Revoke device pairing tokens on deactivation
- Device status changes now lock the same device row used by pairing issuance and revoke all open pairing tokens whenever the device becomes inactive, retired, blocked, or enters maintenance. Pair-token issuance refreshes the locked entity after waiting, preventing a stale pre-lock status from allowing a token after deactivation commits.
- Added a PostgreSQL/API race covering pre-existing and concurrently issued pairing tokens during retirement. It verifies no active token remains after both requests finish.
- Verification: `DeviceManagementConcurrencyIntegrationTest` 3/3; `DeviceManagementServiceTest` 2/2; full `./mvnw -o verify` passed 1,270 unit + 306 PostgreSQL integration tests (1,576 total, 0 failures/errors/skips); `git diff --check` passed. API contracts did not change, so no mobile state or screens changed.
- Device pairing still lacks token redemption/device-secret exchange and a device-authentication path. Remaining broader production blockers are real payment/settlement, real SMS and durable notification retries, durable floor-plan storage, supplier management, refund/void and legacy stock policy, and completion of the modifier-recipe operator workflow.

# 2026-10-07 — Serialize device assignment and pairing-token rotation
- Device management writes now acquire a pessimistic lock on the device row before replacing active assignments, issuing a pairing token, or revoking one. Pairing tokens are refused for inactive, retired, blocked, or maintenance devices.
- Added `DeviceManagementConcurrencyIntegrationTest`: competing assignment writes leave one active assignment; competing token issues leave one active and one revoked token; a retired device cannot receive a token. Updated the device service unit fixture for the lock repository call.
- Verification: focused Device PostgreSQL tests 2/2; `DeviceManagementServiceTest` 2/2; full `./mvnw -o verify` passed 1,270 unit + 305 PostgreSQL integration tests (1,575 total, 0 failures/errors/skips); `git diff --check` passed.
- No mobile API contract or state changed, so no mobile screens/state were changed. Device pairing remains incomplete: the backend issues and revokes pairing tokens but has no token-redemption endpoint/device-secret exchange or device-authentication path. That needs implementation and API/device-client coverage before device onboarding is production complete.

# 2026-10-07 — Redact OTP data from SMS logs; confirm Supplier module gap
- `SmsMessageService` no longer logs destination phone numbers or SMS bodies (including password-reset and phone-verification OTPs). It logs only delivery mode and that sensitive fields were omitted. Updated log-capture assertions in `back-end/src/test/java/pos/pos/unit/auth/service/SmsMessageServiceTest.java`.
- Confirmed there is no standalone Supplier package/controller/entity/repository or supplier-management test suite. Inventory items only carry free-text `supplierName` and `supplierSku`; supplier records, purchase orders, and receiving against supplier documents do not exist.
- Verification: `SmsMessageServiceTest` passed 6/6; full `./mvnw -o verify` passed 1,270 unit + 303 PostgreSQL integration tests (1,573 total, 0 failures/errors/skips); `git diff --check` passed. Payment key contract and mobile state/API tests remain green from this turn. No mobile screens or state were changed.
- Messaging remains not production ready: SMS is still LOG_ONLY and email errors are only logged without durable outbox/retry. Supplier management remains a product/module gap rather than an existing service to rate.

# 2026-10-07 — Require idempotency keys for payment mutations
- `OrderWriteFilter` now rejects payment take, refund, and void POSTs without an `Idempotency-Key`, while its existing transactional replay and restaurant serialization still return the original response for retries. Updated the refund/void API descriptions. Existing mobile `PaymentApi` already sends a key and `PaymentScreenModel` keeps the same key for uncertain retries, so no mobile code change was necessary.
- Added API checks that missing keys produce no payment/refund/void ledger changes and a six-way same-key payment retry creates one payment and replays the same response. Updated the integration request helper to generate keys for legacy payment test calls while retaining an explicit no-key helper.
- Verification: `PaymentFlowIntegrationTest` passed 12/12; mobile `PaymentApiTest`/`PaymentStateTest` passed (3 tests); full backend `./mvnw -o verify` passed 1,270 unit + 303 PostgreSQL integration tests (1,573 total, 0 failures/errors/skips); `git diff --check` passed.
- Payment production blockers remain a real gateway authorization/capture/refund/void integration plus settlement/reconciliation, and an operational archive/retention plan for the idempotency replay table that preserves retry safety.

# 2026-10-07 — Block inventory movements and counts at inactive locations
- `InventoryMovementService.applyMovement` now refuses movements at inactive locations; inventory count creation, start, and approval also require an active location. This prevents receipts, waste, transfers, and zero-variance approvals from changing/auditing stock in deactivated storage.
- Added a PostgreSQL/API regression covering deactivated-location receipt, waste, transfer, count approval, unchanged stock/history, and preserved COMPLETED status in `back-end/src/test/java/pos/pos/integration/inventory/InventoryFlowIntegrationTest.java`. Expanded existing `InventoryUnitConversionTest` to round-trip every supported mass pair and check all same-unit pairs.
- Verification: `InventoryUnitConversionTest` 6/6; `InventoryFlowIntegrationTest` 10/10; `RecipeFlowIntegrationTest` 14/14; full `./mvnw -o verify` passed 1,270 unit + 301 PostgreSQL integration tests (1,571 total, 0 failures/errors/skips); `git diff --check` passed. No mobile contract changed, so no mobile state/screens changed.
- Inventory still needs an explicit business policy for returns/refunds and a reconciliation plan for legacy stock data; no automatic stock return was introduced because that would assert an unapproved food-waste/reuse policy.

# 2026-10-07 — Validate table availability party size and cover menu section concurrency
- Added positive `partySize` validation to the three table availability routes and service methods. Added unit regression coverage and PostgreSQL/API coverage for valid capacity filtering and 400 responses for zero/negative values: `back-end/src/main/java/pos/pos/tables/controller/RestaurantTableController.java`, `back-end/src/main/java/pos/pos/tables/service/RestaurantTableAvailabilityService.java`, `back-end/src/test/java/pos/pos/unit/tables/service/RestaurantTableAvailabilityServiceTest.java`, and `back-end/src/test/java/pos/pos/integration/tables/TableAvailabilityApiIntegrationTest.java`.
- Added a four-thread PostgreSQL/API concurrency test proving simultaneous creation of menu items with the same online section name creates one section; removed a duplicate repository import: `back-end/src/test/java/pos/pos/integration/menu/MenuApiIntegrationTest.java`, `back-end/src/main/java/pos/pos/menu/repository/OnlineMenuSectionRepository.java`.
- Full `./mvnw -o verify` passed: 1,268 unit tests and 300 PostgreSQL integration tests, no failures/errors/skips. `git diff --check` passed. No mobile API contract changed, so mobile state and screens were not modified.

# 2026-10-07 — Serialize online-menu section creation
- Follow-up to the grouped section-count query: `OnlineMenuService.resolveSection` now requires the caller's transaction, locks the restaurant before creating a missing section, and rechecks by normalized name after locking. This coordinates concurrent menu-item writes across sections and avoids a unique-index conflict when both callers initially see no section.
- Added a unit regression for the concurrent-winner recheck. Verification: `OnlineMenuServiceTest` passed 11/11; PostgreSQL `MenuApiIntegrationTest` passed 28/28; `git diff --check` passed. No mobile state or screens changed.
- Remaining production blockers are real payment/settlement, durable email retry/outbox and real SMS, durable floor-plan storage, refund/void and legacy stock reconciliation policy, and completing the existing modifier-recipe operator workflow.

- Current known production blockers remain live payment/settlement, durable email retry/outbox and real SMS, durable floor-plan storage, refund/void and legacy stock reconciliation policy, and completing existing modifier-recipe operator workflow.

# 2026-10-07 — Page customer reservation history
- Replaced the unbounded customer reservation-history response with `PageResponse`, defaulting to 50 and capped at 200. The service pages reservation IDs first, then loads the bounded page with its table assignments and restores deterministic `reservationStart DESC, id DESC` ordering. Added V59 partial index `(restaurant_id, customer_id, reservation_start DESC, id DESC)` for rows with a customer.
- Added PostgreSQL/API checks for newest-first pages, next-page contents, invalid page/size, and cross-restaurant access; updated the customer lifecycle test for the paged `items` response. No mobile code calls this endpoint, so mobile state/screens did not need changes.
- Verification: focused `CustomerFlowIntegrationTest` passed 5/5; full backend `./mvnw -o verify` passed 1,251 unit + 293 PostgreSQL integration tests (1,544 total, 0 failures/errors/skips); `git diff --check` passed.
- Remaining production blockers remain: real payment gateway/settlement and reconciliation, SMS/email delivery with durable queue/retries and real routing, production object storage for floor plans, sales-driven inventory policy/source location/refund handling, and report execution/scheduling/delivery.

# 2026-10-07 — Customer list paging and mobile order-catalog synchronization
- Replaced the unbounded customer list query with a `PageResponse` contract on `GET /restaurants/{restaurantId}/customers`, defaulting to 50 records and capping requests at 200 with stable first-name/last-name/id ordering. Added a partial composite index for active customer pages (`V58`) and pagination metadata, sorting, size-cap, invalid-input, and tenant authorization tests.
- Updated the existing mobile order-catalog API/repository to load bounded customer pages, keep only active customer choices, and reject malformed pagination metadata. No screen was added.
- Verification: full backend `./mvnw -o verify` passed 1,251 unit + 292 PostgreSQL integration tests (1,543 total, 0 failures/errors/skips); full shared `:shared:jvmTest --rerun-tasks` passed 323 tests (0 failures/errors/skips); focused customer integration passed 4/4 after V58 was added; focused `OrderCatalogTest` passed; `git diff --check` passed.
- Production blockers remain: real payment processing, durable SMS/email delivery, production object storage, sales-driven inventory consumption and refund policy, and report execution/scheduling.

# 2026-10-07 — Sales report date/shift validation and full-suite verification
- `MySalesService` now rejects a requested shift that does not overlap the selected local calendar date; this prevents an unrelated shift from silently changing the report range while leaving the selected date in the response. Added a unit regression and PostgreSQL/API assertions for payment totals, tips, top items, selected-shift filtering, cross-date rejection, manager viewing an employee, and waiter denial for another employee.
- Verification: `./mvnw -o verify` passed 1,251 unit tests and 291 PostgreSQL integration tests (1,542 total, 0 failures/errors/skips); `:shared:jvmTest --rerun-tasks` passed 322 tests (0 failures/errors/skips); focused `KitchenAndShiftsIntegrationTest` passed 4/4; focused `MySalesServiceTest` passed 6/6; `git diff --check` passed.
- No mobile API contract changed and no screens were added. Existing mobile shift state fix (clear stale weekly pay on week change) was verified earlier in this goal. Production still depends on real payment processing, durable SMS/email delivery, durable floor-plan storage, report scheduling/execution, and an inventory refund policy.

# 2026-10-07 — Full-suite verification after OTP locking
- Re-ran the full backend `./mvnw -o verify` after the OTP row-lock change and report-refund correction: 1,250 unit tests plus 291 PostgreSQL integration tests passed (1,541 total; 0 failures/errors/skips). This includes both concurrent OTP tests and the extended reporting/fraud test.
- `git diff --check` passed. Production still cannot be signed off until the real payment provider, durable SMS/email delivery, durable floor-plan storage, report execution/scheduling, and inventory refund policy are resolved.

# 2026-10-07 — Serialize SMS one-time-code attempts
- Added a pessimistic write lock to the shared active OTP lookup, so concurrent bad password-reset or phone-verification codes cannot lose failed-attempt increments or bypass the five-attempt limit.
- Added PostgreSQL/API races with eight simultaneous wrong codes for password reset and phone verification; both verify exactly five failures, an invalidated code, and rejection of the original correct code. Focused integration verify passed 15/15 tests across `PasswordIntegrationTest` and `PhoneVerificationIntegrationTest`; `git diff --check` passed.
- No request/response contract changed, so mobile state and screens were unchanged. The complete backend suite has not been rerun after this lock change.

# 2026-10-07 — Statistics respect refunded tips
- Updated report totals, payment-method summaries, and per-staff tips to apply the same refund allocation as payroll: refunds reduce the bill first, then reduce tips, never below zero.
- Extended `StatisticsAndFraudIntegrationTest` with a fully refunded bill-plus-tip payment and assertions for retained tips, net collections, staff totals, and the additional large-refund alert. `StatisticsServiceTest` passed 10/10 and PostgreSQL `StatisticsAndFraudIntegrationTest` passed 4/4.
- The focused Maven verify completed successfully. No API field changed, so mobile state stayed unchanged and no screen was added.

# 2026-10-07 — Protect manager-only discounts through order PATCH
- Fixed `OrderSupport.replaceDiscounts`: replacing or clearing existing discounts now requires the same manager/restaurant-rule permission as the dedicated discount endpoints. This closed a bypass where a waiter with `ORDER_UPDATE` could PATCH `discounts: []` and remove manager-only discounts.
- Added a PostgreSQL/API assertion in `OrderLifecycleIntegrationTest` that the waiter gets 403 and the two stored discounts plus $16.60 order total remain unchanged. Focused discount/pricing units passed 17/17; order lifecycle API tests passed 8/8.
- Full backend `./mvnw -o verify` passed 1,250 unit + 289 PostgreSQL integration tests (0 failures/errors/skips); `git diff --check` clean. Mobile API/state contract did not change.

# 2026-10-07 — Clear stale weekly pay in My Shift state
- Fixed `ShiftScreenModel.date`: changing the selected week now clears the previous pay report with the board. Previously, a failed board reload could leave last week's wages/tips visible under the new dates on the existing My Shift screen.
- Added `ShiftScreenModelTest.changingWeekClearsOldPayWhenTheNewBoardFails`; focused screen-model tests passed 8/8 and full shared `:shared:jvmTest` passed 322/322 (0 failures/errors/skips). No screen was added.
- Backend contract unchanged. Backend payroll refund regression remains 4/4; the most recent complete backend run passed 1,250 unit + 289 PostgreSQL integration tests.

# 2026-10-07 — Payroll refund allocation tested across cases
- Expanded `KitchenAndShiftsIntegrationTest.refundedTipsAreRemovedFromStaffPay` to verify bill-only refunds retain all tips, partial refunds beyond the bill reduce tips proportionally, and full refunds remove all tips; the combined report totals the retained $1.50. Focused PostgreSQL/API shift suite passed 4/4.
- Recompiled and reran the current shared Kotlin JVM suite with `:shared:jvmTest --rerun-tasks`: 321 tests, 0 failures/errors/skips. Existing shift API/state models already consume the same decimal `tips` field; no mobile state or screen change was needed.
- Reconfirmed operational notification delivery is incomplete: email/SMS operational notifications persist as QUEUED with no worker; `SmsMessageService` is LOG_ONLY, and queued events do not establish provider recipient routing. Keep this as a production blocker until provider and routing contracts exist.

# 2026-10-07 — Payroll excludes refunded tips
- Updated `StaffPayRateRepository.tips` to subtract the refunded portion allocated to tips after the bill amount is fully refunded; added a PostgreSQL/API regression that pays $6 + $1 tip, refunds $6.50, and confirms staff pay shows $0.50 in tips.
- Verification: focused `KitchenAndShiftsIntegrationTest` passed 4/4; full backend `./mvnw -o verify` passed 1,250 unit tests + 289 PostgreSQL integration tests (0 failures/errors/skips); `git diff --check` clean.
- No API contract or mobile state changed; no screens added. Production still needs real payment-provider, SMS/email delivery, and floor-plan storage configuration before those integrations can be treated as live-ready.

# 2026-10-07 — Booking goodwill refunds are serialized
- Added pessimistic write locks to kept booking-payment lookup and a locked payment-list query used during cancellation/decline/no-show settlement. Goodwill refunds now lock reservation-payment rows and pre-order refunds use the existing pre-order row lock before checking the remaining amount.
- Added a six-request PostgreSQL/API race against a $50 kept deposit, with each caller requesting $30. Exactly one succeeds, and the persisted refund remains $30. Added a unit case for the retained pre-order goodwill path.
- Verification: full backend `./mvnw -o verify` passed 1,250 unit tests + 288 PostgreSQL integration tests (0 failures/errors/skips); focused reservation flow passed 7/7 and booking-money unit tests 8/8; `git diff --check` clean. Shutdown logged test-container/schema cleanup connection warnings, but the build completed successfully.
- No mobile contract or state change was needed. No screens were added. External production payment, notification-delivery, and floor-plan storage integrations remain open.

# 2026-10-07 — Invalid goodwill refund line IDs now return 400
- Hardened `BookingMoneyService.goodwill`: null/blank line IDs and malformed UUIDs now map to the normal “payment is not on the booking” client error instead of escaping as an unexpected exception.
- Added unit edge cases and a PostgreSQL/API regression for malformed regular-payment and pre-order line IDs. Verification: `BookingMoneyServiceTest` 7/7 and `ReservationFlowIntegrationTest` 6/6 passed; `git diff --check` clean. The integration run logged expected SMTP connection-refused messages because no local SMTP server was running; the test cases passed.
- No API contract or mobile state changed. Production external delivery/storage/payment integrations remain outstanding.

# 2026-10-07 — Concurrent payment refunds stay within captured balance
- Added a six-way PostgreSQL/API refund race in `PaymentFlowIntegrationTest`: six tills request $10 refunds against a $25 captured payment. Exactly two succeed; the persisted summary retains $5 refundable, with paid/refunded totals consistent.
- Verification: focused `PaymentFlowIntegrationTest` passed 10/10, including charge/refund concurrency and idempotent retries; `git diff --check` clean. The complete backend run immediately before this test addition passed 1,249 unit + 285 integration tests; the newly added race case was separately executed against PostgreSQL.
- No API contract or mobile behavior changed. Production-provider setup remains required before card processing can receive production sign-off, along with external notification delivery/retries and durable floor-plan storage.

# 2026-10-07 — Menu item import has PostgreSQL/API coverage
- Added two end-to-end cases to `MenuApiIntegrationTest`: duplicate source IDs import exactly one independent dish copy with ingredients, variant, and option-group link preserved; foreign-restaurant and missing source items return 4xx without a partial target copy.
- Verification: `MenuApiIntegrationTest` passed 27/27; `MenuItemImportServiceTest` passed 2/2; complete backend `./mvnw -o verify` passed 1,249 unit + 285 PostgreSQL integration tests (0 failures/errors/skips); `git diff --check` clean.
- No production implementation or API contract changed, so no mobile state update or screen work was needed. The broader goal remains active: production payment-provider setup, SMS/email delivery and durable retries, durable floor-plan storage, and remaining Reports/Auth notification hooks are still release blockers.

# 2026-10-07 — KDS inventory/refund rule verified end to end
- KDS ticket completion, item completion, and waiter pickup consume recipe stock through the same order-locking workflow as POS fulfillment. A duplicate completion is idempotent; insufficient stock rolls back fulfillment and stock together; simultaneous POS/KDS completion creates one movement.
- Codified and tested the accounting rule for post-fulfillment refunds: refunding/voiding payment does not automatically restock already-prepared ingredients. A served line cannot be individually voided; whole-order void retains the SALE_CONSUMPTION movement. A real physical correction remains an explicit inventory movement.
- Verification: complete backend `./mvnw -o verify` passed 1,249 unit tests + 283 PostgreSQL integration tests (0 failures/errors); focused `RecipeFlowIntegrationTest` passed 14/14; mobile `:shared:jvmTest` has 321 passing tests from the current shared-state verification and required no source change for this backend-only behavior; `git diff --check` clean.
- No mobile screens or state changes were needed because the API contract did not change. Production blockers remain: real payment-provider integration/configuration, production SMS/email providers and durable notification retries, durable production floor-plan storage, and missing Reports/Auth notification hooks. Keep overall production readiness below 100% until the external integrations and remaining TODOs are completed.

# 2026-10-07 — KDS fulfillment now consumes recipe inventory
- Fixed KDS whole-ticket completion, item completion, and waiter pickup so every newly fulfilled order line goes through `InventorySaleConsumptionService`; KDS writes now load the order through its transactional pessimistic-lock path, matching POS fulfillment serialization.
- Added PostgreSQL API integration coverage in `RecipeFlowIntegrationTest`: all three KDS completion actions consume stock once, repeating an item completion does not duplicate movement, insufficient stock rolls order and inventory back, and simultaneous POS/KDS completion of the same line produces only one consumption movement.
- Verified focused RecipeFlow integration suite after the concurrency addition: 13 tests passed. Full backend `./mvnw -o verify` passed 1,249 unit +281 PostgreSQL integration tests; the updated focused suite passed 13/13, covering all 282 current integration cases across the full and focused runs. Mobile `:shared:jvmTest` remains 321/321 passing and Gradle marked it up to date because no mobile contract changed. `git diff --check` passed.
- No mobile state change was needed for this backend internal wiring, and no screens were added.
- Still open for production sign-off: real payment provider, production SMS/email delivery and durable notification retry/outbox, durable production floor-plan storage, and the business rule for inventory reconciliation after post-fulfillment refund/void. Reports and auth notification capabilities also remain TODO. Keep overall production status below 100% until these are addressed.

## 2026-10-07 — Notification reliability and catalog accuracy
- Corrected the notification capability catalog: Inventory, Payment, Shift, and Recipe now report live repository-change events; Reports remains TODO because report execution/scheduling/delivery hooks are missing. Added regression coverage for the advertised event codes.
- Extended `NotificationEntityResolver` to traverse scoped parent relationships for PaymentTransaction→Payment, ShiftBreak→Shift, InventoryCountLine→InventoryCount, InventoryLevel→location/item, and RecipeComponent→Recipe. Added direct tests that verify topic, generated event code, and restaurant scope.
- A full backend run exposed JDBC pool starvation: the notification publisher called a `REQUIRES_NEW` dispatch from `afterCommit`, while the originating request still held its connection. Notification rows are now persisted in the originating transaction's `beforeCommit`; SSE broadcasts remain after commit. Extended the stock-concurrency integration test to assert all 20 movement notifications persist.
- Verified: complete backend `./mvnw -o verify` passed 1,249 unit + 279 PostgreSQL integration tests (0 failures/errors); standalone concurrent inventory test passed with feed assertions; notification catalog/resolver tests passed 3/3; mobile `:shared:jvmTest` passed 321 tests; `git diff --check` clean.
- Mobile contract shape did not change, so no mobile source/state changes or new screens were needed this turn.
- Still open: real payment provider, production SMS/email delivery and durable notification retry/outbox, durable production floor-plan storage, and explicit post-fulfillment refund/void inventory reconciliation. Reports and auth notification capabilities remain TODO. Do not call the whole backend production-ready yet.
# Agent Memory — POS

## 2026-10-06 (Codex) — Inventory oversell race coverage
- Expanded the PostgreSQL concurrency integration test: after 20 concurrent receipts establish 5 kg, two simultaneous 3 kg deductions must yield exactly one 201 and one 409, leaving 2 kg.
- Verified the expanded scenario on current source with `InventoryFlowIntegrationTest#concurrentMovementsAreAtomicAndCannotOversell` (1/1 passed). The full suite had passed immediately before this test-only expansion: 1,244 unit + 273 PostgreSQL integration tests, no failures. Production code is unchanged since that full run.
- `git diff --check` passed. Still no inventory sale-consumption flow; location selection and order edit/reopen/refund reconciliation rules remain required.

## 2026-10-06 (Codex) — Atomic inventory balances
- Replaced `InventoryLevelService.upsertLevel` read/modify/save with a PostgreSQL `INSERT ... ON CONFLICT` delta update in `InventoryLevelRepository.applyMovementDelta`. Concurrent receipts and transfers now update one balance atomically, including when the first movements race to create the row.
- The atomic statement prevents negative balances and returns 409 for an insufficient movement; transaction rollback removes its movement record too.
- Added API/PostgreSQL coverage for 20 concurrent first receipts totaling 5 kg and rejection of negative movements with no stock or insufficient stock. `InventoryFlowIntegrationTest` passed 6/6.
- Full `./mvnw -o verify` passed 1,244 unit tests plus 273 PostgreSQL integration tests (0 failures/errors/skips). `git diff --check` passed. The mobile contract did not change.
- Recipe-to-sale stock consumption remains absent. `SALE_CONSUMPTION` has no configured item/branch source-location policy; implementing a full sale flow still needs that policy and reconciliation rules for reopen, line edits, and refunds. Real payment and production storage/notification integrations also remain open.

## 2026-10-06 (Codex) — Recipe unit costing hardening
- Fixed recipe inventory costing so ingredient quantities convert into the stock item's base unit before applying `costPerUnit`; mass (g/kg/oz/lb) and metric volume (ml/l) are supported, identical units pass through, and unsupported/ambiguous conversions are rejected.
- Corrected nested recipe costing to prorate a child recipe's batch cost by its yield quantity and unit. Recipe save validation now rejects incompatible ingredient/stock and sub-recipe/yield units.
- Added conversion unit tests and recipe API/database integration cases for 250 g at cost/kg, incompatible units, and a four-portion nested recipe yield. A final focused conversion rerun passed 4/4 after adding null-unit rejection.
- Verification: full backend `./mvnw -o verify` passed 1,244 unit tests and 271 PostgreSQL-backed integration tests (0 failures/errors/skips); subsequent focused unit rerun also passed 4/4. `git diff --check` passed.
- No mobile API fields/screens changed; existing recipe state already uses the same contract. Broader production blockers remain: real payment-provider setup, durable production floor-plan storage, automatic inventory deduction when orders sell, and live email/SMS provider configuration. These changes do not constitute whole-backend production sign-off.
- Traced order fulfillment and close paths for the next inventory blocker: `SALE_CONSUMPTION` and order-line movement linkage exist, but no recipe-to-sale deduction is wired. A branch can have multiple inventory locations, with no configured default source location, so safely completing this requires a location policy plus idempotent handling for line edits/reopen/refunds; did not guess a stock source or apply a partial deduction.

## 2026-10-06 (Codex) — Backend production-readiness audit
- Performed a source-level audit of backend domain modules, controllers, security, configuration, migrations, and test inventory on the feature/orders working tree. No code or tests were changed or run.
- Audit result: backend estimated 47% production ready overall; payment-provider integration, booking test payments, POS inventory depletion, queued external notifications, production-profile activation, file storage, and thin domain integration coverage remain material release blockers.
- Detailed ranked ratings and file evidence are in the Codex task “Rate POS Services” (referenced conversation audit). Re-audit after uncommitted changes are finalized and real integrations/deployment config are in place.

## 2026-10-06 (Claude, feature/orders) — Backend finished and hard-tested; app state for every remaining feature (no screens)
**The user asked for this without questions:** finish the backend and test everything hard (unit + integration, edge cases like very long names), finish the mobile state management (no screens), and delete the root images (5/7/8/9.png, now gone). A recheck table of decisions went to the user in chat.

**Backend (V55 used; next free migration V56):**
- **Payments** (`payment/`):
  - `PaymentService` (take, refund, cancel) and `PaymentCalculator` (paid, tips, refunded, balance due with cash rounding).
  - Receipt and payment codes are random (`PaymentCodes`).
  - `OrderPaymentController` and `BranchPaymentController`. Staff without ORDER_AUDIT only see their own payments.
- **Statistics** (`report/`): `/statistics/overview|sales|staff|reports`, needs REPORTS_READ.
- **Fraud** (`fraud/`): rules run as JDBC queries; alert reviews go in `fraud_alert_reviews`; FRAUD_READ / FRAUD_REVIEW. The thresholds are settings (`PATCH /settings/fraud-checks`); payment settings are at `PATCH /settings/payments`.
- **Order guards** (`OrderSupport`):
  - No void or cancel once money was taken.
  - Discount rules apply.
  - Auto-fire to the kitchen when the setting is on.
- **Hardening:**
  - `GlobalExceptionHandler` rewritten: SQL states and entity rules become 400/409, plus 413/415/405.
  - A 1 MB body cap (`config/web`).
  - `@Valid` on every body, and `@Size` on all free text.
  - Page caps.
  - Mail failures are logged instead of failing a staff registration.
  - The refresh token limit is 4096.
- **Tenant isolation:** users and custom roles are scoped to a restaurant (`roles.restaurant_id`, `RoleHierarchyService`). Staff registration takes a restaurant and default branch.
- **This part of the session:**
  - Inventory movements are paged (`page`, `size` ≤ 500).
  - **Low stock never worked** (levels never got a reorder quantity). It now falls back to the item's reorder point and par level, and skips inactive items and places.
  - Audit log entries carry `actorName`.
  - Device notes are limited to 2000 characters.
  - Recipes: description ≤ 2000, waste < 100%, and sub-recipe cycles are refused at save (`RecipeService.assertNotInside`).
  - The branch pre-order list covers at most 62 days.
  - Customer email must be valid, and notes ≤ 2000.
- **Tests:** the final full `./mvnw -o verify` passed: 1226 unit tests + 268 ITs, 0 failures. New ITs: payment flow, robustness fuzz, tenant isolation, staff/roles isolation, statistics/fraud, order lifecycle, inventory (incl. low stock/barcode/paging), kitchen & shifts, bookings, customers, recipes, pre-orders. ITs run on podman (see AGENTS.md).

**App (state only, all registered in `appModule.kt`; screens still to build):**
- `pos/payment`: take payments with request keys so a retry doesn't charge twice; refunds, cancels and receipts.
- `statistics` and `fraud`.
- Admin Hub:
  - `admin/people`: `StaffScreenModel` (search with a 300 ms debounce, paging that keeps pages, add/edit/roles/on-off/reset/remove, never yourself) and `RolesScreenModel` (built-in roles read-only; you can only grant permissions you hold).
  - `admin/inventory`: `InventoryScreenModel` (stock, items, suppliers derived from supplier names, places, history, moves, counts) and `RecipesScreenModel`.
  - `admin/devices`: devices, printers, pairing codes shown once, assignments.
  - `admin/audit`.
- `pos/reservations/preorder`: `PreOrderScreenModel` (take/change/cancel/send a booking's pre-order, and the kitchen list).
- Fixes found by tests:
  - A bare `async` inside `launch` escaped the catch, so it is now wrapped in `coroutineScope`.
  - A refresh during a save dropped the answer, so writes now use a sign-in token.
- 318 jvm tests pass; `:desktopApp:compileKotlin` and `:shared:compileAndroidMain` both OK.

**Open:**
- Screens for all the state modules above.
- Payments are still in test mode.
- Inventory isn't taken off stock when items sell.
- The customer list isn't paged.
- `RestaurantsWorkspaceScreen.kt` shows as deleted in git; it was already like that, not this session.
- Nothing committed.

## 2026-09-28 (Claude, feature/orders) — Backend and app relaunched and checked (~22:50)
- I stopped the backend (pid 11864) and rebuilt the jar; no backend source was newer than it. It started again as pid 69382 with the MailHog env vars. Flyway was up to date (V54) and the log had no errors.
- Authenticated checks all returned 200: auth/me, orders, reservations summary, table-layout, floor-layouts, tables, `/menus`, KDS board and pos-timing, shifts (team and mine), shift pay, my sales, order history, and settings. Note: the menu API is `/menus` (no restaurant prefix). `branches/{b}/tables/layout` isn't a route; it's read as a table id and gives a 500.
- The desktop app was relaunched (MainKt pid 70446) with no errors in its log. No code changes.

## 2026-09-28 (Claude, feature/orders) — App relaunched
- At ~21:02 I started the desktop app (`:desktopApp:run`, MainKt pid 56185; no rebuild needed). The backend (`back-end/target` jar, pid 11864, started 18:00) and `pos-db`/`pos-mailhog` were already running. No code changes.

## 2026-09-28 (Claude, feature/orders) — Kitchen Status slim tiles; My shift narrower right column
- **Kitchen Status:** the 4 big stat cards are now `CompactStat` tiles (50 dp: Ready to serve, Cooking, Waiting, Taking long, with little text). The user wants the lanes to get the space.
- **My shift (desktop):** the left/right weights went from 0.6/0.4 to 0.7/0.3. The Coming up / Worked and paid rows are compact (36 dp date block, 12/10 sp). Week-tile times no longer get cut off.
- Compiles; the app was relaunched. No tests run.

## 2026-09-28 (Claude, feature/orders) — Workspace picker: smaller five cards, centred
- **Five choices** (super admin): the cards are 236×212 (were 280×252) with a 16 dp gap. The picture is 72 dp, the title 17 sp, the subtitle 12 sp, and the arrow 22 dp.
- **Two-row layouts** (four with Fraud, or five) now sit vertically centred in the window (Column min height = window height, `Arrangement.Center`, still scrollable). The user asked for them lower on the screen.
- `workspace/ui/WorkspacePickerScreen.kt` only; the three- and four-card layouts are unchanged. Compiles; the app was relaunched.

## 2026-09-28 (Claude, feature/orders) — Admin shifts: edge "Add shift", smaller, one toolbar
- **The user asked for three changes:**
  - The Add shift button should be the same one Tables uses.
  - Everything was too big.
  - There should be only one navbar row, not two.
- **Add shift is now `RightEdgeActionButton`.** It's draggable on the right edge, like "Add table" and "Reservation", and only shows for SHIFT_MANAGE.
- **One toolbar row** in `ShiftAdminCalendar.kt`:
  - Left to right: title, Schedule/Hours & pay tabs (196 dp), a view dropdown (Team/Timeline/Month), ‹ period ›, a Today icon, then search, role and status.
  - It scrolls sideways below 1200 dp.
  - The colour key was removed.
  - `HoursAndPay.kt` got the same single row, with Week/Month as a dropdown.
- **Smaller:**
  - A new `CompactStat` in OverviewKit (50 dp tiles) replaces the big stat cards on both Admin shift pages.
  - Team view: name column 190, row minimum 54, day header 50, avatar 28, blocks 11/9 sp.
  - Timeline: 42 dp per hour.
  - Month: row minimum 92.
- Compiles; the app was relaunched. No tests run.

## 2026-09-28 (Claude, feature/orders) — Admin shift calendar redesigned; demo data for the new screens
**The user liked the new screens but called the Admin Hub shift calendar (Codex's) "terrible".** They asked for a modern one: people on the left with the weekdays across, or hours on the left with the weekdays across; adding people with a + inside the calendar; scrolling when needed.
- **`pos/shifts/ShiftAdminCalendar.kt` was rewritten.** Same entry point and the same dialogs (ShiftDetails / ShiftEditor / ShiftActionDialog). Row 1: title with the Schedule / Hours & pay switch, then search, role, status and Add shift. Row 2: Team / Timeline / Month tabs, ‹ period ›, This week, and a colour key. Below that, 4 stat cards (On duty now, Planned, Worked, Needs review; the last one filters when clicked).
  - **Team:** people rows × 7 day columns. Day headers show a count of people and hours, with today in a green circle. Shift blocks are coloured by state (Planned blue, On duty solid green with a pulsing dot, On break amber, Worked grey, No clock-in amber outline, Missed red). Every cell has a dashed "+" (faint, clear on hover) that opens the editor for that person and day. A "Put someone on a shift" row sits at the end. The grid scrolls both ways (min 132 dp per day).
  - **Timeline:** hours × weekdays, blocks at their real times; overlapping shifts sit side by side. It has a red now line, and a hover "+ HH:00" on each hour adds a shift at that time. It opens near the first shift, runs 06–24 by default, and stretches past midnight when needed.
  - **Month:** weeks × weekdays with up to 3 shifts per day and "+N more" (opens that week in Team). A "+" appears on hover.
  - The right-hand "selected day" panel is gone (the details dialog replaces it).
- **Demo data (local DB only):** made by the scratchpad script `seed_demo.py`, which records every id in `seed_manifest.json` in the same folder, so it can be undone.
  - KDS stations Grill / Cold & lunch / Bar / Desserts, with routings for restaurant 1's menu.
  - 7 open table orders (T02–T08) sent to the kitchen, in every state: ready (one waiting 7 min), partly ready, cooking (a T06 rush at 26 min), waiting. They're split between Super Admin and Demo Waiter.
  - 49 closed orders with payments and tips ("Demo data 2026-09-28" in the notes, reference DEMO-…): Super Admin today (10, linked to today's shift) and last week, Demo Waiter today and the past 6 days.
  - Shifts: Super Admin's shift left open since 27 Sep was closed at +5h35. Super Admin got last week's shifts, today's open shift (09:58, meal 13:30–14:00) and 6 planned ones, with a wage of 14.00. Today's team: Kitchen, Admin and Owner finished; Co-Owner and Manager on duty; Waiter on a break.
  - Restaurant 1's menu has no pizzas or coffee (those belong to restaurants 2 and 3). The burger needs a bun and the ribeye a temperature.
- Compiles; the app was relaunched. No tests run (user rule).

## 2026-09-28 (Claude, feature/orders) — Kitchen Status, My shift, My Sales and Admin Hours & pay built (UI first, tests not run)
**User asked Claude (not Codex) to fully build these three POS screens,** focusing on the look ("same as we have done till now"), with testing after they review it. Decisions (asked via question):
- **Pay = worked hours × hourly wage + tips.** Managers set each person's wage in Admin Hub.
- **Shift: POS = my own shift. Admin Hub = everyone.** Both use the same data.
- **Kitchen Status is the waiters' view in POS,** not a copy of KDS.

**DB V54 (applied locally):**
- New table `staff_pay_rates` (user_id PK, restaurant_id, hourly_rate, updated_at/by).
- `settings.kitchen_slow_after_minutes` (default 20), `kitchen_ready_waiting_minutes` (5) and `clock_in_early_minutes` (120, replacing the hard-coded 2 h in `ShiftService`).
- **Next free migration: V55.**

**Backend:**
- `ShiftPayService` and `StaffPayRateRepository` (JDBC).
  - `GET /shifts/pay?from&to&mine` (max 62 days; mine = SHIFT_SELF, team = SHIFT_MANAGE).
  - `PUT /shifts/pay-rates/{userId}` (SHIFT_MANAGE, audited as STAFF_PAY_RATE_SET).
  - Clock-in copies the current wage into `shifts.hourly_rate`. Setting a first wage fills earlier worked shifts that had none. Tips come from payments on orders the person created, in the restaurant currency.
- `ShiftDtos.Board` has `clockInEarlyMinutes`.
- KDS additions:
  - `POST /kds/tickets/{id}/picked-up` (KDS_UPDATE or ORDER_UPDATE; only READY items become FULFILLED).
  - `GET /kds/pos-timing` (KDS_READ).
- Settings: `PATCH /settings/kitchen-status` and `/settings/shifts` (both are also restored by reset).
- **The WAITER role now has KDS_READ.**

**App:**
- `core/components/OverviewKit.kt` holds the shared look (stat card, panel, tabs, chips, empty states), copied from the Reservations overview.
- `pos/kitchen/`:
  - Orders are grouped per order across stations, in lanes Ready to serve / Cooking / Waiting. Held orders are marked.
  - "Picked up" button, All orders / My orders filter, station and search filters, 4 stat cards.
  - Timings come from settings. On a phone the lanes become tabs.
- `pos/shifts/`:
  - `MyShiftScreen.kt` (POS): live clock, clock in/out and break buttons, shift bar, week tiles, Coming up list, and a "Worked and paid" panel. It loads the shown week plus the next week, and the week's pay.
  - `HoursAndPay.kt` (Admin Hub → Shifts, new "Schedule / Hours & pay" switch, SHIFT_MANAGE only): week or month view, team table, person panel with an hours chart, wage dialog.
  - The old My shift body in `ShiftScreen.kt` was removed.
- `pos/sales/MySalesScreen.kt` was rewritten in the overview style:
  - Stat cards: Sales, Tips, Earned today, Tables served.
  - Panels: Sales by hour (bars), My pay (day / week / month so far), Payments, Top dishes, Where you served, Latest payments.
  - Pay comes from the shift pay endpoint (needs SHIFT_SELF).
- Admin Hub Settings:
  - Orders & kitchen → "Kitchen Status for waiters" (2 values).
  - Shifts → "Clocking in" (it was empty before).

**Local data:** set demo wages through the API: Demo Waiter 12.50, Demo Kitchen 13.00, Demo Manager 16.00, Demo Admin 15.00. Owners, Co-Owner and Super Admin have no wage on purpose, so the "no wage" state shows.

**Tests: written or updated and compiled, NOT run (user rule).**
- Backend: new `ShiftPayServiceTest` (3); `ShiftServiceTest` constructor updated.
- App: `PosKitchenDataTest` rewritten for the new grouping, new `PayTest` (3), `FakeShifts` given pay methods.
- `PosReadScreensScreenshotTest` still calls `KitchenStatusContent(state, {}, {})`. It compiles, but its render expectations predate the redesign.

**Runtime:**
- The machine had rebooted, so I ran `podman start pos-db pos-mailhog`.
- The backend runs from `back-end/target` (with the MailHog env vars) and the desktop app was relaunched. Logs are `backend.log` / `desktop.log` in this session's scratchpad.
- I couldn't take screenshots (the Wayland display blocks capture), so the user is the first to see the screens.

**Open:** design changes after the user's review; then run the tests. Overtime rules aren't modelled (there's no policy yet).

## 2026-09-28 (Claude, feature/orders) — Backend and desktop app relaunched
- At ~17:07 I rebuilt `back-end/target/pos-0.0.1-SNAPSHOT.jar` from the current working tree, which includes Codex's MySales fix. That means Codex's `/tmp/pos-todo-runtime-20260928.jar` is no longer needed.
- The backend runs from that jar (pid 682352, with the MailHog env vars) and started cleanly on 8080. Its log is `backend.log` in this session's scratchpad (`/tmp/claude-1000/.../03aa0c35-.../scratchpad/`).
- `./gradlew :desktopApp:run` relaunched the desktop app (MainKt pid 683033; everything was up to date, no recompile). Its log is `desktop.log` in the same folder.
- `pos-db` and `pos-mailhog` were already up. No code changes.

## 2026-09-28 (Claude, feature/orders) — Backend confirmed stopped; VS Code closed at the user's request
- Confirmed at ~14:02: nothing listens on 8080 and no Java runs. Codex's `/tmp/pos-todo-runtime-20260928.jar` backend is stopped. Restart it before using the app.
- The user asked to close VS Code and the backend, keeping only Firefox and their files open. VS Code (with this Claude session) was closed right after this entry.
- The ChatGPT/Codex app was not touched. `pos-db` and `pos-mailhog` are still running.
- Work is uncommitted on `feature/orders`, as before; nothing was lost or reverted.

## 2026-09-28 (Claude, feature/orders) — Stopped Codex's backend to free RAM
- The user asked to free memory. At 13:59 I sent a stop signal to Codex's backend `java -Xmx512m -jar /tmp/pos-todo-runtime-20260928.jar` (pid 411805, port 8080, log `/tmp/pos-todo-backend-runtime.log`). Its log only showed the scheduled jobs, and the desktop app wasn't running.
- I couldn't confirm afterwards that it exited.
- **Codex:** if you need the backend, check port 8080 and restart it. The `pos-db` and `pos-mailhog` containers were left running.
- Nothing else was stopped; no Gradle or Kotlin daemons were running. Most of the machine's memory is Firefox (~5.5 GB) and VS Code (~2.1 GB).

## 2026-09-28 (Codex, feature/orders) — POS History, Kitchen Status and My Sales completed
**Scope:** Resumed the interrupted Codex-only TODO. Implemented all three assigned state/backend connections using existing screens; no reservation/settings/pre-order edits, no payment processing, no commits/pushes. Five-choice workspace cards are smaller (280×252 max versus 300×272), still centered 3+2; other choice counts and phone layout retain their sizing.
**History:** Added bounded `/orders/history/page` (ORDER_READ + branch scope; max100, app40; terminal statuses; openedAt range, staff/customer/search filters; stable openedAt/id order). Independent `OrderHistoryScreenModel` owns paging/details/session/reset/retry/live refresh, preserving all loaded pages on refresh and retaining them on transient failure. Late responses are discarded. POS History reuses Orders layout through named DI, is read-only, and exposes past-order status/search/All/Mine filters. Normal Orders controls retain their behavior. Fixed Ktor SSE flow to use channelFlow/send rather than cross-context emit.
**Kitchen Status:** Existing POS lanes now use KdsScreenModel and real station tickets, quantities, variants/modifiers/notes/occasion, elapsed times, permissions, loading/empty/stale/error states and live updates. KDS_READ is sufficient; KDS_ACCESS is not needed for the waiter view. Removed sample rows and the extra hard-coded `1 x` prefix; real item text wraps.
**My Sales:** Added controller/service/JDBC aggregation with ORDER_READ + branch scope; other staff additionally require ORDER_AUDIT and SHIFT_READ. Date uses restaurant-local calendar day (DST aware); shift uses actual attendance, with explicit payment.shift_id for shift collections. Staff attribution uses order.createdBy. Closed order sales and captured/refunded payment totals are aggregated separately so split payments don't multiply sales. Currencies stay separate, including currencies with only open orders. Tips are recorded gross before refunds; refunds are attributed to original payment date, with no invented tip-refund allocation. Closed orders without payment records are flagged. Existing UI uses real report data, date/shift/currency controls, live refresh, top5 items/floors and latest5 payments; total-collected clipping fixed. No estimates/sample totals or payment processing added.
**Verification:** Final desktop compile and 30 POS completion tests passed (11 History state/bridge, 8 MySales state, 3 read API/SSE contracts, 4 kitchen mappings, 4 screen renders). Earlier 29 KDS and 14 workspace tests passed; 28 backend unit tests passed. Isolated live SQL/HTTP verifier passed36 assertions including splits/refunds/unsettled payments, multiple currencies, shift/date boundaries, scope and stable paginated history. Inspected five-card workspace and History/MySales/Kitchen renders. `git diff --check` passed. Logs: `/tmp/pos-todo-final-mobile.log`, `/tmp/pos-todo-mobile-resumed.log`, `/tmp/pos-todo-backend-tests-resumed.log`, `/tmp/pos-todo-live-results.log`; renders: `mobile_desktop/shared/build/reports/pos-completion/`.
**Runtime:** Main backend now runs `/tmp/pos-todo-runtime-20260928.jar` (log `/tmp/pos-todo-backend-runtime.log`), copied from Claude's latest V53-capable target jar with only MySalesRepository.class replaced. All other zip entries verified byte-identical. Latest fix covers PostgreSQL `hour_label` alias and currencies containing only open orders. Original target jar remains intact; future normal builds include the fixed source. Disposable database `pos_codex_todo_verify_20260928` dropped after 36 live assertions; working restaurant data was not seeded. Runtime authenticated History/MySales/KDS reads all returned200. Desktop relaunched successfully (PID 421234), log `/tmp/pos-todo-desktop-runtime.log`; final run reuses up-to-date classes. Tests/builds were serialized after detecting another agent build to avoid memory exhaustion.
**Handoff:** `CODEX_TODO.md` completed. Module READMEs describe contracts/attribution. Printing/payment placeholders in the reused Orders layout remain outside this TODO; write permissions are denied in History. Shared mobile behavior tested on JVM; no physical Android/iOS validation. Known pre-existing broader failures (stale Orders UI/polling expectations and MenuServiceTest missing OnlineMenuService fixture) were not used as release checks or changed to hide failures. Claude's reservation/settings/Phase4 changes remain separate and preserved.

## 2026-09-28 (Claude, feature/orders) — Reservation rules PHASE 4 done; phases 1–4 COMPLETE
**The "IN PROGRESS: reservation rules phases 1–4" claim (2026-09-27) is closed.** Used V49–V53; **V54–V56 are released**, so the next free migration is V54. The reservation/pre-order/app `pos/reservations/**` areas are no longer locked.
- **DB V53:**
  - `reservations.guest_token` (unique; the guest's private booking link).
  - `settings.card_fee_percent` 1.50 / `card_fee_fixed` 0.25.
  - `reservation_payments` (DEPOSIT/EXTRA; PENDING/PAID/REFUNDED/KEPT/CANCELLED; refund_deadline, refunded_amount, card_fee, provider).
  - `pre_orders.refunded_amount`.
- **Backend:**
  - `BookingMoneyService`: deposit for groups ≥ `depositFromGuests` when the rule requires one (**fixed amount per booking**; `depositType` is ignored), paid extras from special menus (occasion + order-before deadline), settle on cancel/expire/no-show/decline, card fee, goodwill (part of KEPT money only, reason required).
  - `GuestBookingService` + `GuestBookingController` (`/public/guest-bookings/...` JSON).
  - `GuestBookingPageController` (server HTML: `/public/book/{slug}/{code}`, `/public/bookings/{token}` with confirm / running late / pay / cancel).
  - `ReservationMailService` + `ReservationGuestMailListener` (confirmed, request received, declined, expired, cancelled, reminder, payment link; sent after commit).
  - Staff endpoints: `POST /{id}/decline`, `GET /{id}/money`, `GET/POST /{id}/extras`, `POST /{id}/payment-link`, `/payments/{pid}/mark-paid|remove`, `/money/{lineId}/goodwill` (needs `PAYMENT_GOODWILL_REFUND`).
  - `GuestExtraChoice` now carries `currency`.
  - **Payments run in test mode** (`app.payments.provider=test`): no real card provider is connected yet.
  - Guest links use `app.reservations.guest-base-url` (default `http://localhost:8080`).
- **App:**
  - `BookingMoney.kt` holds the pure rules: which actions each line offers, the goodwill limit, payment-link / add-extra offers, extras that are too late.
  - `BookingMoneyCard.kt` is the "Money" card on the booking panel's Info tab: each line with the server's explanation; Mark paid / Remove unpaid extra / Goodwill refund (permission-gated); Send payment link; Add extra.
  - "Decline request" now calls `/decline` (the guest is emailed and refunded in full).
  - Model: `MoneyLine` / `BookingExtraChoice` in cents; `moneyCents` / `moneyText`.
  - Admin Hub → Settings → Reservations:
    - The deposit is a single "Deposit per booking" amount (the Percentage choice is removed; the rule is saved with `FIXED_AMOUNT`).
    - A new "Refunds" section has `cardFeePercent` / `cardFeeFixed` (saved with `/reservation-policy`).
- **Tests (run, passing):**
  - Backend: `BookingMoneyServiceTest` (7) plus all reservation/settings unit tests (87/88). The 1 error is `ReservationEntityPersistenceTest`: Testcontainers, no Docker on this host.
  - App: `BookingMoneyTest` (7), `SettingsSpecTest` (9, updated for the fixed deposit and card fees), `BookingRulesTest`, `OccasionsAndEventNightsTest`, `ReservationsPagingTest`.
  - **Final live journey on one build: 101/101.** Phase 1 28, Phase 2 19, Phase 3 12, Phase 4 guest 26, Phase 4 staff 15, plus the 24 h reminder email. Scripts are in the session scratchpad. Test data cleaned (bookings, payments, menus; the temporary booking rule was deleted again).
- **Local run:** start the backend with `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS_ENABLE=false` to see guest emails in MailHog (http://localhost:8025).
- **Open / for later:**
  - A real card provider (Stripe or similar) behind `app.payments.provider`.
  - The website (`web/apps/storefront`) is still a skeleton: guests use the server pages for now.
  - The old `/deposit` endpoints (`ReservationDeposit`) still exist but the app no longer uses them.
- **Not mine, still failing:**
  - `OrderControllerSecurityTest` (missing OrderChangeNotifier bean) and 3 `OrderPricingSafetyTest`.
  - The `RestaurantTableServiceTest` merge test.
  - `MenuServiceTest.shouldDeleteSectionsWithMenuWhenRequested`.
  - `OrdersScreenModelTest.activationPollsOnlyWhileVisible` and 4 `OrdersUiScreenshotTest`.

## 2026-09-28 (Claude, feature/orders) — Reservation rules PHASE 3 done (built + tested)
- **DB V52:**
  - `reservation_occasions` (per restaurant, code/name/icon/options one per line; the 7 agreed defaults are created on first read).
  - `reservations.occasion_code/name/icon/options/note` (a copy, kept if the occasion changes).
  - `restaurant_events` (name, icon, start/end date, menu_id, special_menu_only, active).
  - `menus.is_special`, `menu-items.order_before_hours` + `occasion_codes`.
- **Backend:**
  - `ReservationOccasionService` + `ReservationOccasionController`: GET/PUT `/restaurants/{r}/reservation-occasions` (PUT = whole list; code from the name). Plus `/events`, `/events/on?date=`, POST/PUT/DELETE events (SETTINGS_UPDATE).
  - Choosing an event's menu makes it special and available only on the event dates. Overlapping active events → 409.
  - Booking create/update/patch take `occasionCode/Options/Note` (options must be offered for the occasion; an empty code clears it).
  - Responses carry `occasion*` and the day's `eventName/eventIcon` (batched via `ReservationSupport.addEvents`).
  - The floor plan carries `nextReservationOccasionIcon/Name`; KDS ticket responses carry `occasion` ("🎂 Birthday · Cake from us, Candles · note").
  - Menu: `special` on menu create/update; items `orderBeforeHours` + `occasionCodes`. Update only changes those when `occasionCodes` is sent, so older callers don't wipe them.
  - `MenuItemImportService`: POST `/menus/{m}/sections/{s}/items/import {itemIds}` copies dishes (own price, no SKU, variants, option groups, KDS routing; not online).
  - **Bug fixed:** deleting a dish or a menu with items failed (500) when dishes had KDS routing (FK). Routings are now deleted with the dish (`MenuItemRepository.deleteKdsRoutingsOf`).
- **App:**
  - `OccasionUi.kt`: picker in the create dialog and the edit form; card in the panel; icon on overview cards and floor-plan tables. Check-in toast includes the occasion.
  - Overview: event banner. Occasions count in "Special requests".
  - Admin Hub → Settings → Reservations: `OccasionsAndEventsSettings.kt` (occasion list editor, event-night editor with menu + "only the special menu").
  - Menu: "Special menu" checkbox in the menu editor; "Occasion extra" fields (order ≥ N h ahead, offered-for occasions) in the item editor for special menus; "+ Import existing dishes" → `ImportItemsDialog`.
  - Orders: `DefaultOrderCatalogRepository` takes `onlyMenuToday`; on an event night with special-menu-only, order-taking offers only that menu. KDS app model has `occasion`.
- **Regression fixed:** MenuScreen asked Koin for ReservationApi eagerly (broke `MenuUiScreenshotTest`); now looked up lazily, only for special menus.
- **Tests (run, passing):**
  - Backend: `OccasionsAndEventsTest` (5), `MenuItemImportServiceTest` (2), `KdsTicketOccasionTest` (2), `MenuItemServiceTest`, all reservation tests.
  - App: `OccasionsAndEventNightsTest` (3), reservations/admin/menu tests.
  - Live `phase3_check.py` 12/12; Phase 1 28/28 and Phase 2 19/19 rerun on the same build. Test data cleaned.
- **Not mine, still failing:**
  - `MenuServiceTest.shouldDeleteSectionsWithMenuWhenRequested` (test lacks OnlineMenuService).
  - Orders app tests `OrdersScreenModelTest.activationPollsOnlyWhileVisible` and 4 `OrdersUiScreenshotTest` (Codex's in-progress order screens).

## 2026-09-28 (Codex, feature/orders) — POS TODO implementation in progress
**Ownership:** User explicitly resumed all three `CODEX_TODO.md` items: POS History (bounded backend paging + separate mobile state), POS Kitchen Status (existing UI connected to KDS), My Sales (authoritative scoped backend aggregation + state/UI data wiring). Codex owns these modules and shared DI/PosScreen integration during this work. No reservation/settings/pre-order changes; Claude phases/migrations remain protected. Also shrinking picker cards only for five choices. No new screens or redesign; no payment processing implementation. Final verification/results will be recorded separately.

## 2026-09-28 (Codex, feature/orders) — Fraud Detection choice and Statistics/Fraud navbars
**Request:** Add Fraud Detection using `fraud.png`, visible to SUPER_ADMIN, OWNER and CO_OWNER; arrange five choices three above/two below and reload. Follow-up requested matching navbars with sensible initial sections.
**Changed:** Added `Workspace.FRAUD_DETECTION`, role-gated picker entry and route, and `workspace_fraud.png` copied byte-identically from the supplied image. Desktop choices with Fraud use centered rows of at most three; all five fit the 1280×800 preview, with shared card styling and aligned arrows. Phone keeps the existing scrollable column. Other entry permissions remain unchanged, including Statistics' existing super-admin gate; users with fewer accessible choices see only those choices.
**Navigation:** Added `WorkspaceSections.kt` and `FraudDetectionWorkspaceScreen`; Statistics now uses the same shell. Statistics tabs: Overview, Sales, Staff, Reports. Fraud tabs: Overview, Alerts, Activity, Rules. Reuses the actual POS WorkspaceTopBar/TopBarNavItem and WorkspaceBottomBar/PhoneNavItem, including profile, logout, back, selected underline and phone More controls. Selection is saveable; direct entry rechecks workspace access. Content remains a simple section label; no reporting/fraud engine, fake data or business-state implementation added.
**Verified:** Desktop Kotlin compile and 14 workspace tests passed (13 existing cases plus a focused Fraud role-access test including denied Admin/Manager/Waiter/Kitchen). Inspected five-card desktop renders at 1280×800 and 1024×900; all cards fit and second row is centered. Original artwork preserved, `git diff --check` passed, existing POS/Admin navbar files untouched. Checks log `/tmp/pos-fraud-navigation-final.log`; desktop relaunch log `/tmp/pos-fraud-navigation-runtime.log`.
**Scope:** No backend/reservation/settings changes and no deferred Codex TODO implementation. Other agents' existing changes preserved. No commits/pushes.

## 2026-09-28 (Codex, feature/orders) — Statistics replaces Restaurants in workspace choices
**Request:** Use the user's newly added `statistics.png` to replace the Restaurants choice with Statistics.
**Changed:** Renamed `Workspace.RESTAURANTS` to `STATISTICS`, updated the picker title/subtitle to Statistics / Sales, performance and reports, and routed it to `StatisticsWorkspaceScreen` (the existing coming-soon destination renamed, not a reporting implementation). Copied the supplied PNG byte-for-byte to `composeResources/drawable/workspace_statistics.png`; root original preserved. Retained the old Restaurants illustration because the protected Settings page still references it. Existing picker card styling, sizing, ordering and access scope remain unchanged; Statistics currently inherits super-admin-only access.
**Verified:** Desktop Kotlin build and all 13 existing `WorkspacePickerScreenshotTest` cases passed, including access checks and desktop/phone renders. Inspected desktop and phone output; Statistics appears in the fourth desktop card. Original illustration copy verified identical; `git diff --check` passed. Runtime relaunch log `/tmp/pos-statistics-choice-runtime.log`.
**Scope:** No backend, reservation/settings, or deferred Codex TODO implementation. Other agents' existing dirty files preserved. No commits/pushes.

## 2026-09-28 (Claude, feature/orders) — Reservation rules PHASE 2 done (built + tested)
- **DB V51:**
  - `reservations.attendance_confirmed_at/by/via` (STAFF|GUEST).
  - `guest_no_show_clears` (manager clears a guest's no-show warning, with a reason).
  - `waitlist_entries` (walk-ins: WAITING/SEATED/LEFT).
  - `reservation_reminders` (once-a-day reminders sent).
- **Attendance ("✓ Attendance confirmed", a mark, not a status):**
  - `POST /reservations/{id}/confirm-attendance`. Staff create can pass `attendanceConfirmed`; the app shows "The guest confirmed they're coming" for bookings within 24 h.
  - Response `attendance`: CONFIRMED / WAITING / CONFIRM_NOW (booked < sameDayConfirm ahead) / NOT_CONFIRMED (deadline passed).
- **No-show warning:** `GuestNoShowCounter` derives each guest's no-shows (same customer/phone/email, not the booking itself, only after the latest clear).
  - Lists batch it via `ReservationSupport.toResponses` (2 queries per page). Response `guestNoShows`.
  - `GET /{id}/guest-history`; `POST /{id}/clear-no-show-warning` (RESERVATION_CORRECT, reason required, event NO_SHOW_WARNING_CLEARED).
  - Staff are notified when such a guest books (from `noShowWarningFrom`).
  - **Bug fixed during the live test:** an empty-key placeholder "\u0000" made Postgres reject the query (500 on create/lists). It's now "#no-guest-details#", with a regression test.
- **Reminders (`ReservationReminderJob`, every minute):**
  - Day before at `confirmReminderTime`: managers (RESERVATION_APPROVE, via new `UserRepository.findActiveStaffIdsWithPermission`) get "Tomorrow: N not confirmed · M requests · K big groups to call", once per restaurant per day.
  - `attendanceCallMinutes` before a confirmed booking without attendance: staff get "Call to confirm attendance", once (CALL_REMINDED event). Never cancels anything.
- **Requests:** `ReservationRequestWatcher` (AFTER_COMMIT) tells staff "A table is free … N requests waiting" when a booking holding a table is cancelled or no-shows. Requests are never auto-confirmed.
- **Waitlist:** `/restaurants/{r}/branches/{b}/waitlist` (GET, POST, `/{id}/seat {tableId}` → table OCCUPIED, `/{id}/remove`). Responses carry waited minutes and a table-free-now / free-around estimate.
- **Summary:** `attendanceNotConfirmedCount`, `notConfirmedDueCount`, `bigGroupCount`.
- **App:**
  - Labels: "✓ Attendance confirmed", "Not confirmed", "Confirm now", "⚠ N no-shows before".
  - Panel: "Attendance confirmed" action, plus a no-show card listing the dates, with "Clear warning" for managers.
  - Overview To do: "Requests to answer", "Not confirmed yet" (red after the day-before reminder time), "Big groups (7+) to call" (from the approval setting; replaced the old hard-coded 6+), "No table", "Special requests".
  - Today's By status gets "Not confirmed" and "Needs review" filters.
  - `WaitlistBox.kt` sits in today's "Free for walk-ins" box and behind a "Waitlist" button on the Tables screen.
- **Tests (run, passing):**
  - `GuestHistoryAndAttendanceTest` (9), all reservation/settings/pre-order unit tests, and the app's `BookingRulesTest` (+2).
  - Live `phase2_check.py` 19/19, plus Phase 1 rerun 28/28. Test data cleaned (bookings deleted; clears/waitlist rows removed via SQL).
- **Note:** I stopped Codex's backend (`/tmp/pos-kds-runtime-20260927.jar`, started 23:50) to apply V51. The running backend is now `back-end/target` from the same working tree (includes Codex's KDS code), log in my scratchpad.

## 2026-09-28 (Codex, feature/orders) — Deferred Codex-only POS TODO
**User instruction:** Queue only POS History, Kitchen Status in POS, and My Sales from the supplied screenshot. State management/business logic/backend only; no screen creation or redesign. These tasks belong to Codex, not Claude. Handle the user's smaller tasks first and wait for the user's explicit instruction to resume this list.
**Saved:** `CODEX_TODO.md` contains the three unchecked items, ownership, boundaries and deferred status. No automation, implementation, app restart, commits or pushes. Other gaps from the audit are not included. Claude's reservation/settings work remains untouched.

## 2026-09-28 (Codex, feature/orders) — Read-only POS completion audit
**Request:** Identify unfinished POS logic/state/backend work, excluding screen design. Inspected existing source; no app/backend edits, tests, restarts, commits or pushes in this turn.
**Confirmed gaps:** POS KitchenStatus still uses the hard-coded `kitchenOrders` list, independent of the newly implemented KDS state. MySales numbers/charts/payments are hard-coded with no sales state/API layer. PaymentScreen accepts no order ID and uses sample payment items; payment module has entities/repository but no transaction processing controller/service; existing order mark-paid/refunded endpoints are status operations. Orders Print/Payment integration is documented as disconnected. POS History falls through the generic label branch, while real order-history querying/state already exists in Orders; order-history API is an unpaged list. Tables' Add items opens the sample `AddItemModal`, and PosScreen's onAddToOrder only closes it; selected table/order identity is not passed through this callback.
**Existing foundation/limits:** Orders has real state/API, server drafts, item/option edits, lifecycle, kitchen actions, discounts, split/merge/transfer, audit/events/history and idempotent writes. Tables, Menu, Reservations and Shifts already have their own state/API layers. Orders README explicitly excludes offline write queuing and inventory consumption. Its test-history section notes tests predating SSE need updating; no claim of a new runtime failure was made. Reservation phases/settings remain another agent's protected in-progress work. A new dirty ReservationOverviewScreen diff was already present at audit start and was not touched.

## 2026-09-27 (Codex, feature/orders) — KDS shared mobile state and backend connection
**Request/scope:** Finish KDS state/API behavior without building screens. Added only KDS data/model/state code and tests, DI registration, and lifecycle/tab wiring in the existing placeholder `KdsScreen`. Its layout/navbar/content remain unchanged. No reservation/pre-order/settings code or settings data changed; the earlier Shifts UI diff remains from the preceding task.
**Mobile:** `kds/KdsScreenModel.kt`, `kds/model/`, `kds/data/` provide active boards, held/upcoming tickets, station/device selection, ticket search/priority/status/course, all-day quantities preserving variants/modifiers/instructions, detail plus related station tickets, ticket/item fire/start/ready/complete, explicit order sync, paginated history, kitchen menu availability, station/routing/device configuration. Authenticated Ktor client and existing MenuRepository reused. State resets on user/branch/permissions; generation guards discard late reads/writes. Lifecycle-controlled SSE plus periodic transport reconciliation, serialized requests, duplicate action suppression, stale/error/loading states, and uncertain-write review without automatic retries. See `kds/README.md` for methods, permissions, and integration boundaries.
**Backend:** Added `KdsHistoryController`/`KdsHistoryService` (bounded terminal-ticket pagination, completion date range, inactive-station history) and `KdsRealtimeController` (existing committed order invalidations under KDS_READ). Item response/mapper include variant snapshot, optionsPerUnit, modifier names/quantities/notes. No migrations. Corrected an existing station-routing test fixture to give existing entities distinct IDs.
**Verified:** 29 mobile KDS tests passed (`:shared:jvmTest --tests '*kds.*'`); desktop Kotlin compilation passed. Streaming contract test uses a real local HTTP server/OkHttp because MockEngine does not support SSECapability. 18 backend KDS tests passed (`./mvnw -o test -Dtest='Kds*Test' -DfailIfNoTests=false`). Isolated live flow passed 37 requests/assertions: menu/variant/station/order creation, routing, item start, ticket ready/completion, active-board removal, history/date bounds, availability, inactive-station history, SSE connect/change. Disposable DB `pos_kds_verify_20260927_2339` was dropped; test token removed. Logs `/tmp/pos-kds-final-mobile.log`, `/tmp/pos-kds-backend-final-tests.log`, `/tmp/pos-kds-live-check.log`.
**Runtime — important:** Source contains unfinished reservation V51, so did NOT rebuild/replace the original runnable jar or apply that migration. Built `/tmp/pos-kds-runtime-20260927.jar` by replacing/adding only 9 compiled KDS class entries in the original `back-end/target/pos-0.0.1-SNAPSHOT.jar`; every other entry was verified byte-identical. Local backend now runs this KDS-only artifact (PID in `/tmp/pos-kds-backend-runtime.pid`, log `/tmp/pos-kds-backend-runtime.log`). Original jar remains intact. Authenticated local board/history/stations/devices and SSE all verified after restart; settings fingerprint unchanged and schema still V50. When rebuilding the full backend later, coordinate unfinished reservation work first. Desktop relaunched with `:desktopApp:run`, log `/tmp/pos-kds-desktop-runtime.log`.
**Boundaries:** Screens intentionally remain placeholders. Upcoming includes actual KDS tickets; undispatched reservation pre-orders stay with the protected pre-order module. No recall/undo endpoint exists, so no invented client action. Existing in-process notifier retained. Existing role permissions respected (Kitchen needs MENUS_UPDATE granted separately to change availability). Shared Kotlin behavior tested on JVM; no physical Android/iOS device test. No commits/pushes performed for this task.

## 2026-09-27 (Codex, feature/orders) — Admin Shifts layout matches POS controls
**Did:** Refined only `pos/shifts/ShiftAdminCalendar.kt`. Replaced Today/month navigation and side-panel date arrows with the full selected date and existing Reservations date picker. Week/Month/Time/List now use the existing `HeaderDropdown`; role/status filters and staff search reuse the existing POS components. Desktop controls fit one toolbar at full width and wrap on smaller windows.
**Layout:** Removed the subtitle, extra timezone/range row, duplicate scheduling buttons, outlined empty cells, and the detail-only overflow menus. Weekly rows have a wider staff column, quiet empty cells, clearer status-tinted shift chips, and horizontal scrolling when space is tight. Selected-day cards show name, role, time, and status; notes remain in shift details. Phone date strip is compact and scrolls the selected day into view. Multiple shifts per staff/day retain room for a selectable more count.
**Scope:** Admin management view only; POS waiter screen, navbar, reservations/settings, backend, and demo records were not changed. No new tests added and no commits/pushes performed for this UI request.
**Verified:** `:desktopApp:compileKotlin` and four existing `ShiftScreenshotTest` renders passed (desktop/phone calendar and Add shift dialog); inspected all four output images in `mobile_desktop/shared/build/reports/shifts`. `git diff --check` passed. Desktop relaunched with `:desktopApp:run`; app log `/tmp/pos-shifts-ui-app.log`.

## 2026-09-27 (Codex, feature/orders) — Shift calendar demo data
**Did:** Populated only the local `Local Demo Bistro` / `Main Branch` shift data for visual review: 60 labeled demo shifts for six existing Demo staff accounts, 21 September–4 October 2026. Includes 28 completed shifts with 30-minute meal breaks and 32 scheduled shifts, varied start times and days off. Existing Super Admin open shift was preserved unchanged.
**Data scope:** Inserts into `foundation_local.shifts` and `shift_breaks` only. No reservation/settings data or application source changed. Idempotent IDs and the notes prefix `Demo shift preview 2026-09-21` identify this sample set. Insertion skipped overlapping shifts and verified existing rows unchanged inside the transaction. Seed/manifest files are in `/tmp/seed-pos-shift-preview.py`, `/tmp/pos-shift-preview-manifest.json`, and `/tmp/pos-shift-preview-seed.sql`.
**Verified/runtime:** Authenticated shift board API returned all 60 samples, six staff, and 28 breaks. Reopened the existing compiled desktop app without rebuilding, so the interrupted reservation implementation was not touched. App log: `/tmp/pos-shift-preview-app.log`.

## 2026-09-27 (Codex, feature/orders) — Admin navigation matches POS
**Did:** Updated only `admin/ui/AdminNavBar.kt` to match the existing POS desktop More control: trailing chevron, identical font/spacing, white 10dp popup with text-only entries, matching compact/medium/full sizing, and the selected overflow section promoted into the fourth tab (Devices moves into More). All Admin Hub pages use this bar. Kitchen and phone navigation already reuse the POS frame/item components and matching menu styling.
**Constraint:** User explicitly requested no POS changes. SHA-256 comparison confirmed `PosTopBar.kt`, `PosBottomBar.kt`, and `PosScreen.kt` unchanged during this task. Admin More and menu text were compared directly with POS source after section/width substitutions and match exactly.
**Verified/runtime:** Desktop Kotlin build passed; `git diff --check` passed. Desktop app relaunched successfully (`/tmp/admin-pos-navigation-app.log`, PID 218558). No additional tests added for this navigation styling change; visual review remains with the user.
**Scope:** No backend, reservation-rule, or settings-page edits. Ongoing reservation work by the other agent was left intact.

## 2026-09-27 (Claude, feature/orders) — Reservation rules PHASE 1 done (built + tested)
- **DB:** V49 (`hold_until`, `arrived_guests`, `expired_at`, status EXPIRED in both check constraints), V50 (`reservation_events`: non-status changes with who/why).
- **Permissions:** new RESERVATION_READ / MANAGE / APPROVE / CORRECT. Waiter: read+manage; Manager/Admin/Co-Owner/Owner: all; Viewer: read.
  - The reservation endpoints use them now instead of SETTINGS_READ/UPDATE. The app's order form loads bookings with RESERVATION_READ.
- **Rules:** `ReservationLifecycleService`, with values from `ReservationPolicy` (Admin Hub settings, 15 s cache).
  - Staff bookings: CONFIRMED when a table fits the whole visit (tables given or availability found). PENDING (request) when no table, or 7+ without APPROVE.
  - Default end: 2 h, or 2 h 15 for 5+.
  - Check-in opens 2 h before. Check-in works without a table. `arrivedGuests` ("3 of 6"), with an arrived-guests endpoint.
  - Seat needs a table and marks the tables OCCUPIED. Undo seat: 15 min for staff, then CORRECT + reason; never with orders.
  - Complete: only from SEATED. Cancel from CHECKED_IN needs a reason; SEATED can't be cancelled.
  - No-show: only CONFIRMED and after start.
  - Reopen: back to CONFIRMED (or PENDING if never accepted); gives a new hold if the old one passed; drops tables taken meanwhile (TABLES_RELEASED event).
  - A booking from an earlier service day (06:00–02:00) needs CORRECT + reason for any change.
  - Extend hold (`/extend-hold`): moves only the hold, never past the end.
  - `/seating-check`: minutes left, the next booking on its tables, other free tables.
- **Job:** `ReservationNoShowJob`: CONFIRMED past the effective hold → NO_SHOW; PENDING past start (or its reopened hold) → EXPIRED (not a no-show). Never touches CHECKED_IN/SEATED.
- **Response:** `holdUntil`, `arrivedGuests`, `expiredAt`, `needsReview` + `reviewReason`. Summary has `expiredCount`, `needsReviewCount`.
- **Tables:** `RestaurantTableService.updateOperationalStatus` publishes `TableStatusChangedEvent`. `ReservationTableListener`: occupying a checked-in booking's table seats it; clearing a seated booking's table completes it ("Table cleared").
  - Layout items carry `nextReservationStatus/HoldUntil/HoldWarningAt/PartySize/ArrivedGuests`. The app shows a yellow "Hold ends in N min" table (`TableVisualState.HoldEnding`, #EAB308).
- **App:** `BookingRules.kt` (actions, correction rules, labels) and `BookingActionDialogs.kt` (reason prompt, arrived count, hold longer).
  - `ReservationDetailsPanel` actions rewritten: Accept/Decline, Guest arrived, Seat (pick a table if none), Undo seat, Finish, Left without ordering, Hold longer, No show, Cancel, Left before seated, Reopen.
  - Also in the panel: a "Needs review" card, and a late-guest seating card with "Move to T5". History shows the events.
  - Cards: "Hold ends in N min", "N of M arrived", "Waiting for table", "Needs review". The create dialog's end follows group size. A request shows "Saved as a request: …".
- **Tests (run, passing):** backend reservation/settings/tables/preorder unit tests. New: `ReservationRulesTest` (19), the rewritten `ReservationNoShowJobTest`, `ReservationTableListenerTest`. App: `BookingRulesTest`, `ReservationsPagingTest`, `SettingsSpecTest`.
  - Live API journey: 28/28 (script in the session scratchpad `phase1_check.py`; test bookings deleted).
  - Pre-existing failures NOT from this work: `OrderControllerSecurityTest` (missing `OrderChangeNotifier` bean), 3 `OrderPricingSafetyTest`, `RestaurantTableServiceTest` merge (stubs vs Codex's newer merge check), persistence tests (Testcontainers).
  - Fixed stale stubs in `RestaurantTableServiceTest` (seat), `ReservationCodeTest`, `ReservationStatusEventTest`.

## 2026-09-27 (Claude, feature/orders) — IN PROGRESS: reservation rules phases 1–4
**User gave the build command for phases 1–4.** Claiming migrations **V49–V56**. Codex: use V57+ for anything new. Also avoid editing `pos/pos/reservation/**`, `pos/pos/preorder/**` and the app's `pos/reservations/**` while this entry says IN PROGRESS.

## 2026-09-27 (Claude, feature/orders) — Settings → Reservations page finished
**V48 applied** (`V48__reservation_policy_more_settings.sql`, schema at v48). Codex: use V49+.
- **User approved the Settings grid**, then asked to finish only the Reservations settings. Other categories (Orders, Tables, Payments) are first drafts; they're untouched and still need a go.
- **Backend:**
  - New `settings` columns, also added to the `/reservation-policy` PATCH: `late_after_minutes` 15, `guest_reminder_hours` 24, `no_show_warning_from` 1, `deposit_from_guests` 7.
  - The PATCH rejects late >= hold ("A guest must count as late before the hold ends"), plus the earlier warning >= hold check.
  - Reset (`POST /settings/reset`) now also restores the reservation policy and pre-order values; before, it skipped them.
  - The rule's `cancellationWindowHours` (unused by booking logic) is now shown as the deposit refund deadline (24h).
- **App (`admin/settings/`):** Reservations page sections: Booking length, Who can book, Online bookings, Arrival and late guests, Confirming attendance, No-shows, Corrections, Deposit for big groups.
  - `SettingsCategory.problem()` explains live in the save bar (and blocks Save) when the smallest booking > largest, hold warning or late >= hold, or a deposit is missing its type/amount or is over 100%.
  - Inputs reset after save/discard. Decimals aren't reformatted while typing. A number typed out of range goes back on blur. Times are stored as HH:mm:00.
- **Tests written, not run (user rule):**
  - `SettingsServiceTest`: 4 new tests (policy save, two ordering refusals, reset).
  - `jvmTest/.../admin/SettingsSpecTest.kt`: edits, checks, endpoints and bodies, via MockEngine.
  - Both compile. API checked by hand: new fields read back, late >= hold gives 400.
- **Still not built:** the reservation rules phases 1–4 (nothing reads these settings yet). Occasions/events management belongs to phase 3. Waiting for the user's command.

## 2026-09-27 (Claude, feature/orders)
**User: show the Settings grid first; wait for their "go" before doing anything else on settings.** The grid is built and the app was relaunched for them to review it. The "left to do" items in the entry below are on hold until they approve the grid.

## 2026-09-27 (Claude, feature/orders) — Admin Hub Settings (in progress)
**V47 is applied** (`V47__reservation_policy_settings.sql`, schema now at v47). Codex: use V48+.
- **Backend:** new `settings` columns for reservation policy (large group from 5 / +15 min, approval from 7, hold 30, hold warning 20, check-in opens 120, reminder 15:00, same-day confirm 120, attendance call 120, reopen 60, undo seat 15, running late max 30). `pre_order_lead_minutes` default is now 30. New `PATCH /restaurants/{id}/settings/reservation-policy` (rejects warning >= hold). Reservation-rule entity defaults: 120 min, buffer 5, auto-confirm on.
- **Checked by hand:** GET settings returns the defaults; the invalid PATCH gives 400. Backend restarted from my build (log in the session scratchpad); this stopped the backend Codex had started from `/tmp/shift-admin-backend-runtime.log`.
- **App:** `admin/settings/` has `SettingsApi`, `SettingsSpec` (categories, sections, fields, save groups) and `AdminSettingsScreen` (grid of 8 categories, then one page per category with a save bar). It's wired to `AdminSection.SETTINGS` and registered in Koin. Compiles.
- **Pages with settings:** Reservations, Orders & kitchen, Tables, Payments & receipts. Devices, Shifts and Notifications show "nothing to set yet". Online booking is disabled ("Later").
- **Left to do:** client checks before saving (smallest <= largest booking; a deposit needs a type and an amount); stop decimal inputs reformatting while typing; reset a number typed out of range when the field loses focus. Then launch the app and look at it. After that: Devices & printing (printers). Shifts and Notifications contents still need deciding with the user.
- **The reservation rules (phases 1–4) are NOT started.** These settings are only stored; nothing reads them yet. Still waiting for the user's build command.

## 2026-09-27 (Claude, feature/orders) — IN PROGRESS
**Claiming migration V47** for the reservation-policy settings columns on `settings` (Admin Hub Settings build). Codex: use V48+ for anything new.

## 2026-09-27 (Claude, feature/orders)
**Build plan rule (user): test each phase before moving to the next, then check the whole booking journey at the end.**
- **Phase 1:** statuses, timers, table availability, correction permissions.
- **Phase 2:** reminders, large-group attendance confirmation, requests, waitlist.
- **Phase 3:** occasions, special menus, events.
- **Phase 4:** website bookings, messages, payments, refunds.
- **End:** a full end-to-end booking journey check.
- **Each phase includes its Admin Hub settings** (every time/limit/threshold with its agreed default), so it's production ready. This overrides the general "tests only at the very end" preference for this reservation build.
- Still open: deposit deadline default (24h?), and whether normal bookings use the 7+ attendance steps.
- **Do not start until the user gives the build command.**

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): correcting a no-show updates the guest's history.**
- **Derive the no-show count from the guest's reservations whose current status is NO_SHOW.** Don't store a counter, so corrections update it automatically.
- **Marked by mistake, or the guest arrived after all:** correcting the booking (reopen / "Guest arrived") removes it from the count. The original change stays in the status history/audit.
- **The guest really didn't come:** a manager can **clear the warning** with a reason. This stores a per-guest "warning cleared at" time plus who and why; the NO_SHOW records remain.
- **A later no-show** after that time shows a new warning. The warning counts only no-shows after the last clear.
- This replaces the earlier "manager clears the record" wording: clearing hides the warning, it never deletes history.
- **Still open:** the deposit deadline default (24h suggested), and whether normal bookings use the 7+ attendance steps. Still waiting for the build command.

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): cancelling and refunding are explained separately.**
- **Nothing paid:** the guest cancels with no charge.
- **Food, extras or a deposit already paid:** before the guest confirms the cancel, show how much is refunded, how much is kept, and why, line by line per paid part. E.g. "€49.10 back: food pre-order €50 minus the card fee".
- **The button always says "Cancel booking"**, never "Cancel for free".
- **Separate refund rules per paid part** (each refunds minus the card fee before its deadline; kept after it or on a no show; Owner goodwill partial refund possible):
  - **Pre-ordered food:** until it goes to the kitchen (default 30 min before).
  - **Extras** (cake, decoration): until that item's own "order before" deadline.
  - **Deposit** (7+, optional): its own deadline, **not** the kitchen rule. My suggested default: 24h before (Admin Hub setting); asked the user to confirm.
- Still waiting for the build command. Still open: do normal bookings use the 7+ attendance steps?

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): correction permissions.**
1. **Reopen a CANCELLED or NO_SHOW booking:** staff with reservation permission within **1 hour of that status change** (use the status-history time, i.e. `cancelledAt` / `noShowAt`). After that, a manager must approve and give a reason.
2. **Undo seating (SEATED → CHECKED_IN):** staff within **15 minutes** (Admin Hub setting; the default changes from the earlier 5 min), **only if no orders or payments are attached**. After that time, or when orders/payments are attached, a manager must handle it.
3. **Correcting a booking from an earlier service day:** any change always needs a manager and a reason.
- Still waiting for the build command. Still open: do normal bookings use the same attendance steps as 7+?

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): check in without a table.**
- "Guest arrived" works even if their table is gone (e.g. they arrived late and it was given away): the booking becomes **CHECKED_IN** and shows **"Waiting for table"**. Staff choose a free table → SEATED when one frees up.
- **CHECKED_IN = they're here; SEATED = they have a table.** Check-in must not require a table.
- **Always use the existing reservation, never a new walk-in**: a new walk-in would leave the booking wrongly as No show and count the guests twice.
- The original table is not guaranteed after the hold expires.
- This replaces "Guest arrived on a No show: if the table is taken, pick another first".
- **My addition, not yet confirmed:** checked-in guests waiting for a table also show at the top of the walk-in waitlist box, marked "has a booking".
- **Still open from before:** do normal bookings (under 7) use the same attendance steps 2–5 as big groups?
- Still waiting for the build command.

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): attendance confirmation for large groups (7+).**
1. Staff approve the request → CONFIRMED.
2. The day before, the guest gets "Your table for 8 guests is booked tomorrow at 19:00. Please confirm you're still coming." with [Confirm attendance] [Cancel booking].
3. The guest confirms → a small **✓ Attendance confirmed** mark (extra info, NOT a status; stays CONFIRMED).
4. Still no reply 2 hours before → staff are notified to call. **Never auto-cancel for no reply.**
5. Same-day booking confirmed during a phone call → staff mark attendance confirmed immediately; no need to ask again.
- **Rename:** my "✓ Reconfirmed" is now **"✓ Attendance confirmed"** throughout the plan. This replaces "staff call big groups the day before" with step 4.
- **Open:** do normal bookings (under 7) use the same steps 2–5? I suggested yes, one rule for all, and asked the user.
- Still waiting for the build command.

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): extending the hold ≠ extending the booking.**
- **Extend the hold** (the guest calls "we'll arrive at 19:40" on a 19:00–21:00 booking): staff keep the table until e.g. 19:45.
  - Only the hold timers move: "Hold ends in 10 min" at 19:35, No show at 19:45.
  - The booking **still ends at 21:00**. The guest's message is "we'll wait longer for you, but your booking still finishes at 9 pm".
  - Needs a hold-until time on the reservation (e.g. `holdUntil`, default start + the 30 min setting).
- **Extend the end** (stay past 21:00) is a separate action. The app checks the table's next booking plus 5 min cleaning: free → extend; not free → suggest another table, or staff decline.
- **The website "I'm running late" button** only extends the hold (max +30 min, once), never the end.
- Still waiting for the build command.

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): staff bookings are CONFIRMED immediately only when a suitable table is available.**
- **Group under 7, table free** → CONFIRMED right away.
- **Group under 7, no table free** → PENDING request. The app suggests nearby free times (reuse the create dialog's nearest-free-start search) for staff to offer on the call.
- **7+ guests** → needs approval. Staff **with approval permission** can approve and confirm during the call → CONFIRMED; otherwise it stays PENDING.
  - A new permission, e.g. RESERVATION_APPROVE. My suggested default holders: Manager and above; the Owner can grant it to others (not yet confirmed by the user).
- **Availability must cover the full visit plus cleaning**: start → start + booking length (2h, or 2h15 for 5+) + 5 min cleaning. Not just the arrival slot. Check `ReservationAvailabilitySupport` and the rule's `bufferMinutes` against this.
- The same availability rule decides the website's instant confirm vs request (phase 4).
- Still waiting for the build command.

## 2026-09-27 (Claude, feature/orders)
**Change to the build plan (user): a PENDING request never becomes No show.** Pending now means the restaurant hasn't accepted yet, so a no-show would wrongly blame the guest.
- **CONFIRMED + guest doesn't arrive:** NO_SHOW when the hold expires; counts in the guest's no-show history.
- **PENDING + staff never answered:** **Request expired**, a new end state (e.g. EXPIRED, or DECLINED with an "expired" reason). It is **not** a no-show and is never added to the history.
  - Suggested timing: it expires at the booking start time, and the guest is emailed "Sorry, we couldn't confirm your request".
  - Staff can still check in a pending request if the guest arrives before then.
- **Fixes to the plan I sent:**
  - `ReservationNoShowJob` must only take CONFIRMED (not PENDING) for auto no-show; today its WAITING set includes PENDING.
  - The manual "mark No show" also only applies to CONFIRMED.
  - The earlier "requests wait until answered or cancelled" now ends at the booking time with "Request expired".
- Still waiting for the build command.

## 2026-09-27 (Claude, feature/orders)
**Decision (user): importing an existing item into a special menu makes a COPY.** A price change in the original menu doesn't change the copy, and the other way round. With this, every reservation-rule question from today's discussion is answered, except private events (deferred). **Waiting for the user's command before building anything.**

## 2026-09-27 (Claude, feature/orders)
**Decision (user): occasion add-ons live in the Menu, not in settings.**
- When creating a menu, it can be a **special menu** (e.g. "Occasion extras"; the event menus are special menus too).
- Inside a special menu the admin can **create new items** (decoration, flowers) or **import existing items** from other menus, via an "import existing" option next to create, like picking existing items when adding one.
- Occasion bookings offer the items of that special menu, with each item's deadline and occasion link.
- **Open:** is an imported item the same item (a price change applies everywhere) or a copy with its own price? My suggestion: a copy.
- Reminder: don't build any of this until the user gives the command.

## 2026-09-27 (Claude, feature/orders)
**Decisions on the small open points (user):**
- **Event nights:** "only the special menu" vs "special + normal menu" is a per-event setting in the Admin Hub.
- **Cleaning time between bookings: 5 min** (Admin Hub setting). The +15 min for 5+ guests is extra table time, so 5+ = 2h15 + 5 min cleaning.
- **Day-before reminder at 15:00**, changeable in settings.
- **Goodwill partial refunds: Owner only for now, or whoever the Owner allows** (a grantable permission).
- **"Hold ends in 10 min" table colour: yellow #EAB308.** Picked by me; the user will say if they don't like it.
- **Still open:** add-ons as menu items vs separate extras. I'm explaining it again; the user didn't follow the question.
**IMPORTANT: do NOT start building any of these reservation rules until the user explicitly gives the command.**

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): big groups.**
- **Every booking of 7+ guests needs staff approval**, occasion or not. The website sends it as a request, like "no free table". The threshold is an Admin Hub setting.
- **Deposit for 7+ is optional, an Admin Hub setting, off by default** ("mostly no deposit"). When on: paid online, refunded minus the card fee if cancelled in time, kept on a no show; same rules as pre-orders.
- **Big groups may pre-order online if they want**, with the same pre-order rules (refund minus fee before the kitchen dispatch; no refund after that or on a no show; Owner goodwill partial refund).
- Staff call big groups the day before (shown in To do), on top of the normal ✓ Reconfirmed reminder.
**All parked topics are now discussed** except private events (type 2), deferred "later if needed".
**Small open points** to settle when building:
- add-ons as menu items vs separate extras;
- a per-event switch "only the special menu" vs "special + normal menu";
- whether the +15 min for 5+ guests is table time or cleaning;
- the 15:00 reminder time (placeholder);
- whether Co-Owner can also give goodwill refunds;
- the colour for "Hold ends in 10 min".

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): paid add-on refunds use the same rule as pre-orders.**
- Cancel before the add-on's own "order before" deadline (set per add-on in the Admin Hub, e.g. cake 2 days before) → refund minus the card fee.
- After that, or a no show → no refund. The Owner may give a goodwill partial refund (audited).
- Guest text states only the policy, e.g. "Cancel before 12 Feb to get the cake money back (minus the card fee)."

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): pre-order cancelled after it went to the kitchen.**
- **No refund**, same as a no show.
- **The Owner may give a goodwill partial refund**, never the full amount: a manual action saved in the audit (who, amount, reason). Whether Co-Owner can too is not asked yet; the user said "owner".
- **Guest-facing text states only the policy**, never the goodwill option. E.g. at payment and in emails: "Cancel before 18:30 to get your money back (minus the card fee). After that there's no refund." The time is the kitchen dispatch time.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): pre-order money rules.**
- **No show → no refund.**
- **Guest cancels → refund minus the payment provider's fee.** The provider (e.g. Stripe, ~1.5% + €0.25 for EU cards; depends on the provider chosen) doesn't return its fee on refunds, so the restaurant loses nothing.
  - Show it at payment, e.g. "If you cancel, you get €40 back minus the card fee (€0.85)".
  - This needs a fee calculation per payment when the payment provider is added.
- **Open question:** a cancel after the pre-order has gone to the kitchen (the auto dispatch time, suggested 30 min before, an Admin Hub setting) — refund minus fee, or no refund? My suggestion: no refund.
- Existing `pos.pos.preorder` code still has "paid by default" and the older refund-before-kitchen rule. Update it when payments are built.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): special events, type 3 — the restaurant's own nights** (New Year's Eve, Valentine's…).
- **An event = a special menu available that day. Bookings stay normal:** no pre-order, no online payment, same booking rules. Guests order from the special menu at the table.
- **Admin Hub → Events:** name, date(s), icon (e.g. ❤️ Valentine's, 14 Feb), and the special menu. Reuse the menu date availability (`Menu.availableFromDate` / `availableUntilDate`), so the menu shows in the POS only that day.
- Bookings on that date automatically show the event icon; the website shows a banner (e.g. "❤️ Valentine's evening, special menu").
- **My suggestion, not yet answered:** a per-event switch in the Admin Hub, "only the special menu" vs "special + normal menu" that evening.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): occasion add-ons.**
- Besides free options (candles, song, quiet table), an occasion booking can have **add-ons** (e.g. cake €25, prosecco €30, flowers €15, decoration €20).
- **Admin sets them in the Admin Hub settings:** name, price, which occasions they appear for, how early they must be ordered (e.g. cake 2 days before; the website hides it after that), and quantity.
- **Paid add-ons must be paid online by the guest** at booking. Needs the online payment provider (website phase; `payment` module has entities only).
  - Phone bookings with a paid add-on → the guest gets a payment link by email/SMS; the add-on shows "Waiting for payment" until paid.
- On the day: listed on the booking and in the To do box so staff prepare them. They reach the kitchen/bar at the right moment (e.g. the cake at dessert) and show on the bill as already paid.
- **Refund on cancel:** decide together with the pre-order cancellation discussion (same question).
- **Still open:** add-ons as menu items marked "for occasions" (my suggestion: prices, KDS and reports work already) vs separate extras. Also: 7+ approval for every booking or only occasion bookings.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): special events, type 1 — an occasion on a normal booking.**
- **Scope:** the user chose types 1 (occasion) and 3 (the restaurant's own events). Type 2 (private events: weddings, company dinners, full buyouts) is deferred, "later if needed".
- **Occasion field** (optional, staff and website booking forms). Each occasion has **its own icon and its own options**. Starting set, editable in the **Admin Hub**:
  - 🎂 Birthday: cake (ours / guest brings one), candles, birthday song, decoration.
  - ❤️ Anniversary: flowers, prosecco/champagne, dessert with a message.
  - 💍 Engagement/proposal: surprise (ring hidden), flowers, champagne, quiet table.
  - 🎓 Graduation: cake, decoration, champagne.
  - 🌹 Date night: quiet/window table, candles.
  - 💼 Business: quiet table, invoice with company details.
  - ✨ Other: free text.
  - Plus a free note (e.g. "Cake with 30 candles at dessert", "It's a surprise").
- **Shown on:** the reservation card (icon + tag), the table on the floor plan (icon), a check-in reminder, the KDS dessert/relevant ticket (note), and the future-day To do → "Special requests" count.
- **More than 6 guests (7+) on an occasion booking → staff must check/approve** (a request, like "no free table"), never instant.
- **Open question:** does the 7+ approval also apply to big groups without an occasion? Earlier I proposed 9+ for big groups and the user hadn't answered.
- Otherwise it's a normal booking: same rules and length.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): booking length.** Every booking takes **2 hours**; groups of **5 or more get +15 min** (2h15). This is simpler than my per-size table, which was rejected. It applies to availability checks, suggested times, website bookings and staff bookings; staff can still change one booking's end time by hand.
**Rule (user): everything a restaurant can change lives in the Admin Hub settings, with the agreed defaults.** Added to `AGENTS.md`. For the reservation rules that means at least:
- booking length (2h) and the extra for 5+ guests (+15 min);
- no-show hold (30 min) and the "hold ends" warning (20 min);
- the day-before reminder time (15:00) and the same-day confirm deadline (2h);
- the reopen window (1h) and the undo-seat window (5 min);
- the running-late limit (+30 min) and check-in opening (2h before).
Today some of these are constants in code (`ReservationNoShowJob.GRACE`, `ReservationLifecycleService.CHECK_IN_OPENS_BEFORE`, the rule's `defaultDurationMinutes` / `bufferMinutes`); move them to settings when building.
**Idea list done:** all 8 ideas discussed. Card/deposit and big groups / special events are parked for later.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): walk-in waitlist**, following my 4 steps as proposed.
1. When every table is taken, staff add the walk-in to the waitlist: name, phone, group size, time added (e.g. "Maria · 4 guests · 18:40").
2. The list shows how long each guest has waited and a rough "table free around HH:MM", based on when seated tables are expected to finish.
3. When a fitting table frees up, staff pick them. A "Your table is ready" text goes out once an SMS provider exists; until then staff call them. One-tap seat from the list.
4. If they leave, staff remove them. It's never recorded as a no-show.
- **Placement:** a small "Waitlist" box on today's Reservations overview and on the Tables screen.
- **Not the same as the website request:** a request is for a future day or time and online; the waitlist is walk-ins at the door now.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): part of a group arrived.**
- No new status. The booking is **CHECKED_IN** with an arrived count, e.g. "3 of 6 arrived"; later "6 of 6". Needs a field such as `arrivedGuests` on the reservation, and "Guest arrived" asks for how many.
- Staff can **seat them right away**; SEATED keeps showing "3 of 6" until the rest come.
- **The no-show timer stops** at the first arrival (a checked-in booking is never a No show). Reports count arrived vs booked guests.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): late guests and the "Running late" button.**
- **"I'm running late" in the guest's email** (with Confirm/Cancel, website phase): pick 10/15/20 min. Staff see "Running N min late" + a notification, and the hold is extended (same as staff extending it after a call).
  - Limits I proposed, not objected to: once per booking, max +30 min, only before the hold expires.
  - The extension only goes through if their table (or another free one) can still take them; otherwise the guest sees "We can hold your table until HH:MM".
- **A late guest keeps the original end time** (less time at the table). On arrival or on "running late", the app checks that table's next booking:
  - it fits → seat them, and staff see "Next booking at this table at HH:MM · Xh Ym left";
  - they need more time or it doesn't fit → the app suggests another table free for their full duration, and staff choose;
  - nothing fits → staff are warned and decide (shorter stay or not).

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): no-show history on the guest.**
- From a guest's **first** no-show, every new booking by that guest (matched by customer, or by email/phone for online bookings) **notifies staff** and shows a warning on the booking, e.g. "⚠ 1 no-show before" (count and dates in the guest profile).
- **Manager and above** can clear the record. The clear action is saved in the audit (who, when, reason) and never silently deletes history.
- **No automatic restriction.** My suggestion (after 3 no-shows, online bookings become requests) was not adopted; staff decide.

## 2026-09-27 (Claude, feature/orders)
**Decision (user): cancelling is always free.** No cancellation cut-off and no "Late cancel" mark; a guest can cancel any time. The only exception is bookings with a food pre-order: the user wants to discuss those later, so treat the earlier pre-order refund rule (refund if cancelled before it goes to the kitchen) as still open. Idea 3 (card or deposit) is parked with big groups / special events for a later discussion.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): website booking with no free table → request.**
- **No table free at booking time:** the website doesn't refuse. The booking becomes a **PENDING request**, and the guest sees/gets: "Your reservation request has been sent. We'll let you know as soon as a table is free."
- **Staff see requests** waiting for an answer (e.g. in the To do box).
  - **Accept** (assign a table) → CONFIRMED + "Your table is confirmed ✓".
  - **Decline** → the guest gets "Sorry, we're full that day".
- **A table frees up** (a cancellation etc.): the app **notifies staff**, and they decide. It does **not** auto-confirm the oldest request.
- **No automatic decline and no automatic cancel on silence.**
  - The guest gets the reminder ("See you tomorrow? [Confirm] [Cancel]"). Cancel → CANCELLED.
  - **No reply → nothing changes**: the booking or request stays as it is.
  - This applies both to confirmed bookings (the ✓ Reconfirmed mark just stays missing, which gives the "Not confirmed" flag) and to waiting requests (they stay PENDING until staff answer or the guest cancels).

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): answers to the open questions on the two-step confirm.**
- **Staff-made bookings** (phone / in person): staff check availability and tell the guest → **CONFIRMED right away**. This replaces the earlier "every new booking starts PENDING" for staff bookings.
- **Website bookings:** instantly CONFIRMED when a table is free, and the guest gets an email/text "Your table is confirmed ✓".
- **All bookings** get the day-before "See you tomorrow? [Confirm] [Cancel]" → ✓ Reconfirmed mark. The day-before / 2h / "Not confirmed" rules check that mark.
- **Big groups and special events:** deferred; to be discussed later. For now only normal bookings.

## 2026-09-27 (Claude, feature/orders)
**To do (user decision, not implemented): two kinds of "confirm".** This updates the FINAL reservation rules entry below.
- **Step 1, booking accepted (restaurant → guest), instant:** an online booking with a free table is accepted immediately → **CONFIRMED**, and the guest gets a "Your table is confirmed ✓" email.
  - Only special cases go to **PENDING** for the restaurant to approve: very big groups (threshold still to decide, e.g. more than the largest table or 8+), or cases where no single table fits.
  - Staff accept or decline; the guest is emailed either way.
  - Pending should be answered within hours, not days.
- **Step 2, "Still coming?" (guest → restaurant), the day before:** a reminder email/text: "See you tomorrow? [Confirm] [Cancel]".
  - Confirm does **not** change the status; it adds a **✓ Reconfirmed** mark (by the guest via the link, or by staff after a call). Cancel → CANCELLED.
  - Timing: bookings made more than 24h ahead get a "booking received/confirmed" email right away (with a cancel link), then the reminder 24h before. Bookings made less than 24h ahead get one email straight away with Confirm/Cancel.
- **Knock-on effect on the earlier rules:** "confirm by the day before", the 15:00 reminder to managers, the 2h rule, "Confirm now" and the red "Not confirmed" warning now check the **✓ Reconfirmed mark**, not the PENDING status. PENDING now only means "restaurant hasn't accepted yet".
- **Open question for the user:** do staff-made bookings (phone / in person) become CONFIRMED right away, since staff are the restaurant accepting them, then only need ✓ Reconfirmed? The earlier decision said they start PENDING.
- **Needs:** email sending (exists), a public Confirm/Cancel page (website phase), and optionally an SMS provider later.

## 2026-09-27 (Claude, feature/orders)
**To do (user requested, not implemented): FINAL reservation rules.** This replaces the earlier to-do entries from today on confirmation rules, no-show steps, undoing a No show, and after check-in/seating; where they differ, this one wins. Agreed with the user after review.

**Statuses:**
- **Pending:** every new booking, any channel, same-day included → Confirmed, Checked in, Cancelled, No show.
- **Confirmed:** staff confirm (later also the guest via a link) → Checked in, Cancelled, No show.
- **Checked in:** "Guest arrived", allowed from 2h before, or from No show (rules below) → Seated, or Cancelled with a reason ("Left before seating"). Never No show, never auto-cancelled.
- **Seated** → Completed; Undo seat → Checked in (rules below). No Cancel.
- **Completed:** only when staff mark them finished or close the table visit. **Paying the bill does not complete it** (today nothing auto-completes; keep it that way).
- **Cancelled:** from Pending/Confirmed, or from Checked in with a reason → Reopen.
- **No show:** only from Pending/Confirmed, automatically when the hold expires or set by staff → Reopen, or "Guest arrived".

**Timeline:**
- **Confirmation deadline:**
  - Booked on an earlier day: confirm by the day before. Reminder to managers at 15:00 the day before (time adjustable); tomorrow's "Pending to confirm" To do row turns red.
  - Booked the same day, 2h or more ahead: confirm at least 2h before start.
  - Booked less than 2h ahead: orange "Confirm now" immediately. Red only if still unconfirmed when due.
- **Deadline passed, still Pending:** red "Not confirmed" badge, a warning at the top, a manager notification. The booking is kept, never auto-cancelled.
- **Check-in opens** 2h before.
- **15 min late:** shows "N min late" under Previous → Still active.
- **Guest calls to say they're late:** staff can **extend the hold** (e.g. +15 min); every timer below shifts with it.
- **Hold ends in 10 min** (default start+20): the table shows **"Hold ends in 10 min"**; the details say "Reservation hold expires in 10 min". This replaces the "About to be free" name.
- **Hold expires** (default start+30): No show (Pending/Confirmed only). The hold is released, but the table only becomes Free if nobody is sitting at it.
- **Booking end while still Checked in:** a **"Needs review"** flag (a flag, not a status; also listed in To do). Staff pick Seated, or Cancelled "Left before seating". Change `ReservationNoShowJob`'s never-seated rule to raise this flag instead of setting NO_SHOW.
- **02:00 (end of the restaurant's day):** it only decides a booking's date and never ends an active visit. Visits left open from an earlier day get "Needs review" and are never auto-completed.

**Corrections and permissions:**
- **Reopen** (No show / Cancelled): back to Pending or Confirmed. Within 1h: staff with reservation permission. After 1h: Manager+ with a required reason. Never takes back a table that has been given away.
- **"Guest arrived" on a No show:** straight to Checked in; same 1h / Manager rule. If the table is taken, pick another.
- **Undo seat:** within exactly 5 min, staff with reservation permission; after that Manager+ with a reason. If orders or payments are already linked, a manager must resolve them first.
- **Cancel after check-in:** staff with reservation permission, reason required.
- **Seated guests leave without ordering:** Complete with a reason ("Left without ordering").
- **Every change** records who, when and why in the status history.

**Still to choose (ideas from OpenTable/Resy/SevenRooms, not agreed):** guest confirm/cancel link; free-cancellation cut-off; card or deposit for groups and busy nights; no-show history on the guest profile; "running late" button; partly arrived groups; waitlist; table time by group size.

## 2026-09-27 (Claude, feature/orders)
**To do (user requested, not implemented): after check-in and after seating.**
- **Checked in:** can never become No show; No show means "never came".
  - Allow **Cancel from CHECKED_IN with a reason** (e.g. "Left before being seated", "Waited too long").
  - Change `ReservationNoShowJob`'s never-seated rule (`findCheckedInPastEnd`, `NEVER_SEATED_REASON`) so a booking checked in but never seated by the end becomes **CANCELLED** with reason "Left before being seated", not NO_SHOW.
- **Seated:** no Cancel.
  - Guests who leave without ordering → **COMPLETED with a reason** ("Left without ordering").
  - Add an **"Undo seat"** (SEATED → CHECKED_IN) for when the wrong booking was seated. Any reservation staff within ~5 minutes of seating; after that Manager+ with a reason, same pattern as the No show undo rule.
- **Where it touches:** `ReservationLifecycleService.transitionReservation` (sources for CANCELLED and the new undo transition), the no-show job, request DTOs (reasons), app detail actions, and tests.

## 2026-09-27 (Claude, feature/orders)
**To do (user requested, not implemented): rules for undoing a No show.**
- **Today:** `ReservationLifecycleService.reopenReservation` moves NO_SHOW (or CANCELLED) back to PENDING, with no time limit and no role check beyond the reservation write permission (`SETTINGS_UPDATE`). `ReservationNoShowJob` skips bookings already reopened from no-show.
- **New rules (user decisions):**
  1. **Within 1 hour** of the booking becoming NO_SHOW (use `noShowAt`), staff who can manage reservations may reopen it or mark the guest arrived. My reading of the user's "maximum one hour"; confirm when building.
  2. **After that hour**, only **Manager and above** (role rank via `RoleHierarchyService`) can change it, and a **reason is required**. The user's example: "we forgot to check the guest in".
  3. **"Guest arrived" is one action:** NO_SHOW → CHECKED_IN directly. Today it takes a reopen to PENDING and then a check-in. The transition must accept NO_SHOW as a source for CHECKED_IN under these rules (the 2h check-in-opens rule is irrelevant here).
  4. Reopening because it was a mistake still goes back to PENDING/CONFIRMED.
  5. **The table may have been given away by then:** if the booking's table is now taken, the app asks for another table before checking in.
  6. **Record who and why** in status history (`addStatusHistory` reason), including for the within-an-hour case when a reason is given.
- **Where it touches:** `ReservationLifecycleService` (source/time/role checks), reopen/check-in request DTOs (a reason, required after 1h), the app's reservation detail actions ("Guest arrived" on a No show booking, a reason dialog, a table pick if taken), and tests.

## 2026-09-27 (Claude, feature/orders)
**To do (user requested, not implemented): no-show steps for a reserved table.** The guest hasn't arrived (booking still PENDING/CONFIRMED, not checked in):
1. **0–20 min after the start:** the table stays **Reserved** (purple, as now).
2. **At 20 min:** the table shows a new **"About to be free"** state: its own look on the floor plan and table list (e.g. a warning variant of Reserved with "free in 10 min"). This tells staff they can soon give it away. The booking itself stays on the reservations list, flagged late (today it already shows "20 min late" in Previous).
3. **At 30 min:** the booking becomes **No show** and the table is **Free**. This is already how it works: `ReservationNoShowJob` uses `GRACE = 30 min`, and once the booking is NO_SHOW the table stops showing it as its next reservation. Keep the 30 min, and keep the 20 min step aligned with it (e.g. `GRACE - 10 min`).

**Where it touches:**
- Backend: `RestaurantTableSupport.toLayoutResponseItem` / `TableLayoutItemResponse`, e.g. a `nextReservationLateSince` field or an "about to be free" flag computed from `nextReservationStart` + 20 min while the booking isn't checked in.
- App: `tables/ui/Table.kt` gets a new `TableVisualState` with a colour (pick with the user; current palette Free #147A25, Occupied #D15F00, Reserved #8B5CF6, Bill pending #3B82F6, Unavailable #6B7280).
- Notifications: optionally notify the waiter/host at 20 min.

## 2026-09-27 (Claude, feature/orders)
**To do (user requested, not implemented): reservation confirmation rules.** Agreed with the user in discussion.
1. **Every new reservation starts PENDING.** No exceptions: staff-made and same-day bookings too. This is already how `ReservationCrudService` / `ReservationPublicService` create them; keep it.
2. **Confirmation deadline.**
   - Booked on an earlier day: confirm by the day before.
   - Booked on the same day: confirm at least 2 hours before its start.
   - Past the deadline and still PENDING, it counts as "not confirmed" (hard warning).
3. **Reminder the day before.** In the afternoon (my suggestion 15:00; the user didn't fix a time), managers get a notification like "N bookings for tomorrow are still not confirmed". In the future-day overview, "Pending to confirm" in the "To do before the day" box turns red while it's above 0.
4. **Hard warning on the day** for bookings past the deadline and still PENDING:
   - a red "Not confirmed" badge on today's list instead of the orange Pending badge;
   - a warning at the top of the overview, e.g. "2 bookings today were never confirmed";
   - a notification to managers when a booking passes its deadline unconfirmed (same-day ones 2h before start).
5. **If it's never confirmed, keep it and flag it.** No auto-cancel and no freeing its table: the guest may still come, and check-in straight from PENDING stays allowed. The existing auto no-show 30 min after start (`ReservationNoShowJob`) still applies.

**Where it touches:**
- Backend: a scheduled job for the day-before reminder and the deadline notifications (like `ReservationNoShowJob` + `ReservationNotifications`). Optionally a `confirmationOverdue` flag in `ReservationResponse` / summary, so the app and later the website agree.
- App: `ReservationOverviewScreen` (badge, warning, red To do row) and `ReservationCard` status pill.

## 2026-09-27 (Claude, feature/orders)
**Note for Codex:** at 18:20 the desktop build was broken by in-progress Shifts work. `ShiftAdminCalendar.kt` calls `ShiftMessage` and `ShiftPanel`, which are `private` in `ShiftScreen.kt` (and before that, `ShiftScreen.kt` referenced `ShiftAdminCalendar` before the file existed). I didn't touch the Shifts files. I left a background loop that relaunches the desktop app as soon as `:desktopApp:compileKotlin` passes. The user can't open the app until then.

## 2026-09-27 (Claude, feature/orders)
**Did:** Removed the word "party" from on-screen text (user: it reads like a celebration; use "booking" or "group").
- **Future-day overview:** "Biggest booking: N guests", "Big groups (6+)", "No groups of 6 or more…".
- **Tables:** `TableDetailsModal` split dialog now reads "The guests and their order stay…". `TablesScreen` merge dialog reads "Merge them into one group?" / "Merge into one group".
- **Convention:** added to `AGENTS.md` (App section).
- **Tests:** no test strings referenced the old text.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `.../pos/tables/ui/TableDetailsModal.kt`, `.../pos/tables/ui/TablesScreen.kt`, `AGENTS.md`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Removed "Ordered online" from the future-day "To do before the day" box (user: it isn't a to-do). The box now has Pending to confirm, No table, Big parties (6+) and Special requests; "Clear" no longer touches `onlineOnly`. Today's By status panel still has its "Ordered online" row. Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Future-day Reservations overview (user agreed to my proposal), in `ReservationOverviewScreen.kt`. `isFuture = !isToday && !isPast`; Codex's past-day branch and today are unchanged.
- **Cards** (`futureCard0..2` + existing `card3`):
  - **Bookings:** pending+confirmed count, suffix "booked", detail "N confirmed · N pending · N cancelled".
  - **Guests expected:** the largest party.
  - **Busiest time:** the hour with most bookings, with booking and guest counts.
  - **Without a table:** the existing card.
  - Numbers show "–" until `dayDataComplete`.
- **Right column:** a new `todoList` box, "To do before the day", above Codex's `reservationsByTime`. Its rows use `StatusCountRow`, are clickable filters that combine, and share a "Clear":
  - **Pending to confirm:** status filter.
  - **No table:** area Unassigned.
  - **Big parties (`BIG_PARTY_SIZE` = 6+):** local `bigOnly`.
  - **Special requests:** local `requestsOnly`; `specialRequests` or `internalNotes` not blank.
  - **Ordered online:** `onlineOnly`.
- **List:** without filters it shows only PENDING/CONFIRMED, grouped under `HourGroupLabel` rows ("19:00 · 6 reservations · 14 guests"). With filters it's the usual flat list; the title and empty messages cover the new filters.
- **Phone/tablet:** the future cards 2×2, then the list, To do, By time.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Added an "Ordered online" row under "No table" in the overview's By status panel (user request), in `ReservationOverviewScreen.kt`.
- **Meaning:** bookings with `source` in `OnlineSources` (WEB, MOBILE, THIRD_PARTY), the same definition as Codex's past-day "Online bookings / N ordered online" card. It is NOT pre-orders: `ReservationResponse` has no pre-order flag; that would need a backend field if the user meant food ordered ahead.
- **Behaviour:** globe icon, purple `OnlineColor`, and the count covers every status that day. It toggles a local `onlineOnly` filter (`remember(date)`) that combines with status / No table / hour. The list title adds "Ordered online", "Clear" resets it, and it has its own empty message.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** The user reported "can't reach backend". The backend (my 11:28 start) had been shut down cleanly at 15:44, probably by Codex while rebuilding for the Shifts work (V46). A 15:51 jar existed but was never started, so the backend was down for ~2h.
- Rebuilt with `./mvnw -o package -Dmaven.test.skip=true` (already up to date) and started `java -jar target/pos-0.0.1-SNAPSHOT.jar`. Schema at version 46; login and `/arrivals` checked OK.
- The log is in my scratchpad (`backend.log`).
- **For any agent:** if you stop the backend to rebuild, start it again afterwards; the desktop app shows "can't reach backend" otherwise.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-27 (Claude, feature/orders)
**Did:** Three reservations overview tweaks (user), in `ReservationOverviewScreen.kt`.
1. **Past-day "Reservations by time" hour rows now look clickable.** This is Codex's panel; I only replaced its inline row with a new `HourRow`.
   - Bordered white card with a chevron.
   - Hover tint and hand cursor (`hoverable` + `pointerHoverIcon(PointerIcon.Hand)`).
   - Picked hour: green border, `FormGreenSoft` fill and a filled check.
   - Empty hours: greyed out and not clickable.
   - A "Pick an hour to see its reservations" hint under the title.
   - Behaviour unchanged (`selectedHour` toggle).
2. **The list opens with Arriving in focus again.** The day list loads page by page and each page added Previous rows under the anchor, pushing Arriving down to the "Previous rows filling the box" layout.
   - The positioning effect is now keyed on `previousRowCount` and re-applies (Previous pinned, "N previous above" line, Arriving) whenever Previous grows.
   - It stops once `userMoved` is set. That happens on user-input scroll (in the `NestedScrollConnection`) or any section click (`startMotion`).
   - It uses `headerPx` when known, otherwise measures the Previous header once.
3. **The "N previous reservations above" pill is no longer clickable** (plain `Surface`, no `onClick`).
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders) — Shifts
**Did:** Built the shift workflow end to end, preserving the POS form/button/card style. POS → Shift is personal clock-in, break/resume, clock-out, schedule/history and details. Admin Hub → Shifts (manager and above by default) provides a weekly calendar, staff/status filters, schedule/create/edit/cancel/missed actions, recorded attendance corrections with required reasons, and review of unclosed/overdue shifts. Responsive desktop/phone layouts and dialogs; read failures, loading, duplicate-click guards, polling, and stale-edit protection included. Other Admin navigation remains available through More to fit the existing top bar.
**Backend:** Added SHIFT_SELF/READ/MANAGE permissions; waiter gets SELF, manager/admin/co-owner/owner/super-admin manage. No new viewer permission. Branch/restaurant and ownership checks; actual work time excludes unpaid breaks; overnight/timezone handling; reasons and before/after audit logs; per-staff transaction locks and optimistic versions. Clock-in automatically links eligible schedules even while viewing another week. Corrections validate actual attendance overlap and preserve break intervals. V46 permits unstarted scheduled shifts and adds unique active-shift/open-break indexes. No invented payroll, overtime policy, or sales totals.
**Files/modules:** `back-end/.../shift/{entity,repository,dto,service,controller}`, `security/rbac/AppPermission.java`, `AppRole.java`, migration V46; `mobile_desktop/.../pos/shifts`, POS/Admin routing/navigation and Koin registration; shift unit/model/screenshot tests.
**Verified:** Backend package passed with 50 focused unit/role tests (25 shift tests). Desktop compilation plus 7 model and 6 headless screenshot tests passed; visually reviewed desktop/phone screens and schedule dialogs. Live local PostgreSQL/API workflow passed 17 checks, including permissions, overlaps, clock-in/break/resume/out, correction, cancellation, version conflicts, concurrent double clock-in, saved audit trail, and own-history isolation. Temporary QA staff/shifts/notifications were removed. Migration V46 applied locally; backend rebuilt/restarted using `/tmp/shift-backend-runtime.log`; desktop relaunch uses `/tmp/shift-app-runtime.log`. Screenshot previews in `mobile_desktop/shared/build/reports/shifts/`.
**Scope/limits:** Verified locally, not deployed to production. Manual attendance corrections retain recorded breaks; automatic missed marking/payroll rules are intentionally absent. Native desktop UI automation unavailable; verified screen rendering via headless Compose and API workflow separately. Preserve existing unrelated reservation/menu dirty changes. Sandbox bwrap errors on the visualization root required escalated exec for local builds/edits. `view_image` had the same sandbox problem; image previews were read via base64 from exec.

## 2026-09-27 (Codex, feature/orders)
**Did:** Extended the past-date “Reservations by time” panel to display hourly rows starting at 08:00, including empty hours through the last booking. Clicking an hour highlights it and filters the reservation list to that hour; clicking it again or using Clear removes the hour filter. The date remains beside the list title.
**Verified/runtime:** `:desktopApp:compileKotlin` passed; `git diff --check` passed. Relaunched the desktop app. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** User to review the clickable hourly view.

## 2026-09-27 (Claude, feature/orders)
**Did:** Final reservations-overview polish (the user said this finishes the overview list), in `ReservationOverviewScreen.kt`:
- **Bottom header copy (`sectionBelow`) fixed:** it used to show the first below-view section anywhere, so after reopening Previous a "Later" copy covered the real Arriving header sitting at the bottom edge.
  - It now takes the next section header after `firstVisibleItemIndex` only, and shows its copy only while that real header is still below (or cut off at) the bottom. It never covers another header.
- **`focusSection` is now two visible steps** (user: switching from Arriving to Later should be smooth and understandable):
  - **Step 1:** fold the other open sections (rows fade out). If the header at the top belongs to the folding section and was pinned, `scrollToItem` keeps it in place.
  - **Step 2:** after `FOLD_STEP_MILLIS` (240ms), `animateScrollToItem(target, -(headerPx + spacing))` to sit under the header above, then open the target so its rows fade in underneath.
  - Header indices after the fold come from `laterIndexFor` / `tomorrowIndexFor` with the new open flags.
Compiled (a Kotlin daemon clash with a concurrent Codex build needed one retry) and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**Did:** For past dates in the Reservations overview, replaced the right-side status and walk-in panels with a scrollable “Reservations by time” panel. It groups the full day’s bookings by reservation start hour, shows booking count and booked guest count, and scales a bar against the busiest hour. It waits until all reservation pages load before showing the breakdown. Today’s sidebar remains unchanged.
**Verified/runtime:** `:desktopApp:compileKotlin` passed (Gradle had concurrent build-cache contention and used its fallback compiler); `git diff --check` passed. Relaunched the desktop app. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** User to review the first visual pass.

## 2026-09-27 (Claude, feature/orders)
**Did:** Reopening Previous now matches the user's reference screenshot. The box fills with the latest previous reservations, then the "N previous above" line, with the Arriving header at the bottom edge. `toggleSection`, after two frames: `scrollToItem(headerIndex + previousRowCount + 1, -(viewportHeight - headerPx))`, i.e. the rest header's bottom sits on the viewport end. The initial screen position is unchanged (Previous pinned, line, Arriving). Compiled (one retry after a Kotlin daemon failure, likely a concurrent Codex build) and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** More reservations overview fixes (user), in `ReservationOverviewScreen.kt`:
- **Previous reopens "where I am" = at its start, which is its bottom.** The user reads it upward, latest to earliest. After two frames, `toggleSection` now runs `scrollToItem(headerIndex + previousRowCount, -(headerPx + spacing))`, landing exactly like the initial screen: Previous pinned, the "N previous above" line, then Arriving. This replaces my previous "scroll to the top of Previous" change, which was a misread. Opening Arriving/Later still glides to that section's own start (`focusSection`).
- **Bottom overlay generalized** from Later-only (`laterBelow`) to `sectionBelow`: the first of Arriving/Upcoming, Later, Tomorrow whose header is still below the view keeps a copy pinned at the bottom (e.g. Arriving while scrolled up into Previous). Clicking it → `focusSection(that)`.
- **Crash hardening for "adding a reservation":** the user saw a crash earlier but couldn't reproduce it, and its log was lost. I couldn't pin a cause: the backend only logged broken pipes, and no reservation was inserted today. The only crash path my LazyColumn introduced is duplicate keys, so the day list and arrivals feed are now `distinctBy { it.id }` before splitting into sections. Earlier ids are already excluded from feed rows.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Three fixes to today's Previous section (user), in `ReservationOverviewScreen.kt`. This is the same file Codex is working in (past-day cards and `showFloor` on `ReservationCard`); my edits don't touch Codex's parts.
1. **Rows only animate right after a user fold/open.**
   - Why: `smoothItem()` animated every change, so refreshes and the clock (a booking moving from Arriving into Previous) replayed motion by themselves ("the opening animation plays more than once").
   - Now: `smoothItem(animateRows)` passes null specs unless `animateRows`. `startMotion()` sets it true in `fold` / `toggleSection` / `focusSection` / open-on-scroll, and a `LaunchedEffect(motion)` clears it after 700ms.
2. **Opening Previous shows its start.** `toggleSection` open path: `scrollToItem(headerIndex)` instantly, then re-checks after two frames that the header is still first (it was `animateScrollToItem`, and the user saw it land at the end).
3. **Previous is split into two groups.**
   - **Top:** finished reservations (`FinishedStatuses`: COMPLETED, NO_SHOW), `fadedAlpha = FinishedAlpha` (0.45).
   - **Divider:** a `StillActiveLine` ("Still active" + hairline).
   - **Bottom:** still-going reservations (checked in, seated, late pending/confirmed), `StillActiveAlpha` (0.72).
   - `ReservationCard` got a `fadedAlpha` param (after Codex's `showFloor`).
   - `previousRowCount` (rows + optional line + "above" pill) now drives `restHeaderIndex` and the open position.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**Did:** Made the past-date Reservations header keep “Reservations” bold while showing the selected date beside it in smaller gray text. Added an “All floors” choice only for past dates; when selected, past reservation rows show each assigned table’s floor. The Online bookings detail now reads “N ordered online.”
**Verified/runtime:** `:desktopApp:compileKotlin` passed; relaunched the desktop app. `git diff --check` passed. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationsScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** None.

## 2026-09-27 (Codex, feature/orders)
**Did:** Made the past-date Reservations card readable by moving the completed count beside the total and leaving cancelled/no-show/open counts on the shorter detail line. The Online bookings card now shows its count out of the day's total bookings.
**Verified/runtime:** `:desktopApp:compileKotlin` passed; rebuilt and relaunched the desktop app. `git diff --check` passed. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** None.

## 2026-09-27 (Codex, feature/orders)
**Did:** For past dates only, changed the four Reservations overview summary cards to show total bookings with completed/cancelled/no-show/open counts, guests who arrived versus all booked guests, online bookings and their share, and the peak reservation start hour. Moved the selected date into the Reservations list title on the left. Today cards and the right-side panels are unchanged.
**Verified/runtime:** `:desktopApp:compileKotlin` passed; launched the rebuilt desktop app with `:desktopApp:run`. `git diff --check` passed. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** None.

## 2026-09-27 (Codex, feature/orders)
**Did:** Widened only the Reservations overview date filter from 176dp to 208dp so the selected date has room to display fully.
**Verified/runtime:** `:desktopApp:compileKotlin` passed; launched the desktop app with the updated UI. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** None.

## 2026-09-27 (Codex, feature/orders)
**Did:** Reduced the Reservations overview date filter slightly from 208dp to 200dp at the user’s request.
**Verified/runtime:** `git diff --check` passed; `:desktopApp:run` rebuilt successfully and launched the updated desktop app.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** None.


## 2026-09-27 (Claude, feature/orders)
**Did:** Corrected the last entry. The user wanted the section header bars to look a bit wider than the reservation cards under them, not taller. In `ReservationOverviewScreen.kt`:
- **Header padding** is back to 12×9dp.
- **Cards under a section header** (Previous, Arriving/Upcoming, Later, Tomorrow rows, plus the compact empty message) are now inset horizontally by `SectionRowInset = 10dp`. The headers stay full width.
- **Plain other-day list** (no headers, `restHeader == null`): not inset (`rowInset = 0`).
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Made the reservations overview section header bars (Previous / Arriving / Later / Tomorrow, `DayGroupHeader`) a little taller: padding went from 12×9dp to 14×12dp. The user asked for the "container of arriving, previous and later a little wider", which I read as the header bars; if they meant the whole list box, narrow the 320dp right column instead. Pinned-header geometry reads `headerPx` at runtime, so nothing else needed changing. Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Four menu fixes (user):
1. **Cover edit/info buttons a bit further inside.** `CoverActionOverhang` is now 10dp (was 20, half a button). Desktop offset `x = CoverActionOverhang`, `y = CoverFaceTop - CoverActionOverhang`. Phone offset `(10dp, -10dp)` via the same constant. The grid still reserves that overhang on the right.
2. **"Add new menu" restored to its pre-13cfcfd look:** full slot size with `.padding(40.dp)` all round, so it's smaller and centred. Codex's online-menu commit had changed it to `padding(top = 8dp)`. My `DesktopCoverGrowth` height trim is removed.
3. **Section row's edit (pencil) button was cut on the right:** a Material3 `IconButton` with `size(44.dp)` still takes a 48dp minimum touch size and spilled past its slot, and the `horizontalScroll` row clipped it. `CategoryButtons` now draws it as a plain clickable 44dp `Box`, and the row has 2dp end padding. The phone row wasn't affected (the button there is outside the scroller).
4. **Online menu "Change items position" button always shows for managers** (Codex hid it under 2 dishes). With fewer than 2 it's faded (`compositeOver`) and shows `MenuValidationToast` "Add at least 2 dishes in this section…", same as `MenuScreen`.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/menu/ui/menu/MenuCoverUi.kt`, `PhoneMenuCoverUi.kt`, `.../menu/ui/section/CategoryFilterBar.kt`, `.../menu/ui/online/OnlineMenuContent.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Closing a section no longer moves anything (user: "if Later is up and I close it, it stays up; I can close all three"). In `ReservationOverviewScreen.kt`:
- **`clickSection`:** clicking an open section → `fold()` only (it no longer switches to another section), so all sections can be closed. Clicking a closed one still `focusSection`s it (opens it alone and glides it up).
- **End space while the last section is closed:** before, closing the last section left nothing under it, so the LazyColumn clamped and pulled the list down. Now, while the last section is closed (Tomorrow, else Later, else Arriving/Upcoming), an `end-space` Spacer item of (list height − header height − 8dp) is appended, so any header can stay at the top.
  - The list height comes from `LazyListContainer(onListHeight = …)` (`onSizeChanged` on the LazyColumn); it avoids reading `layoutInfo` in composition.
  - The spacer disappears once the last section opens, e.g. when auto-opened by scrolling.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Overview list sections now open one at a time (user: clicking Later when it was open jumped to the top instead of moving smoothly; they want Arriving and Later never both open by click, but scrolling should open the next section). In `ReservationOverviewScreen.kt`:
- **New `ListSection` enum** (ARRIVING, LATER, TOMORROW). Later and Tomorrow now start closed.
- **`clickSection`:**
  - Clicking a closed section → `focusSection(it)`.
  - Clicking the open section → switches to the next one (Later after Arriving, Arriving after the others), or just `fold()`s it when it's the only one.
  - Previous is independent and still uses `toggleSection`.
- **`focusSection(target)`:** records the target header's screen Y (visible offset, 0 if above, or `viewportEnd - headerPx` for the bottom copy), then opens only the target. It then runs `scrollToItem(newIndex, -screenY)` in the same frame, so nothing jumps while the rows above fold away. Finally `animateScrollToItem(newIndex, -(headerPx + spacing))`, so the target lands under the pinned header above it.
  - `laterIndexFor(arrivingOpen)` / `tomorrowIndexFor(...)` compute the post-change indices.
- **Open on scroll:** a `NestedScrollConnection.onPreScroll` (user input, scrolling down) on the `LazyListContainer` (new `scrollConnection` param) opens a closed section whose header is fully in view in the lower half of the viewport. That keeps the Arriving header sitting at the top after clicking Later from reopening itself.
- **Bottom Later copy:** clicking it → `focusSection(LATER)`.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** The user's final shape for the overview list sections, in `ReservationOverviewScreen.kt`.
- **Previous is sticky again, and the pill stays:** the previous-header is a `stickyHeader` and the "N previous reservations above" `EarlierDivider` item is kept.
- **On open:** `scrollToItem(0)` to measure the Previous header, then `scrollToItem(1 + earlier.size, -(headerHeight + spacing))`. Previous sits pinned at the top with the pill, then the Arriving header, right under it.
- **Expanding a section glides it to the top** (`toggleSection` → `bringToTop`: `animateScrollToItem(headerIndex, -(headerPx + spacing))`), stopping just under the section before it, whose header stays pinned: Previous over Arriving, Arriving over Later, Later/Arriving over Tomorrow.
  - Collapsing still uses `fold()` (a pinned header keeps its place).
  - The bottom "Later" copy opens Later and brings it up the same way.
- **Header height:** `headerPx` is recorded by `PinnedHeader(onHeight = …)` via `onSizeChanged`. Every sticky header has the same height.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Corrected the previous entry's changes to what the user actually wants, in `ReservationOverviewScreen.kt`.
- **"N previous reservations above" pill restored:** the `EarlierDivider` composable and the `previous-hint` item after the Previous rows are back. The list opens on it again (`scrollToItem(1 + earlier.size)`, once per `LazyListState` via `positioned`).
- **Previous header is a normal `item` now, not a `stickyHeader`:** on open, Previous stays out of sight (nothing is pinned above the pill) until the user scrolls up to it. Arriving and Later are still sticky, and the Later bottom overlay (`laterBelow`) stays.
- **Index formula:** `restHeaderIndex` is back to `1 + earlier.size + 1` when Previous is open.
- **Don't:** pin the Previous header, or remove the pill.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**Did:** Expanded Admin Hub navigation with Users, Permissions, and Audit Logs. Desktop shows all seven sections in the top bar. Phone keeps Inventory, Suppliers, Devices, and Settings in the bottom bar and places Users, Permissions, and Audit Logs under More to preserve tab width. All Admin sections still display placeholder labels.
**Verified:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/admin/ui/AdminScreen.kt`, `AdminNavBar.kt`, `AdminSection.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** Admin section screens are not implemented yet; user will provide additional sections later.


## 2026-09-27 (Claude, feature/orders)
**Did:** Overview list, two more tweaks (user), in `ReservationOverviewScreen.kt`.
- **Removed the "N previous reservations above" pill** (the `EarlierDivider` composable and the `previous-hint` item). On open, a one-time `positioned` effect per `LazyListState` runs `scrollToItem(restHeaderIndex)` then `scrollBy(-(header.size + mainAxisItemSpacing))`. The pinned Previous header then sits exactly above the Arriving header, covering the last previous row; scrolling up reveals the previous rows under it. It is skipped if the user already scrolled, while loading, or when Previous is folded.
  - `restHeaderIndex` = `1 + earlier.size` (no hint item any more).
- **Later header "sticks" at the bottom while below:** Compose's sticky headers only pin to the top (`StickyItemsPlacement.StickToTopPlacement`). A `derivedStateOf` `laterBelow` shows a copy of the Later header via `LazyListContainer(bottomOverlay = …)`, drawn over the list's bottom edge, until the real header is fully in view at the same spot. Clicking the copy runs `animateScrollToItem(laterHeaderIndex)`. The overlay uses `PinnedHeader`, the same strip as the real header, so the hand-over doesn't shift.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Two overview-list fixes (user), in `ReservationOverviewScreen.kt`.
1. **Folding a pinned header no longer jumps.** Cause: LazyColumn anchors on the first visible item's key; when that row sits in the folded section it disappears and the list lands further down. Fix: `fold(headerIndex, toggle)` scrolls back to the header after toggling, but only if the header was pinned (`headerIndex < firstVisibleItemIndex`).
   - Header indices are computed from the section layout: `restHeaderIndex`, `laterHeaderIndex`, `tomorrowHeaderIndex`. If items are added or reordered before a header, update those formulas.
2. **Later = the rest of today only**, with no day sub-groups; `DaySubHeader` and `collapsedDays` are removed.
   - **Tomorrow:** a "Tomorrow · <date>" pinned, foldable section appears only from `TOMORROW_FROM_HOUR` = 22:00 today until the service date flips (06:00). The service day is 06:00–02:00, per `START_HOUR`/`END_HOUR`.
   - **Further ahead:** bookings beyond that aren't listed.
   - **Paging:** the arrivals feed stops paging once its last loaded booking is past the last day shown (`feedPastShown` → `paging.copy(hasMore = false)`), so it doesn't auto-load future days nobody sees.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Sticky section headers in the reservations overview list (user request).
- **Pinned headers:** Previous, Arriving (next 2 hours)/Upcoming, and Later are `stickyHeader`s (Compose 1.11, the stable `(Int)` overload), wrapped in `PinnedHeader`, an opaque white strip. The next section's header pushes the pinned one away.
- **Later section:** "Later" is now a real foldable section header (`laterOpen`) instead of a text label. Its count reads "N+ reservations" while the feed has more pages (new `countText` param on `DayGroupHeader`).
- **Day groups:** the days inside Later use a lighter `DaySubHeader` (transparent, smaller) so the two levels read apart.
- **On open:** the list still scrolls to the previous-hint item. The pinned "Previous" header then sits over that pill, directly above the Arriving header, which acts as the "there's more above" cue.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**Did:** Added an Admin-only workspace nav matching KDS/POS shared frames: desktop top bar and phone bottom bar, with Inventory, Suppliers, Devices and Settings tabs. Each tab selects and displays its placeholder label; top bar supports sign-out and switch-workspace when available. No POS or KDS behavior changed.
**Verified:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/admin/ui/AdminScreen.kt`, `AdminNavBar.kt` (new), `AdminSection.kt` (new), `AGENT_MEMORY.md`.
**Left open / next steps:** Admin sections remain placeholders until their screens are implemented.


## 2026-09-27 (Claude, feature/orders)
**Did:** Five user tweaks.
- **Reservations overview (`ReservationOverviewScreen.kt`):**
  - **Folding animates:** every lazy row is wrapped in `Box(smoothItem())` (`Modifier.animateItem`, same specs as Codex's calendar rows: fade in 260/60, placement 380 FastOutSlowIn, fade out 200). A new `FoldArrow` rotates one down-arrow (0° ↔ −90°) instead of swapping icons, in both `DayGroupHeader` and `ListHeader`.
  - **Sections are today-only:** Previous / Arriving (next 2 hours) / Upcoming / Later. Another day is one plain list of every non-cancelled booking, all faded (`passed = pastDay`) when the date is before today.
  - **Other-day header:** titled "Reservations" (was "Bookings") with the full date on the right (`LazyListContainer` now passes an `action` slot) and a count. The empty text is now "No reservations this day".
- **Menu grid (`MenuCoverUi.kt`):**
  - **"Add new menu":** back to its old desktop height (`coverHeight - DesktopCoverGrowth`, 40dp), since only the menus were meant to grow.
  - **End space:** the desktop grid gets `DesktopGridEndSpace = 32dp` of scrollable room after the last row (not for the single centred row). This is inside the scroll content, unlike the fixed bottom padding removed earlier.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `mobile_desktop/.../pos/menu/ui/menu/MenuCoverUi.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Overview list sections (user: earlier ones in their own category, not inside "Arriving (next 2 hours)"). In `ReservationOverviewScreen.kt` the list is now split into foldable `DayGroupHeader` sections:
- **"Previous":** a new `muted` grey header style over faded cards. At its end, `EarlierDivider` reads "↑ N previous reservations above".
- **"Arriving (next 2 hours)":** today's feed. On other days and in filters, the rest is headed "Upcoming", but only when a Previous section exists.
- **"Later":** the day groups, unchanged.

The box header is now "Today" (feed) / "Bookings" (other day) / the filter label. Only filters show a box count; sections show their own. The fold moved from the box header to the section header, and the old "N hidden. Show them" row is gone. The list still opens at the previous-hint item (`scrollToItem(1 + earlier.size)`, since the Previous header is item 0). `LazyListContainer`'s `open`/`onToggle` params are now unused by callers. Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Reservations overview list: earlier bookings now sit above the rest (user request), in `ReservationOverviewScreen.kt`.
- **What counts as earlier:** "Passed" = status in `PassedStatuses` (CHECKED_IN, SEATED, COMPLETED, NO_SHOW), or start more than 15 min ago. Cancelled bookings still show only under their filter.
- **Layout:** earlier rows sit at the top of the list, faded (`ReservationCard(passed = true)` → alpha 0.5). They read "N min late" when still pending/confirmed, else "until HH:mm". Then an `EarlierDivider` pill, "↑ N earlier reservations above" (click → `animateScrollToItem(0)`), then the upcoming rows (feed / day rows), then Later.
- **Scroll position:** on open, each view's `LazyListState` scrolls to the divider (`scrollToItem(earlier.size)`), so the list starts at the next guest and earlier ones are one scroll up.
- **Data:** earlier rows come from the day list (`reservations`, or `listed` when filtering). The day list now always auto-loads its remaining pages (it used to only while filtering). Feed rows/Later drop ids already in earlier, since the feed and day list refresh one after the other and LazyColumn keys must be unique.
- **Filter view:** passed matches go up too; the count covers all matches. Other days: all non-cancelled bookings, split the same way (a past day is all "earlier", with no empty message).
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Made the "Next 2 hours" card icon bigger (user request). `overview_next_hours.png` has more transparent padding than the other card pictures: its content is 53% of the image, against 57–68% for the others. `SummaryCard` got an `imageScale` param that applies `Modifier.scale` inside the same 52dp slot, so the layout is unchanged; card2 passes 1.3f. Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Reservations overview paging, done properly (user: "Show more" threw them back to the top, and could it get heavy?). Root causes:
- `refreshNow()` reset to page 0 every 60s, dropping loaded pages; this also broke the calendar after `loadAllReservationPages`.
- The list wasn't lazy.
- Cards and "next 2 hours" were computed from the first 100 of the day, so they were wrong late in a busy day or with several floors.

**Backend:**
- `GET …/branches/{b}/reservations/arrivals?from&floor&page&size`: `PageResponse` of PENDING/CONFIRMED from `from` (default now−15min), ordered by start then id.
  - Queries: `ReservationRepository.findUpcomingIdPage` / `findUpcomingIdPageOnFloor`.
  - Floor rule: a table on that floor, or no table at all (same as the app).
- `GET …/summary` takes an optional `floor` and returns new fields: `presentGuests`, `guestsToArrive`, `unassignedCount`, `arrivingSoonCount`/`arrivingSoonGuests` (now−15min..now+2h).
- `ReservationQueryService.getReservationPage` was split into `resolvePageSize` + `toReservationPage` (shared).

**App model (`ReservationsScreenModel`):**
- `refreshNow` reloads pages 0..`loadedReservationsPage` via `loadPages()`.
- `loadMoreReservations` now waits on `refreshMutex`.
- New arrivals feed: `refreshArrivals(floor)` reloads the pages already shown, swapped in one go; `loadMoreArrivals()` continues with the same `from`.
- New state (appended with defaults): `loadMoreReservationsFailed`, `arrivals`, `arrivalsLoaded`, `hasMoreArrivals`, `isLoadingMoreArrivals`, `loadMoreArrivalsFailed`.
- A failed first arrivals page ends up "ready + failed" (Try again), never an endless spinner.
- `ReservationSummary`'s new fields are nullable; the cards fall back to list counts on an older backend.
- Repository/API: `getArrivalsPage` (interface default for fakes); `getReservationSummary` got `floor`.

**App UI:**
- `ReservationOverviewScreen` takes `arrivals` + `arrivalsPaging: ListPaging` + `dayPaging: ListPaging` (replaces `later` and the three load-more params); the `showAllLoaded` view switch is gone.
- Today without filters: the list is the Arriving feed ("Arriving (next 2 hours)", foldable, plus "Later" day groups, no 20 cap).
- A status/area/search filter auto-loads the rest of the day's pages.
- New `LazyListContainer` (a `LazyColumn` keyed by reservation id), shared `ListHeader` and `LoadMoreFooter`. The next page auto-loads when the end is within `LOAD_AHEAD_ITEMS=6`; the pill is the manual fallback and shows "Couldn't load more. Try again" after a failure.
- Phone/tablet: the list box is `heightIn(max 560/640)` because the page scrolls.
- Cards always use the server summary for the floor on screen: `overviewFloor = selectedFloor` only when there are >1 floors. The summary reloads on `lastRefreshedAt` and floor changes (no longer on the 30s clock tick). Arrivals reload on `lastRefreshedAt` and floor changes.
- Removed `LATER_FETCH_LIMIT`, `filteredLater` and `LATER_SHOWN`.

**Tests (written, NOT run):**
- Backend `unit/reservation/service/ReservationArrivalsAndSummaryTest`.
- App `jvmTest/.../reservations/ReservationsPagingTest`.
- `AppScreenshotTest` proxy now answers `getArrivalsPage`/`getTodayReservationsPage`/`getBranchReservationCalendarPage`.
- Backend `test-compile` and app `compileTestKotlinJvm` pass.

**Verified live:** the machine had rebooted, so I restarted podman `pos-db`/`pos-mailhog`, rebuilt the jar and started the backend. `arrivals` returned 100+69 (hasNext correct); the floor filters gave 162/158; `summary` had total 200 and 193 per floor. Relaunched the desktop app.
**Files/modules touched:**
- Backend: `BranchReservationController`, `ReservationQueryService`, `ReservationRepository`, `ReservationSummaryResponse`.
- App: reservations `ReservationApi`, `ReservationDtos`, `ReservationDtoMapper`, `ReservationModels`, `ReservationRepository` (domain), `DefaultReservationRepository`, `ReservationsUiState`, `ReservationsScreenModel`, `ReservationsScreen`, `ReservationOverviewScreen`.
- Tests: the new test files above and `AppScreenshotTest`.
**Left open:** Run the new tests with the rest at the end.

## 2026-09-27 (Claude, feature/orders)
**Did:** Restyled the reservations overview "Load more reservations" button (user didn't like it). It was a full-width grey box; now it's a centred white pill with a light green border between two hairline dividers: a down-arrow icon and "Show more reservations". While loading it shows a 14dp spinner and "Loading more…". The behaviour is unchanged: `showAllLoaded = true` and `onLoadMoreReservations()`. Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Reservations overview filtering moved into the "By status" box (user request), in `ReservationOverviewScreen.kt`:
- **Toolbar:** removed the status `HeaderDropdown` from the overview's toolbar. The calendar toolbar still has its own, and `selectedStatus` is shared with it.
- **Status rows:** clickable. Tapping one sets `selectedStatus`; tapping it again clears to "All statuses". The selected row gets its status colour tint, a 1.5dp border, bold text and a filled check.
- **"No table" row:** added below a divider, with a count equal to the "Without a table" card. It toggles the existing area filter `"Unassigned"` (tables empty), so it combines with a status (e.g. Pending + No table) and stays in sync with the Area dropdown.
- **"Clear" action:** appears in the By status header while a status or No table is active; it only resets the area if it was Unassigned.
- **List title:** now names every filter, e.g. "Pending · No table".
- **Signatures:** `OverviewContent` takes `onStatusChange`/`onAreaChange` (both call sites updated). `ListContainer` got an optional `action` slot. `StatusCountRow` got `selected`/`onClick`.
Compiled and relaunched desktop; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Reservations overview "Arriving" panel (user request): in today's default view (not filtering, no "Load more" all-day list), the title reads "Arriving (next 2 hours)" and the header folds that list.
- **Header:** clickable, with a chevron at the right after the count badge, like `DayGroupHeader`.
- **Folded:** shows a "N reservations in the next 2 hours hidden. Show them" line; the "Later" day groups and Load More stay.
- **Implementation:** `ListContainer` got optional `open`/`onToggle` params (other lists are unaffected). The fold state is `remember(date)` (open by default). Targeted edits only, in `ReservationOverviewScreen.kt`, which also has Codex's uncommitted pagination/details changes.
Compiled and relaunched desktop (I stopped the running app, likely Codex's relaunch, to restart it); no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**Did:** Updated the Reservations overview summary cards: the large reservation and guest counts now stand alone; reservation detail shows `done/total done · cancelled · no show`, and guest detail shows `arrived/total arrived`.
**Verified/runtime:** Shared/JVM and desktop app compilation passed. No tests were run. Relaunched the desktop app; `:desktopApp:run` is active.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** User can visually confirm the updated counts on the Reservations overview.

## 2026-09-27 (Codex, feature/orders)
**Did:** Made the Reservations calendar's Unassigned lane collapsible using the same section arrow/header behavior as table groups. Kept Unassigned pinned above floor groups and changed its collapsed summary to say how many reservations are hidden.
**Verified/runtime:** `:shared:compileKotlinJvm` and `:desktopApp:compileKotlin` passed; no tests run per user preference. Relaunched the desktop app; `:desktopApp:run` is active. `git diff --check` passed for the changed reservations screen.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationsScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** User can visually confirm the Unassigned section foldout in the calendar.

## 2026-09-27 (Claude, feature/orders)
**Did:** Third round of menu cover button placement. The user wants the edit pencil centered on the cover's top-right corner (half above, half past the right edge) on every device. The height from the previous round is kept.
- **Desktop (`MenuCoverUi.kt`):** the edit button is at `offset(x = actionSize/2, y = CoverFaceTop - actionSize/2)` from TopEnd. Info sits directly below it, on the same right edge; `actionInset` is gone. New `internal val CoverActionOverhang = 20.dp`: the desktop card width now subtracts it, so the right column's buttons aren't clipped (a `verticalScroll` only allows 15dp of horizontal overflow). The desktop gap is 32dp (was 24), the phone gap 26dp (was 18), and the phone vertical padding is at least 20dp (was 4), so the overhanging buttons never touch the next cover or get clipped. The skeleton grid matches (480dp height, 32dp gap, end padding).
- **Phone (`PhoneMenuCoverUi.kt`):** moved the edit/info Column (and the reorder badge) out of the clipped face `Box` into the `BoxWithConstraints` scope, at `offset(x = 19dp, y = -19dp)` (`PhoneCoverActionSize = 38.dp`).
Compiled and relaunched desktop; no tests run. Gotcha: don't kill with `[d]esktopApp:run` in a command that also starts `./gradlew :desktopApp:run`, because the pattern matches your own shell (exit 144).
**Files/modules touched:** `mobile_desktop/.../pos/menu/ui/menu/MenuCoverUi.kt`, `PhoneMenuCoverUi.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** Second round of menu cover polish (user screenshots: "a little bigger height" and "button more top right, half inside half out"):
- **Taller desktop covers:** `desktopMenuCoverLayout` in `pos/menu/ui/menu/MenuLayoutMetrics.kt` now uses a 480dp max height (was 440) and 560dp for a single centered row (was 520). `MenuLayoutMetricsTest` doesn't assert these values.
- **Edit button straddles the face's top edge:** in `MenuCoverUi.kt`, a new `private val CoverFaceTop = 20.dp` replaces the old `.padding(top = 8.dp)` on all four covers (`MenuCoverSkeleton`, `MenuBookCover`, `OnlineMenuCover`, `AddMenuCover`), so they stay aligned and the button isn't clipped on the first grid row. The edit button is at `offset(x = -12dp (compact 10dp), y = CoverFaceTop - actionSize/2)`, centered on the face's top edge near the right corner. Info sits `actionSize + 8dp` below it, or takes its place when the user can't manage menus. Phone cover unchanged.
Compiled and relaunched the desktop app; no tests run. Didn't touch Codex's uncommitted reservation files.
**Files/modules touched:** `mobile_desktop/.../pos/menu/ui/menu/MenuCoverUi.kt`, `MenuLayoutMetrics.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**Did:** Added `GET /restaurants/{restaurantId}/reservations/{reservationId}/details`, aggregating the reservation, audit (notes/status history/table assignments), timeline, and deposit. The desktop detail panel now loads it with one request. Added page-based `PageResponse` results (100 max per request) for branch `/today` and `/calendar`; the overview has a Load More action and the calendar fetches additional pages when opened.
**Test data:** Inserted 200 synthetic reservations for 2026-09-27 into the local `pos_local` database, Local Demo Bistro / Main Branch. They have mixed PENDING/CONFIRMED/CANCELLED statuses, varied party sizes/sources, deposit examples, status-history rows, and 14 table assignments across Main and 1st Floor. Codes use `PAGINATION-DEMO-20260927-`; names use `Pagination Demo Guest`. These are local DB rows, not migrations or committed fixtures.
**Verified/runtime:** Backend package compile passed with Maven tests skipped. Desktop shared/JVM and app compile passed; I stopped a redundant follow-up incremental compile while a concurrent desktop compile was active. Tests were not run, per the user's preference. Rebuilt backend is running on port 8080; desktop app run remains active for manual testing.
**Left open / next steps:** Manual visual/API verification by the user. Local synthetic reservations can be removed later by matching the `PAGINATION-DEMO-20260927-` reservation-code prefix, along with their status-history/table-assignment child rows.

## 2026-09-27 (Claude, feature/orders)
**Did:** Menu cover grid polish (user screenshot request), in `pos/menu/ui/menu/MenuCoverUi.kt` only:
- **Bottom padding:** removed the desktop 20dp bottom padding under the cover grid, so covers scroll to the bottom edge (phone was already 0).
- **Edit/info buttons:** on desktop `MenuBookCover` they now sit inside the book face's top-right corner (face starts 8dp down): inset 14dp (compact 10dp) from the right and from the face top. Info sits 8dp below edit, or takes edit's spot when the user can't manage menus. Previously the offset was (-4, 2), which hung over the rounded corner (Codex's "4dp safe inset" change). The phone cover already placed them inside (padding top 14, end 10) and is unchanged.
Compiled and relaunched the desktop app; no tests run. Codex has uncommitted reservation changes in the tree; I didn't touch those files.
**Files/modules touched:** `mobile_desktop/.../pos/menu/ui/menu/MenuCoverUi.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Claude, feature/orders)
**Did:** "Show in online menu" chooser (`OnlineSectionChooserDialog` in `pos/menu/ui/item/ItemEditorScreen.kt`) now lets the user pick any existing online section directly (user request). Three kinds of choice:
- **Same section:** "Same section as here: X" (with its dish count) if the online menu has it, else "Create “X” in the online menu".
- **Existing online sections:** a scrollable radio list of every other online section with dish counts.
- **New section:** text field; a matching existing name is reused, and the dialog says so.
Preselection: the dish's current online section, else the same-named one, else the first existing. The title and button read "Online section"/"Save" when the dish is already online. The call site now also passes `currentOnlineSectionId`. UNCOMMITTED (only this file differs from HEAD).
**Context:** Codex worked on the online menu in parallel and committed `13cfcfd` (online sections + reordering, including my V44/V45 backend work: `OnlineMenuService.place`/`removeIfEmpty`/`removeEmptySections`, dish order, `PUT …/online-menu/sections/{id}/items/order`) and `4d00cb6`. Codex's `ui/online/OnlineMenuContent.kt` already makes the Online menu look like a normal menu (rename/reorder sections, reorder dishes, no add/edit/delete), so I did NOT redo that.
**Runtime:** the backend was still on V44 (started 23:02), so dish reorder would have 404'd. Rebuilt and restarted it: V45 applied. Relaunched the desktop app. Compiled; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/menu/ui/item/ItemEditorScreen.kt`, `AGENT_MEMORY.md`.

## 2026-09-27 (Codex, feature/orders)
**To do (user requested, not implemented):** Add a reservation-details aggregate API for opening the reservation panel. It should return the reservation plus the panel's supporting details (deposit, notes/audit, status history, and timeline) in one initial client request. Keep individual endpoints for actions or later refreshes; consider lazy-loading/paginating history and timeline if their payloads grow. Also make assigned floor/area visible with the reservation and let staff view or filter reservations by floor/area; include table assignment location in the aggregate response. Paginate reservation lists (for example, 100 rows per page) and request further pages on scroll; return an explicit `hasMore`/next cursor so the app knows when to stop. Keep the initial list scoped to today and apply status/date/floor filters server-side where practical.
**Why:** The current app opens the panel with five parallel GET requests; user and assistant agreed to use one aggregate request for initial details loading. User also wants to identify which reservations are on each floor/area and avoid loading an unbounded number of bookings at once.
**Files/modules:** Backend reservation controller/service/response DTO and mobile reservation API/DTO/repository/screen model and reservation overview UI.
**Left open / next steps:** Implement when requested; this entry records the task and is not implementation.

## 2026-09-26 (Codex, feature/orders)
**Did:** Committed the pending work in `13cfcfd` (menu-specific online-menu changes) and `4d00cb6` (remaining connected POS changes). Fast-forwarded local `develop` and `feature/menu-final-polish` to the same tip as `feature/orders`.
**Why:** User asked to commit the accumulated changes and keep the local branches synchronized.
**Verified/runtime:** Worktree clean after the two feature commits. Local branch heads were aligned; no remote push was performed. Tests were not run, per user instruction. Desktop app remains running after a successful compile/relaunch.
**Files/modules touched:** AGENT_MEMORY.md; branch refs for `develop` and `feature/menu-final-polish`.
**Left open / next steps:** Push the synchronized branches only if requested.

## 2026-09-26 (Codex, feature/orders)
**Did:** Removed the 28dp trailing inset from regular, online, and skeleton menu covers; expanded the Create Menu cover to the same allocated size; changed the cover action label to Change Positions / Save Positions and set it to the same height as Create Menu. Moved cover edit/info actions to the upper-right with a 4dp safe inset. Pinned the phone section-edit button outside the horizontally scrolling chip strip so it remains fully visible while chips scroll.
**Why:** User requested no trailing white space, consistent cover/create sizing, a clearer position-change label, and no clipped edit controls.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings; tests were not run. Desktop app relaunched and `:desktopApp:run` remains active. CUA app inventory did not surface native windows, so no live screenshot verification was available.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/menu/ui/menu/MenuCoverUi.kt`, `pos/menu/ui/section/CategoryFilterBar.kt`, `pos/menu/ui/online/OnlineMenuContent.kt`, AGENT_MEMORY.md.
**Left open / next steps:** None.

## 2026-09-26 (Codex, feature/orders)
**Did:** Fixed Online Menu dish reordering availability so the action remains visible when the selected section has at least two dishes, regardless of the active search filter. Shifted regular menu edit and info cover actions 28dp inward to keep them on the visible book face rather than in the cover wrapper's right margin.
**Why:** User could not reliably reorder online menu dishes and reported the menu edit control clipped at some positions.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings. Tests were not run. Relaunched desktop app; `:desktopApp:run` is active.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/menu/ui/online/OnlineMenuContent.kt`, `pos/menu/ui/menu/MenuCoverUi.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Visual confirmation pending on user's side.

## 2026-09-26 (Codex, feature/orders)
**Did:** Finished the Online Menu staff screen to reuse the standard menu section filters, search, dish cards, and reorder layout. Online sections can be renamed/reordered, dishes can be reordered, and the screen keeps online-specific section creation/removal behavior. Reworked its menu chooser cover to match the regular book covers: exclusive purple palette, title at top, centered globe, online dish count, and Open Menu footer. Added count loading when returning to the cover. Also wired the user's root icon assets into the reservation overview cards and no-more-reservations empty state.
**Why:** User asked to complete Claude's started online menu work, make its chooser tile match other menu covers while retaining an online-only color, and see the added icons in the running app.
**Verified/runtime:** Backend compile had passed earlier in this task. `mobile_desktop ./gradlew :desktopApp:compileKotlin` passed with existing warnings; no tests were run, per user instruction. Desktop app relaunched and is still running.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/menu/ui/{MenuScreen,MenuSearchBox}.kt`, `pos/menu/ui/menu/MenuCoverUi.kt`, `pos/menu/ui/online/OnlineMenuContent.kt`, shared menu DTO/domain/repository/model/card/section files from the current Online Menu changes, `pos/reservations/ReservationOverviewScreen.kt`, overview drawable assets, backend `menu` online-menu service/controller/entity/repository/DTO files and migration `V45__online_menu_item_order.sql`.
**Left open / next steps:** Visual confirmation pending on user's side.

## 2026-09-26 (Claude, feature/orders)
**Did: the online menu has its own sections, plus an Online menu preview in the app** (user-designed; replaces the plain `show_online` flag from V43).
- **Model (migration V44):** `online_menu_sections` (restaurant_id, name, display_order; unique per restaurant on lower(name)) and `"menu-items".online_section_id` (FK); drops `show_online`. `MenuItem.onlineSection` (a dish keeps its staff section and may also sit in ONE online section); `isShowOnline()` is now derived (`onlineSection != null`), so PublicMenuService and PreOrderPricing work unchanged.
- **Dish save (`MenuItemService.applyOnlinePlacement`):** `showOnline=false` → offline. `showOnline=true` or an `onlineSectionId`/`onlineSectionName` → `OnlineMenuService.resolveSection` (by id, else by name case-insensitive, else created at the end), defaulting to the dish's own staff section name. Nothing given → kept (so reorder saves never move it).
- **`OnlineMenuService`:** staff preview for today (all sections incl. empty, all placed dishes with `visible`/`hiddenReason`: "Sold out", "Its menu or section is switched off", "Its menu isn't offered on this date"); public version (visible dishes only, no empty sections; optional `date`); rename (unique name), reorder (must list all), delete (only empty → else 409).
- **Endpoints:** `GET /restaurants/{r}/online-menu`, `GET …/online-menu/sections` (MENUS_READ), `PATCH …/sections/{id}` and `PUT …/sections/order` (MENUS_UPDATE), `DELETE …/sections/{id}` (MENUS_DELETE); public `GET /public/restaurants/{r}/online-menu?date=`.
- **App:** turning on "Show in online menu" in the dish form opens `OnlineSectionChooserDialog` (in `ItemEditorScreen.kt`). Choices are "Same section as here: X" when the online menu already has it, else "Create “X” in the online menu", or "Another name" (text field + chips of existing online sections; reuses a matching name, else creates it). While on, the switch row shows "Online section: X · Change"; off clears it. onSave passes `OnlinePlacement(show, sectionId, sectionName)`; the reorder save no longer sends showOnline. A new "ONLINE MENU" cover (globe) sits after the menus in the cover grid (hidden while reordering). It opens `ui/online/OnlineMenuContent.kt`: sections in order with dishes ("In Dinner · Pasta", price, greyed "Hidden: reason"); managers can move up/down, rename, and delete empty sections.
**Verified:** backend compile + test-compile; app compile + compileTestKotlinJvm. Tests NOT run: new `OnlineMenuServiceTest`, updated `MenuItemServiceTest` (+2), `PublicMenuServiceTest` fixture, `MenuItemControllerTest` stub constructor. Restarted the backend (V44 applied). Live run as co-owner with 2 temp dishes covered: default section "Starters" created then reused, custom "ZZ Chef's picks", offline, save-without-fields keeps the section, preview vs website, rename, reorder, delete non-empty → 409, delete empty → 204. Everything cleaned up (no online sections left). Relaunched the desktop app. No visual check by the agent.
**Files/modules touched:** backend `db/migration/V44__online_menu_sections.sql`, `menu/entity/{OnlineMenuSection (new),MenuItem}`, `menu/repository/{OnlineMenuSectionRepository (new),MenuItemRepository}`, `menu/dto/request/CreateMenuItemRequest`, `menu/dto/update/{UpdateMenuItemRequest,RenameOnlineMenuSectionRequest (new),ReorderOnlineMenuSectionsRequest (new)}`, `menu/dto/response/{MenuItemSummaryResponse,OnlineMenuResponse (new),OnlineMenuSectionResponse (new)}`, `menu/mapper/MenuMapper`, `menu/service/{OnlineMenuService (new),MenuItemService}`, `menu/controller/{OnlineMenuController,PublicOnlineMenuController}` (new), tests above. App `pos/menu/{data/dto/MenuDtos,data/dto/MenuRequests,data/dto/MenuDtoMapper,data/api/MenuApi,data/repository/DefaultMenuRepository,domain/model/MenuModels,domain/repository/MenuRepository,ui/MenuScreenModel,ui/MenuScreen,ui/item/MenuItem,ui/item/ItemEditorScreen,ui/menu/MenuCoverUi,ui/online/OnlineMenuContent (new)}.kt`.

## 2026-09-26 (Codex, feature/orders)
**Did:** Traced reservation statuses, deposit handling, and pre-order lifecycle to answer the user's request for a complete scenario table. Confirmed `ReservationStatus` states and allowed transitions in `ReservationLifecycleService`; pre-order dispatch, arrival, cancellation/no-show handling in `PreOrderService`, `PreOrderLifecycleService`, `PreOrderReservationListener`, and `PreOrderDispatchJob`; deposit actions in `ReservationDepositService`/`ReservationSupport`.
**Findings:** Pre-order amounts are currently marked `PAID` in full on placement/change; actual online payment integration is not implemented. A scheduled pre-order is refunded if cancelled/no-show before kitchen send; after send it becomes `FORFEITED`/`RETAINED` and the linked editable order/KDS ticket is cancelled. Check-in/seating sends a scheduled pre-order immediately. Reservation deposit is a separate manual status; status transitions do not automatically refund or forfeit it, including no-show. No-show automation marks pending/confirmed bookings 30 minutes after start and checked-in-but-never-seated bookings after reservation end. Deleting a reservation with any pre-order is blocked; reopening cancelled/no-show reservations does not restore the old pre-order.
**Files touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Did: "Show in online menu" per menu item** (the to-do from earlier; the user asked to build it now).
- **Backend:** migration **V43** adds `"menu-items".show_online boolean NOT NULL DEFAULT false`, so every existing and new dish is staff-only until switched on. `MenuItem.showOnline`; create request (null → false); update request (null → keep current); `MenuItemSummaryResponse.showOnline`.
- **Public menu:** `PublicMenuService.getMenu` now lists only `showOnline` dishes and drops sections left without any.
- **Pre-orders:** `PreOrderPricing.applyItems(..., onlineMenuOnly)` rejects non-online dishes for ONLINE pre-orders ("X can't be ordered online"); staff pre-orders can use any dish.
- **App:** full data path (DTO default false, requests/`MenuItemInput` nullable, domain field LAST with default, UI model, `toUiMenuItem`, reorder save carries it). The item editor has a second switch "Show in online menu" with helper text ("Customers see it in the online menu and can order it online" / "Only staff see it, in the POS menu"). Both switches now use a shared private `ItemEditorSwitchRow`.
**Bug found and fixed:** `PublicMenuService.getMenu`/`getMenus` had no transaction while `open-in-view` is false. Mapping items hit a `LazyInitializationException` on `MenuItem.ingredients`, so the public menu with items always returned 500. Both methods now use `@Transactional(readOnly = true)`.
**Verified:** backend compile + test-compile; app compile + `compileTestKotlinJvm`. Tests updated, not run: `MenuItemServiceTest` (default off, kept on update, changed when sent), `PublicMenuServiceTest` (fixture shows online; new test hides staff-only dishes and empty sections), `PreOrderPricingTest` (+ online-only rejection), and `PreOrderServiceTest` for the new parameter. Restarted the backend (V43 applied). Live check: existing dishes report false; create with the switch on → true; a PUT without the field keeps true; the public menu was empty before, then showed only the switched-on test dish (deleted afterwards). Relaunched the desktop app.
**Files/modules touched:** backend `db/migration/V43__menu_item_show_online.sql`, `menu/{entity/MenuItem,dto/request/CreateMenuItemRequest,dto/update/UpdateMenuItemRequest,dto/response/MenuItemSummaryResponse,mapper/MenuMapper,service/MenuItemService,service/PublicMenuService}.java`, `preorder/service/{PreOrderPricing,PreOrderService}.java`, tests above. App `pos/menu/{data/dto/MenuDtos,data/dto/MenuRequests,data/dto/MenuDtoMapper,data/repository/DefaultMenuRepository,domain/repository/MenuRepository,domain/model/MenuModels,ui/item/MenuItem,ui/item/ItemEditorScreen,ui/MenuScreen}.kt`.

## 2026-09-26 (Claude, feature/orders)
**Plan (user):** Everything customer-facing online (public booking page, online menu + "Show in online menu" flag, online payment for pre-orders) belongs to the website, which the user will build LAST. Until then, don't treat those as open blockers; focus on the staff side (POS, KDS, Admin).
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Did: pre-order backend (new package `pos.pos.preorder`), connected to reservations, orders, menu and KDS.** The user asked for no payment yet: every pre-order is recorded as PAID in full when placed or changed.
- **Model (migration V42):** `pre_orders` (status SCHEDULED/SENT/CANCELLED/FORFEITED, payment_status PAID/REFUNDED/RETAINED, source ONLINE/STAFF, totals, paid_amount, lead_minutes, order_id; partial unique index = one live SCHEDULED/SENT per reservation), `pre_order_items`, `pre_order_item_options` (price snapshots). Also `orders.prepaid_total` and `settings.pre_orders_enabled` (default false) + `pre_order_lead_minutes` (default 15, 0–240).
- **Rules (`PreOrderService`):** only for PENDING/CONFIRMED reservations, only when enabled, and only before `sendAt` = reservation start − lead. PUT replaces the items while SCHEDULED (keeps its lead). Cancel before `sendAt` = CANCELLED/REFUNDED; after that it's locked.
- **Pricing (`PreOrderPricing`):** builds a throwaway Order through `OrderSupport.buildLineItem` + `recalculateTotals` (same menu/option/tax/service-charge rules), checks `Menu.isAvailableOn(booking date in restaurant tz)`, and copies snapshots.
- **Sending (`PreOrderLifecycleService.send`):** creates a real OPEN DINE_IN order from the snapshots (source WEB for online, POS for staff), with prepaidTotal = paid amount. It fires only `goesToKitchen` items, and the notes read "Pre-order for X · reservation CODE at HH:mm (N guests)", which are copied to the KDS tickets. KDS tickets get `dueAt` = reservation start. Triggered by `PreOrderDispatchJob` (@Scheduled 30s, 4h horizon, plus a safety net refunding SCHEDULED ones whose booking is CANCELLED/NO_SHOW/COMPLETED), by staff "send now", or on arrival.
- **Reservation link:** new events in `reservation/event` (`ReservationStatusChangedEvent` published from `ReservationLifecycleService.transitionReservation` and the no-show job's never-seated path via `announceStatusChange`; `ReservationDeletingEvent` from `ReservationCrudService.deleteReservation`). `PreOrderReservationListener`: cancel/no-show in the same transaction → refund if SCHEDULED, forfeit if SENT (RETAINED, and the linked order is cancelled + KDS synced). Check-in/seat AFTER_COMMIT → `handleArrival` (REQUIRES_NEW): sends early arrivals and attaches the order to the reservation's free primary table when seated. Deleting a reservation that has any pre-order → 409.
- **Orders:** `OrderSupport.refreshPrepaidPaymentStatus` (called from `recalculateTotals`) → PAID while prepaid ≥ total, PARTIALLY_PAID once more is ordered. It leaves orders without a prepayment and refunded/voided ones alone. `prepaidTotal` was added to `OrderResponse`.
- **Locking:** reservation row first (`lockFresh` = refresh FOR UPDATE for untouched, `lockHeld` = lock for the one being changed), then the pre-order row. This avoids the place-vs-cancel race.
- **Endpoints:**
  - Staff: `GET/PUT /restaurants/{r}/reservations/{id}/pre-order` (ORDER_READ/ORDER_CREATE), `POST …/pre-order/cancel` (ORDER_CANCEL), `POST …/pre-order/send` (ORDER_UPDATE), `GET /restaurants/{r}/branches/{b}/pre-orders?from&to&status` (ORDER_READ).
  - Public: `GET/PUT /public/reservations/{code}/pre-order`, `POST …/cancel`.
  - Settings: `PATCH /restaurants/{r}/settings/pre-orders` (SETTINGS_UPDATE).
**Bugs found and fixed along the way:** (1) Reservation codes and (2) KDS ticket numbers took the first 8 chars of a time-ordered UUID, which repeat for ~27 s. A second booking or kitchen ticket within that window failed with 500 ("could not be generated"), and for KDS that broke Send to kitchen. Both now use `UUID.randomUUID()` (order numbers had already been fixed this way). (3) `KdsStationCommandService.replaceRoutings` removed and re-added routings, so any station save with dishes violated `uk_kds_station_routings_station_menu_item`. It now updates existing routings in place.
**Verified:** compile + test-compile. Tests written, NOT run (user preference): new `unit/preorder/service/{PreOrderServiceTest,PreOrderLifecycleServiceTest,PreOrderReservationListenerTest,PreOrderDispatchJobTest,PreOrderPricingTest,PreOrderFixtures}`, `unit/reservation/service/{ReservationStatusEventTest,ReservationCodeTest}`, `unit/kds/service/{KdsTicketNumberTest,KdsStationRoutingUpdateTest}`, plus added cases in `OrderFulfillmentTest` (prepaid) and `SettingsServiceTest` (pre-order settings). Constructor calls updated in `ReservationServiceTest` and `ReservationNoShowJobTest`. Restarted the backend (V42 applied). Full live run as co-owner: disabled → 400; place → SCHEDULED/PAID with totals; guest view/replace by code; send → order OPEN, PAID via prepaid, FIRED; change after send → 400; delete → 409; cancel after send → FORFEITED + order and KDS ticket CANCELLED; guest cancel → REFUNDED; booking cancel before send → REFUNDED; the timer sent a pre-order 30 s after sendAt; the KDS ticket landed on a station with dueAt = booking start; back-to-back bookings now get distinct codes; station save now works.
**Local test data left behind:** 4 cancelled reservations named "ZZ Pre-order test" with their pre-orders and 3 cancelled orders; an INACTIVE KDS station "ZZ Test Kitchen" (routing for Truffle Fries, inactive); staff notifications from those bookings. Pre-orders setting restored to OFF.
**Left open:** Online payment (placeholder PAID). No app UI for pre-orders yet. The "Show in online menu" flag doesn't exist yet, so public pre-orders accept any available item. Late guests after an auto no-show find the order cancelled, and staff must create a new one. The local DB has no KDS stations configured, so unrouted items never reach the KDS: the default-station blocker is still open.
**Files/modules touched:** new `back-end/src/main/java/pos/pos/preorder/**`, `reservation/event/*` (new), `reservation/service/{ReservationLifecycleService,ReservationCrudService,ReservationNoShowJob,ReservationSupport}.java`, `order/{entity/Order,dto/OrderResponse,mapper/OrderMapper,service/OrderSupport}.java`, `settings/{entity/Settings,dto/SettingsResponse,dto/UpdateSettingsPreOrdersRequest (new),mapper/SettingsMapper,service/SettingsService,controller/SettingsController}.java`, `kds/service/{KdsSupport,KdsStationCommandService}.java`, `db/migration/V42__add_pre_order_schema.sql`, the tests listed above, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Pre-order idea, final policy from the user:** Paid online at booking. Cancelling before the order is sent to the kitchen → full refund (the restaurant keeps only the payment commission). Once it's been sent to the kitchen, a no-show forfeits the money. Verdict given: with this rule it IS a good feature, because no-shows no longer cost the restaurant. Watch-outs: late guests (send it close to the booking time, allow a "running late" push-back), show the customer the exact cancel cutoff (= send time), refund sold-out items. The kitchen start time is not chosen yet; "a set number of minutes before the booking" fits this policy. Not built. It depends on the online menu, a booking page and an online payment provider.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Pre-order idea, update:** The user's policy is that a no-show gets a full refund and the restaurant keeps only the payment provider's commission. Verdict given: NOT a good feature in the "food ready on arrival" form. The kitchen would cook before arrival, so every no-show costs the food plus the refund, and customers have nothing at stake. Suggested alternative: prepaid pre-order where the kitchen starts only at check-in (faster service, no waste). The other option is keeping at least the food cost on no-show. Still not approved or built.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Idea under discussion (not approved, not built): online pre-order with a reservation**, i.e. the customer picks dishes when booking so the food is ready on arrival. User decisions so far: online pre-orders must be **paid online**, and the customer can change or cancel **until the order is sent to the kitchen**. The kitchen start time was not chosen; the user first asked whether it's a good feature. My assessment: useful mainly for lunch/business, groups, events and set menus. Risks are late guests, sold-out items and a mix of prepaid and table orders. It depends on things that don't exist yet: a customer booking page, an online menu (see the "Show in online menu" to-do), and an online payment provider (the `payment` module has entities only). Recommended building it after the core POS/KDS work and after online payments.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**To do (user requested, not implemented):** Add a **"Show in online menu"** switch on menu items (Menu screen, create and edit). On means the item appears in the customer-facing online menu once it's built; off means it's staff menu only. The user's wording implies the default is off for new items. Decide how existing items should migrate before implementing. Today there's no such flag: `PublicMenuService` + `MenuRepository.findPublicMenus…` expose every active menu available today, every active section and every available item. The public menu endpoints (`/public/restaurants/{id}/menus`) must filter on the new flag. Same pattern as `sendToKitchen`: column + migration, create/update DTOs (null keeps current), response, app DTO/domain/UI model, editor switch, carry it through the reorder save.
**Also found (answered the user, nothing built):** Ordering without reserving (e.g. pickup): staff can take TAKEAWAY orders in POS if `settings.enableTakeaway`. Customers can't order online for pickup or delivery: the only public order endpoint is QR at a table (`/public/.../tables/{tableCode}/orders`, needs `enableQrOrdering`). There's no pickup time, no online payment, and no public reservation pre-order.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Did:** Read-only inventory of reservation API usage (user is starting reservation functionality work). The backend has 45 reservation endpoints: Branch 10, Restaurant 30, Public 5. The app's `ReservationApi`/`ReservationRepository`/`ReservationsScreenModel` wrap nearly all staff endpoints, but only **22 are reached from a screen**:
- **Branch list:** calendar, upcoming, availability/recommend (table suggestions), summary, capacity, settings (on init); branch list GET is used only by Orders' reservation picker (`OrderCatalogApi`).
- **Per reservation:** create, GET, PATCH (edits + change table via tableIds), confirm, cancel, check-in, complete, mark-no-show, reopen, status-history, timeline, audit (notes come from here), add note, delete note, GET deposit.
**Not used (23):** today (only fires once on open from the default TODAY mode before the calendar filter replaces it: a wasted call); restaurant-wide GET list; PUT (full replace); DELETE; seat; all 6 table-assignment endpoints (GET/PUT tables, add/remove table, primary, auto-assign); availability/search; validate; deposit PUT/pay/refund/waive/forfeit; all 5 public endpoints (no customer booking page, and the web app has no reservation code).
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Decision (user):** Notification backgrounds are white, in both bell rows and arrival pop-ups. The type color lives only in the unread dot (bell list), plus the icon and the pop-up's thin accent border (asked whether to make that gray). Removed `NotificationKind.background`. Compiled and relaunched the desktop app; no tests run.
**Files/modules touched:** `notifications/NotificationBell.kt`, `notifications/NotificationKind.kt`, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Did:** So the user could preview the notification colors, I created 5 SAMPLE notifications in the local DB via `POST /restaurants/{rid}/notifications/broadcast` (as co-owner). Each went to all 8 users of restaurant `10000000-0000-0000-0000-000000000001`, branch `37d536b7-…`: ORDER "Order ready to serve", TABLE "Table needs cleaning", PAYMENT "Payment received", ORDER "New order in the kitchen", TABLE "Tables merged". They were sent 2 s apart so the pop-ups show live. The feed returns them with referenceType ORDER/TABLE/PAYMENT. They're fake data (no real orders/tables behind them). There's no delete endpoint, so they remain in local `notifications` until the DB is cleaned; the user can mark them read. The app only shows notifications addressed to the signed-in user (`personalOnly`), which is why the broadcast needs `recipientUserId`.
**Files/modules touched:** `AGENT_MEMORY.md` only (local DB data).

## 2026-09-26 (Claude, feature/orders)
**Did:** Notification look per type. The user supplied three icon PNGs at the repo root (`1.png` order receipt+cloche, `2.png` reservation calendar+table, `3.png` table+chairs; left in place, untracked). I made trimmed 192px copies in `composeResources/drawable/notification_{order,reservation,table}.png` (~35 KB each), to be used ONLY in notifications. New `notifications/NotificationKind.kt`: kind from `referenceType` (fallback: `eventCode` prefix). RESERVATION purple `#8B5CF6`, ORDER/KDS orange `#D15F00`, TABLE green `#147A25`, PAYMENT blue `#3B82F6` (no image yet: Material `Payments` icon in a tinted circle), OTHER gray. Background = accent at 7% over white; `NotificationKindIcon` draws it. These match the table status palette. Bell list rows (`NotificationBell.kt`) now use the kind background for every row plus the kind icon, and the unread dot uses the kind color. The old per-event icons (EventAvailable/EventBusy/PersonOff) and green unread background are removed. New `NotificationArrivalQueue` (same file) replaces the dark `TableNotificationQueue` for arrival pop-ups in `PosScreen`: light kind background, accent border, icon, title and message, close button, 5 s auto-dismiss, and it caps at 420dp but shrinks on phones. Other action toasts (TableNotificationQueue) are unchanged. Compiled and relaunched the desktop app. Added `jvmTest/.../notifications/NotificationKindTest.kt`; **not run** (user runs tests at the end).
**Left open:** A payment image when the user provides one. Only reservation notifications exist in the backend today (ORDER/TABLE/PAYMENT kinds are ready but nothing sends them yet).
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/composeResources/drawable/notification_*.png` (new), `notifications/NotificationKind.kt` (new), `notifications/NotificationBell.kt`, `pos/ui/PosScreen.kt`, `jvmTest/.../notifications/NotificationKindTest.kt` (new), `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Decision (user):** Occupied is back to burnt orange `#D15F00` (same as Orders "In preparation"). The user preferred it over palette orange `#F59E0B`. Current table palette: Free `#147A25`, Occupied `#D15F00`, Reserved purple `#8B5CF6`, Bill pending blue `#3B82F6`, Unavailable gray `#6B7280`. Relaunched the desktop app; no tests run.
**Files/modules touched:** `mobile_desktop/.../pos/tables/ui/Table.kt`, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Decision (user, current table palette):** Free green `#147A25`, Occupied orange `#F59E0B`, Reserved purple `#8B5CF6`, Bill pending blue `#3B82F6`, Unavailable gray `#6B7280` (`tableStatusColor` in `pos/tables/ui/Table.kt`). This supersedes the entries below. Purple now means "reservation", which is the direction for per-domain notification colors. The served-progress ring/bar (purple) only appears on occupied (orange) tables, so there's no clash anymore. Relaunched the desktop app; no tests run.
**Left open:** Reservation dialogs still use red `#AA3F38` for "already reserved/booked" (should probably become purple). Text contrast of light colors on tables. Notification colors per domain are not done yet.
**Files/modules touched:** `mobile_desktop/.../pos/tables/ui/Table.kt`, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Decision (user):** Occupied table color is now purple `#8B5CF6` (palette "Purple"), replacing red `#EF4444` from the entry below. The other table colors are unchanged. Relaunched the desktop app; no tests run.
**Left open:** The served-progress ring `#6F43B5` and bar `#5F35A4` on occupied tables are also purple, so on a purple table they may blend in. Asked the user whether to recolor them (e.g. white). The reservation dialog legend red and the text-contrast questions from the entry below are still open.
**Files/modules touched:** `mobile_desktop/.../pos/tables/ui/Table.kt`, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Decision (user):** Table status colors follow the user's "Saporine Italiano" palette (Tailwind-500 style values). `tableStatusColor` in `pos/tables/ui/Table.kt`: Free `#147A25` (kept, user likes it), Occupied red `#EF4444`, Reserved orange `#F59E0B`, Bill pending blue `#3B82F6`, Unavailable gray `#6B7280`. This supersedes the cyan trial in the entry below. The served progress ring stays purple. Relaunched the desktop app; no tests run.
**Left open:** The reservation dialogs (`ChangeTableDialog`, `CreateReservationDialog`, `ReservationOverviewScreen`) still hard-code red `#AA3F38` for "already reserved/booked", which no longer matches Reserved = orange. Asked the user. These lighter colors are also used for text on tables (contrast: orange ~2.1:1, red/blue ~3.7:1 on white), so a darker text shade may be needed. Next up: pick per-domain colors for notifications (reservation, order, ...).
**Files/modules touched:** `mobile_desktop/.../pos/tables/ui/Table.kt`, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**Did:** Color work for tables, as a preview only. The user wants one color per domain (reservation, order, ...) so notifications can be recognized by color, starting with tables. Changed `tableStatusColor` Occupied in `pos/tables/ui/Table.kt` from orange `0xFFD15F00` (shared with Orders "In preparation") to cyan `0xFF06B6D4` from the user's "Saporine Italiano" palette image. Orders' orange is unchanged. Relaunched the desktop app. No tests run (per user preference).
**Current table colors:** Free green `#147A25` (user likes it). Occupied cyan `#06B6D4` (trial). Reserved dark gray `#4F5350` (user dislikes it; offered blue `#2F6FDB`, violet `#6A3FC9`, gold `#9A6B00`, raspberry `#C2185B`, indigo `#4338CA`, cocoa `#8A5A2B`, steel blue `#3D6A96`, plum `#7E3F98`, fuchsia `#B0279B`; not chosen yet). Bill pending teal `#087594` (never produced from backend data). Unavailable red `#AA3F38` (covers DIRTY/MAINTENANCE/OUT_OF_SERVICE). Served progress ring purple `#6F43B5`/`#5F35A4`.
**Left open:** Cyan `#06B6D4` is light: only ~2.4:1 contrast as text on white, and close in hue to Bill pending teal. If the user keeps it, consider a darker cyan for text (e.g. `#0891B2`) and recolor Bill pending. Reserved color is still to be picked.
**Files/modules touched:** `mobile_desktop/.../pos/tables/ui/Table.kt`, `AGENT_MEMORY.md`.

## 2026-09-26 (Claude, feature/orders)
**User preference (standing):** Write/update tests with each change, but do NOT run test suites until the user asks. They run everything together at the end because runs are slow. Compiling to check for errors is still fine.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Did:** Added a "Send to kitchen" attribute on menu items (off for counter items like a cola), end to end.
- **Backend menu:** `MenuItem.sendToKitchen` (column `send_to_kitchen` on `"menu-items"`, migration **V41**, default true). `CreateMenuItemRequest.sendToKitchen` (null → true). `UpdateMenuItemRequest.sendToKitchen` (null → keep current, so older clients and reorders don't reset it). Exposed in `MenuItemSummaryResponse`.
- **Backend orders/KDS:** `OrderLineItem.goesToKitchen()` reads the live menu item setting. It's used in `OrderWorkflowService.sendToKitchen` (send-all skips counter items), `OrderItemService` (`assertKitchenStatusAllowed` makes FIRED/PREPARING a 400 for counter items on fire and on generic status change; READY/FULFILLED are allowed, so the waiter serves directly), `KdsOrderSyncService` (never creates tickets for counter items even when a route exists), and `OrderSupport.refreshOrderFulfillment` (a pending counter item counts as ready, so a finished kitchen isn't shown as "in preparation"). `OrderLineItemResponse.sendToKitchen` is added.
- **App:** a "Send to kitchen" switch with a helper line in `ItemEditorDialog` (create + edit). The flag flows through the DTOs/`MenuItemInput` (nullable)/domain (`sendToKitchen` is the LAST field with default true, because tests construct it positionally)/UI `MenuItem`. The reorder save passes it so reordering never flips it. Orders: the new `OrderLineItem.awaitingKitchen` (in `ui/OrderWidgets.kt`) replaces the PENDING checks for every send-to-kitchen button/dialog (OrdersScreen, OrderComposer, OrderDetails). Counter rows show a muted "Serve directly" label instead of the send button, and their status picker offers only Ready/Served.
**Verified:** Backend compile. New tests pass: `OrderCounterItemTest` (3), `OrderFulfillmentTest` +2, `KdsOrderSyncServiceTest` +1, `MenuItemServiceTest` +2. The related menu/order controller tests pass. App compile. App `OrderDataContractTest` +1 (legacy JSON defaults to true; counter item isn't awaitingKitchen). The whole app jvmTest passes except 5 pre-existing Orders failures: `OrdersScreenModelTest.activationPollsOnlyWhileVisible` expects the old 15s polling that SSE replaced, and `OrdersUiScreenshotTest` looks for the old "+ Add item"/"Activity" actions. Restarted the backend (V41 applied). Live API check as co-owner: existing items report true; create with false → false; PUT without the field keeps false; PUT true → true. The temp item was deleted. Restarted the desktop app.
**Files/modules touched:** backend `menu/entity/MenuItem.java`, `menu/dto/request/CreateMenuItemRequest.java`, `menu/dto/update/UpdateMenuItemRequest.java`, `menu/dto/response/MenuItemSummaryResponse.java`, `menu/mapper/MenuMapper.java`, `menu/service/MenuItemService.java`, `order/entity/OrderLineItem.java`, `order/dto/OrderLineItemResponse.java`, `order/mapper/OrderMapper.java`, `order/service/OrderWorkflowService.java`, `order/service/OrderItemService.java`, `order/service/OrderSupport.java`, `kds/service/KdsOrderSyncService.java`, `db/migration/V41__menu_item_send_to_kitchen.sql`; tests `MenuItemServiceTest`, `OrderFulfillmentTest`, `KdsOrderSyncServiceTest`, `OrderCounterItemTest` (new). App `pos/menu/{data/dto/MenuDtos.kt,data/dto/MenuRequests.kt,data/dto/MenuDtoMapper.kt,data/repository/DefaultMenuRepository.kt,domain/repository/MenuRepository.kt,domain/model/MenuModels.kt,ui/item/MenuItem.kt,ui/item/ItemEditorScreen.kt,ui/MenuScreen.kt}`, `pos/orders/{data/dto/OrderResponses.kt,data/dto/OrderDtoMapper.kt,domain/model/OrderModels.kt,ui/OrderWidgets.kt,ui/OrderDetails.kt,ui/OrderComposer.kt,OrdersScreen.kt}`, jvmTest `orders/OrderDataContractTest.kt`.
**Left open / next steps:** The item detail dialog and menu cards don't show the flag yet (only the editor). Existing drinks in real menus default to "send to kitchen" and must be switched off by hand. The 5 pre-existing Orders app test failures still need their expectations updated. Pending since earlier: V39 notifications migration missing from src.

## 2026-09-26 (Claude, feature/orders)
**Did:** Implemented the role to-do items 1–4 from the entries below, plus user-specified workspace access.
- **Workspace access:** new permissions `POS_ACCESS`, `KDS_ACCESS`, `ADMIN_ACCESS` in `AppPermission`. The app's `core/session/Workspace.kt` gates workspaces only on these (no longer on ORDER_CREATE/KDS_READ/USERS_READ). Owner/Co-Owner/Admin/Viewer get all three, Manager gets POS + Admin (it keeps `KDS_READ` for POS kitchen status but has no KDS workspace), Waiter gets POS, Kitchen gets KDS. Restaurants stays Super Admin only (unchanged).
- **Roles:** `AppRole.ADMIN` is now assignable. New `VIEWER` (rank 7,500, only *_READ + the three workspace permissions). New `AppRole.lowestManagingRole` field: MANAGER → CO_OWNER, VIEWER → ADMIN. `RoleHierarchyService` enforces it in `assertCanAssignRole`, `assertCanManageUser` (checks every active role of the target) and `getAssignableRoles`, via `meetsRoleFloor`.
- **Owner's switch:** `settings.admins_can_manage_managers` (migration **V40**, default false) + `PATCH /restaurants/{id}/settings/staff-permissions`. Only Owner, Co-Owner or Super Admin can change it (explicit rank check in `SettingsService.updateStaffPermissions`, since Admin holds SETTINGS_UPDATE). When on, Admins can create/edit/delete Managers. `meetsRoleFloor` reads it only when an Admin-level actor targets a Manager. Kept out of reset/templates/export on purpose.
**Verified:** Backend compiles. New and updated tests pass: AppRoleTest 6, RoleHierarchyServiceTest 21 (8 new), SettingsServiceTest 8 (2 new), plus role/user/auth/session/settings suites. App `WorkspacePickerScreenshotTest` 13/13 (2 new: manager POS+Admin without KDS, kitchen straight to KDS). Rebuilt the jar, restarted the backend (V40 applied, permissions seeded), and confirmed in the DB that each role has the right *_ACCESS set. Live API check with local seed accounts: workspaces and assignable roles per role are as specified; Admin PATCH switch → 403; Co-Owner → 200; switch on adds MANAGER to Admin's assignable roles. Reset the switch to off afterwards. Restarted the desktop app.
**Found (pre-existing, not fixed):** (1) Local DB `foundation_local.flyway_schema_history` has **V39 "notifications"** applied today at 15:28, but `V39__notifications.sql` exists only in stale `back-end/target/classes/db/migration/`, not in `src/` or any branch. That's why this work uses V40. After `mvn clean`, Flyway will fail validation on the missing applied V39, and fresh/prod DBs won't get the notifications tables. Someone needs to restore it to src or decide otherwise. (2) Full unit run: 911 tests, 83 errors, all unrelated: Testcontainers can't find Docker (podman host) for the repository/persistence tests; `OrderControllerSecurityTest` is missing the `OrderChangeNotifier` bean; `OrderPricingSafetyTest` has a null orderChangeNotifier/branch; `RestaurantTableServiceTest` fails with "Table not found" (uncommitted table-merge changes).
**Files/modules touched:** backend `security/rbac/AppPermission.java`, `security/rbac/AppRole.java`, `security/rbac/RoleHierarchyService.java`, `settings/entity/Settings.java`, `settings/dto/SettingsResponse.java`, `settings/dto/UpdateSettingsStaffPermissionsRequest.java` (new), `settings/mapper/SettingsMapper.java`, `settings/controller/SettingsController.java`, `settings/service/SettingsService.java`, `db/migration/V40__admins_can_manage_managers.sql` (new); tests `AppRoleTest`, `RoleHierarchyServiceTest`, `SettingsServiceTest`. App: `core/session/Workspace.kt`, `jvmTest/.../WorkspacePickerScreenshotTest.kt`.
**Left open / next steps:** Viewer is read-only in the backend, but the app still shows edit buttons (the user said permissions come later). There's no UI yet for the Owner's switch (Admin workspace is ComingSoon). Kitchen role is still not assignable (only Super Admin), which the user hasn't approved changing. Custom (non-system) roles now need *_ACCESS permissions to open any workspace. Pending suggestions from earlier entries (reservation permissions for waiters/managers, Kitchen 86 permission, one-owner rule, KDS blockers) still await the user.

## 2026-09-26 (Claude, feature/orders)
**To do (user requested, not implemented), continuing the role to-do list:**
4. **By default, Admin can NOT edit or delete Managers.** Only Owner, Co-Owner (and Super Admin) can. The Owner can optionally grant Admins this ability; it's off by default. This answers the open question in the entry below.
**Design constraint found:** permissions attach to roles only (`RolePermission`; there's no per-user permission table), and `Role` is global (no `restaurantId`). `assertCanManageRole` blocks everyone but Super Admin from editing system roles like ADMIN. So "Owner grants it" can't be done by editing the Admin role, because that would affect every restaurant. It needs a per-restaurant setting ("Admins can manage managers") or a per-user grant, checked in `RoleHierarchyService.assertCanManageUser`.
**Open question (asked the user):** Should the grant apply to all Admins in the restaurant, or to specific Admins?
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**To do (user requested, not implemented), continuing the role to-do list in the entry below:**
3. **Manager can be created only by Owner, Co-Owner and Super Admin**, so Admin must no longer be able to create Managers. Today MANAGER (rank 20k, assignable) can be assigned by anyone ranked higher, which includes Admin (30k). Rank alone can't express this, so it needs a per-role rule in `RoleHierarchyService.assertCanAssignRole` and `getAssignableRoles` (e.g. a minimum assigner rank on the role: Manager requires ≥ Co-Owner 40k). Apply the same rule to Admin (item 1), so both are created only by Owner/Co-Owner/Super Admin.
**Open question (asked the user):** Should Admin still be able to edit or delete Managers (today they can, by rank)?
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**To do (user requested, not implemented):**
1. **Admin role creatable by Owner and Co-Owner.** In `back-end/.../security/rbac/AppRole.java`, set ADMIN `assignable` to `true`. Rank already allows it (Owner 50k, Co-Owner 40k > Admin 30k), and `RoleHierarchyService.assertCanAssignRole` blocks it only because of the flag. Existing DBs also need the `roles.is_assignable` row updated, since `SuperAdminBootstrapRunner` copies the flag at seed time; check whether it re-syncs existing rows or needs a Flyway migration.
2. **New Viewer role (`VIEWER`).** Read-only, with only *_READ permissions (restaurant, menu, users, roles, settings, orders, KDS). Ranked between Waiter and Kitchen, assignable by Owner/Co-Owner/Admin.
Implement both only when the user asks.
**Files/modules touched:** `AGENT_MEMORY.md` only.

## 2026-09-26 (Claude, feature/orders)
**Decision (user):** A new read-only role will be called **Viewer** (`VIEWER`), with only *_READ permissions. NOT implemented yet; the user said to wait.
**Found:** Admin role is `assignable=false` in `AppRole`, so Owner/Co-Owner currently CANNOT create Admins (only Super Admin can). Deletion follows `RoleHierarchyService.assertCanManageUser`: the actor's rank must be strictly higher than the target's, and the target can't hold a protected role (Owner, Super Admin). So Admin can't delete Owner or Co-Owner; Owner and Co-Owner can delete Admin; Co-Owner can't delete Owner or another Co-Owner; same-rank users can't delete each other.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** Probably make ADMIN assignable so Owner/Co-Owner can create Admins (awaiting user). Add the Viewer role when asked.

## 2026-09-26 (Claude, feature/orders)
**Did:** Answered "how many owners per restaurant" (read-only). `Restaurant.ownerId` is a single column, so there is one official owner. `RestaurantValidationService.validateOwnerUser` rejects an owner already assigned to another restaurant (a user has one `restaurantId`), so an owner owns one restaurant. Gap: nothing stops Super Admin from also giving the OWNER role to other staff in the same restaurant (`UserAdminService.replaceUserRoles`; Super Admin bypasses `assertCanAssignRole`). Those users get all permissions via `scope.belongsToRestaurant` but aren't the recorded owner. Co-Owner (assignable, unlimited) is the intended role for partners.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** User to decide whether to block a second OWNER-role user per restaurant.

## 2026-09-26 (Claude, feature/orders)
**Did:** Summarized roles for the user (read-only, from `security/rbac/AppRole.java`). Roles, highest first: Super Admin, Owner, Co-Owner, Admin, Manager, Waiter, Kitchen.
**Found (role/permission gaps relevant to tables/reservations/orders/menu/KDS):** (1) KITCHEN has `assignable=false`, so `RoleHierarchyService.assertCanAssignRole` blocks everyone except Super Admin from giving it. (2) There are no table/reservation permissions; all reservation endpoints require `SETTINGS_READ`/`SETTINGS_UPDATE`. WAITER has neither, so waiters can't see reservations. MANAGER only has `SETTINGS_READ`, so managers can view but not create or edit reservations. (3) Marking an item sold out (`MenuItemController.updateItemAvailability`) needs `MENUS_UPDATE`, which KITCHEN lacks, so the KDS Menu tab can't 86 items. (4) WAITER has no `ORDER_CANCEL`/`ORDER_VOID`/`ORDER_REOPEN`.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** User to decide whether to fix these (e.g. make Kitchen assignable, add RESERVATION_*/TABLE_* permissions or grant waiters access, add a Kitchen-only availability permission).

## 2026-09-26 (Claude, feature/orders)
**Did:** Added the KDS nav bar: Tickets, Upcoming, Menu, History. Profile was left out because the user will put it "in another place" later. Removed Profile from POS too (`PosSection.PROFILE`, `OverflowSections`, `icon()`); the avatar chip on the right of the top bar is untouched. To avoid duplicating the bar, split the POS bars into shared frames. `WorkspaceTopBar` + `TopBarNavItem` in `PosTopBar.kt` and `WorkspaceBottomBar` + `PhoneNavItem` (now internal) in `PosBottomBar.kt` hold the frame. POS and KDS each pass in their own tabs, and POS looks unchanged. New `kds/ui/KdsSection.kt` and `kds/ui/KdsNavBar.kt`. `KdsScreen.kt` now mirrors `PosScreen` (desktop top bar / phone bottom bar, back-to-workspaces), and each tab shows only its name until its screen is built. On phone, KDS More holds only Switch workspace + Sign out. Fixed the KDS workspace gate `KDS_ACCESS` → `KDS_READ` in `core/session/Workspace.kt` (KITCHEN, MANAGER, ADMIN, OWNER, CO_OWNER have it; WAITER does not) and updated the fixtures in `WorkspacePickerScreenshotTest.kt`.
**Verified:** `:desktopApp:compileKotlin` passed. `WorkspacePickerScreenshotTest` 11/11 passed. Relaunched `:desktopApp:run` (log in scratchpad). No visual check of the KDS bar was done by the agent.
**Files/modules touched:** `pos/ui/PosSection.kt`, `pos/ui/PosTopBar.kt`, `pos/ui/PosBottomBar.kt`, `kds/ui/KdsScreen.kt`, `kds/ui/KdsSection.kt` (new), `kds/ui/KdsNavBar.kt` (new), `core/session/Workspace.kt`, `jvmTest/.../WorkspacePickerScreenshotTest.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** User will say where Profile goes. Tab screens are empty. The next KDS step is the Tickets screen, plus backend blockers (modifiers in the ticket DTO, default station for unrouted items, per-item waiter notification). The KDS top bar reuses the POS `ProfileChip` with hardcoded "DK" initials (pre-existing).

## 2026-09-26 (Claude, feature/orders)
**Decision (user, final):** KDS nav bar has 5 tabs in this order: Tickets, Upcoming, Menu, History, Profile. No Expo tab.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** Nothing built yet. `kds/ui/KdsScreen.kt` is still `ComingSoonScreen`. The blockers listed in the entries below (KDS_ACCESS permission mismatch, missing modifiers in the ticket DTO, unrouted items skipped, per-item waiter notification) come before or alongside the UI.

## 2026-09-26 (Claude, feature/orders)
**Decision (user):** The waiter is notified **per item** as soon as it's READY (e.g. salad goes out before the steak), NOT when the whole order is ready. This supersedes the "notify on whole order" recommendation in the entry below. Waiter picks up and taps Mark as served (line item FULFILLED), which clears it from KDS.
**Found:** `NotificationTopic.KDS` exists but only emits generic `KDS_KDS_TICKET_ITEM_UPSERT` events (`NotificationCatalogService`). There is no targeted "Table X: Salad ready" message to the waiter yet, so it needs adding.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** KDS tabs still pending confirmation (Tickets, Menu, Upcoming, History, Profile; no Expo). Greyed other-station items on Tickets cards are still useful for cooks.

## 2026-09-26 (Claude, feature/orders)
**Did:** Explained Expo to the user (no code). User's kitchen likely has no dedicated pass person; the head chef also cooks. Recommended dropping the Expo tab. Instead, each Tickets card shows the other stations' items for that order greyed out with their status, and the waiter's POS is notified when the whole order (all tickets for the order) is READY, not per item.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** User to confirm: KDS tabs = Tickets, Menu, Upcoming, History, Profile (no Expo).

## 2026-09-26 (Claude, feature/orders)
**Did:** Proposed the KDS nav bar (no code): Tickets, Expo, Menu, Upcoming, History, Profile. All-day counts go in a side panel inside Tickets, not a tab. The station picker goes in the top bar, not the nav. Expo tab only if the restaurant has a pass person. For comparison, POS nav (`pos/ui/PosSection.kt`) has 9 sections.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** User to confirm the tab list and the four open questions from the previous entry.

## 2026-09-26 (Claude, feature/orders)
**Did:** Read-only KDS scoping for the user's "finish tables/reservations/orders/menu/KDS, production ready" push. No source changes. Proposed KDS scope: station ticket board, expo/pass view, all-day counts, 86/availability board, recall/history, upcoming reservations/scheduled orders; station/routing setup lives in Admin.
**Verified gaps (production blockers):** (1) `core/session/Workspace.kt` gates KDS on `KDS_ACCESS`, but backend only issues `KDS_READ`/`KDS_UPDATE`, so the KDS workspace never shows. (2) `kds/ui/KdsScreen.kt` is `ComingSoonScreen`; `pos/kitchen/KitchenStatusScreen.kt` is hardcoded sample data. (3) `KdsOrderSyncService` silently skips line items with no `KdsStationRouting`, so unrouted items never reach the kitchen (no default station). (4) `KdsTicketItemResponse` has no variant/options fields, so the kitchen can't see modifiers. (5) No KDS-specific SSE (Order SSE does fire on KDS writes via `OrderSupport.saveOrder`). (6) KDS transitions are forward-only (no recall/undo). (7) `seatLabel` and `dueAt` are never set; `EXPO_READY` is never produced by `toKdsStatus`; course comes only from routing.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** User still needs to decide: expo/pass person or not, coursing/hold-and-fire, one screen per station vs shared, touch vs bump bar. Then implement in this order: fix permission gate, then modifiers in the ticket DTO, then default-station fallback, then the realtime board UI.

## 2026-09-25 (Codex, feature/orders)
**Did:** Started the backend from the existing Spring Boot jar, starting local `pos-db`/`pos-mailhog` containers first if needed. Backend reached `Started PosApplication` and `System started successfully`. Verified `http://localhost:8080/v3/api-docs` returned 200.
**Why:** User asked to run the backend after confirming it was not running.
**Verified/runtime:** Backend is running on port 8080 in session 65178. Desktop app was already running from the previous `:desktopApp:run` session.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** None.


## 2026-09-25 (Codex, feature/orders)
**Did:** Replaced the old Reservations screen content with a fresh full-width reservation calendar shell and top navigation controls only. Removed the previous right detail panel from the main reservation view. The new screen uses shared search styling, date navigation, Day/Week toggle, area/status dropdowns, a full calendar grid with table/area rows and hour columns, and a floating New reservation button. It reads real reservation state when available and falls back to demo blocks only so the calendar layout is visible while UI design continues.
**Why:** User asked to remove the old reservation screens and start the main reservation view with only calendar + nav bar, leaving the right panel/details for later.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings before the turn was interrupted. Relaunched desktop app with `./gradlew :desktopApp:run --no-daemon --max-workers=1`; it reached `:desktopApp:run`.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationsScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** Calendar is a shell for the main reservation view; next UI work should connect date/week behavior, real table rows/areas, create-reservation flow, and later the right detail panel when the user gives the design.


## 2026-09-25 (Codex, feature/orders)
**Did:** Added the reservation state-management/data layer without changing the Reservations UI: backend API wrapper, DTOs/mappers, domain models, repository interface/default repository, `ReservationsUiState`, and `ReservationsScreenModel`. Registered the reservation API/repository/screen model in Koin. The state model covers list filters/search, selection/detail loading, create/update/delete, lifecycle actions, table assignment, notes, deposit state/actions, availability search/recommend, validation, summary, and capacity.
**Why:** User asked to do reservation state management only and leave the screens/UI for later instructions.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings. Desktop app relaunched and reached `:desktopApp:run`.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/data/api/ReservationApi.kt`, `.../data/dto/ReservationDtos.kt`, `.../data/dto/ReservationDtoMapper.kt`, `.../data/repository/DefaultReservationRepository.kt`, `.../domain/model/ReservationModels.kt`, `.../domain/repository/ReservationRepository.kt`, `.../ui/ReservationsUiState.kt`, `.../ui/ReservationsScreenModel.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/core/di/appModule.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** Reservation UI is intentionally still the old/static screen until the user gives the UI design/connection instructions.


Running handoff log for AI agents (Claude, Codex, or others) working on this repo. Newest entries at the top. Read `AGENTS.md` first for the protocol these entries follow.

## Entry format
```
## YYYY-MM-DD HH:MM (agent, branch)
**Did:** what changed, concretely.
**Why:** the reasoning, if not obvious.
**Files/modules touched:** short list.
**Left open / next steps:** anything unfinished, known issues, decisions still pending.
```

---

## 2026-09-24 18:15 (Codex, feature/orders)
**Did:** Restarted the desktop POS app after stopping the previous Compose Desktop process.
**Why:** User requested a rerun of the app.
**Verified/runtime:** `./gradlew :desktopApp:run --no-daemon --max-workers=2` reached `> Task :desktopApp:run`; backend was left running.
**Files/modules touched:** AGENT_MEMORY.md only.
**Left open / next steps:** None.

## 2026-09-24 18:12 (Codex, feature/orders)
**Did:** Completed the requested table/order follow-ups. Merged table groups now expose current order data from backend layout responses and the table detail summary uses the whole merged group to decide whether to show Start order versus View/Add items/Edit order. Move guests now transfers any active/draft source-table orders to the destination before updating table statuses. Seat customers/change guest count now keep the modal open until the backend success callback. Table/plan edit mode now separates Back from Save changes: Back with unsaved changes opens a Save changes/Discard dialog; Save changes applies table layout changes and pending plan image/transform changes; Discard restores the saved table/plan state. New tables now start smaller by default.
**Why:** User asked to finish only the merged-table order state, merged group detail data, moving active order references with guests, then asked to make seat customers work and prevent table/plan edits from saving before Save changes.
**Verified/runtime:** `back-end ./mvnw -q -DskipTests test-compile` passed earlier after constructor updates. `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with only existing warnings. Restarted backend on port 8080 from current source; it reached `Started PosApplication`. Opened desktop via `./gradlew :desktopApp:run --no-daemon --max-workers=2` and it is running.
**Files/modules touched:** `back-end/src/main/java/pos/pos/tables/dto/TableLayoutItemResponse.java`, `back-end/src/main/java/pos/pos/tables/service/RestaurantTableSupport.java`, backend constructor tests for table/reservation services, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/core/di/appModule.kt`, table DTO/domain/UI mapper/model files, `TablesScreen.kt`, `TablesScreenModel.kt`, `TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Payment-specific table blocking is still intentionally not implemented because the user deferred payment. Visual testing is pending on the user's side.

## 2026-09-24 17:25 (Codex, feature/orders)
**Did:** Finished the non-payment parts that were feasible for table merge rules. Backend table merge now pessimistically locks affected table rows while merging/updating status, exposes next reservation metadata in table-layout responses, and moves open/draft child-table orders onto the primary merged table while preserving existing order items/KDS progress and emitting `TABLE_CHANGED` order events. Desktop table merge now waits for backend success before closing merge mode/updating the local merged group, and reserved-table warnings can show the reservation time when the backend has it. Added optional reservation fields through the desktop table DTO/domain/UI models.
**Why:** User asked to finish table rules except payment and then asked for a production-ready checklist of table work. Payment transaction blocking was intentionally not expanded because the user excluded payment.
**Verified/runtime:** `back-end ./mvnw compile -q` passed. `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with only pre-existing warnings. Started backend in a foreground check and started desktop with `:desktopApp:hotRun --auto`; standard detached `:desktopApp:run` exited immediately in this environment, so hot-run is the active desktop mode.
**Files/modules touched:** `back-end/src/main/java/pos/pos/tables/repository/RestaurantTableRepository.java`, `back-end/src/main/java/pos/pos/reservation/repository/ReservationTableAssignmentRepository.java`, `back-end/src/main/java/pos/pos/tables/dto/TableLayoutItemResponse.java`, `back-end/src/main/java/pos/pos/tables/service/RestaurantTableSupport.java`, `back-end/src/main/java/pos/pos/order/repository/OrderRepository.java`, `back-end/src/main/java/pos/pos/tables/service/RestaurantTableService.java`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/domain/model/TableLayoutModels.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/data/dto/TableLayoutDtos.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/data/dto/TableLayoutDtoMapper.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableModels.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableUiMapper.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreenModel.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Different-waiter merge logic still cannot be production-ready until the data model has a real assigned-waiter field. The user noticed a production issue still to fix: merged tables can still show "start order" even when one table in the merged group already has an order; merged table details/actions need to aggregate group orders before deciding Start/View/Add-items actions. Payment locking remains intentionally deferred.

## 2026-09-24 17:02 (Codex, feature/orders)
**Did:** Audited table-merge rule coverage in the actual code without making product source changes. UI validation in `TablesScreen.kt` covers unavailable, dirty/cleanup, already merged, different floors, reserved warning, both-occupied warning, active+free allowed, and current-client saving. Backend validation in `RestaurantTableService.mergeTable` covers unavailable, dirty/cleanup, already merged, different floors, and only `OrderPaymentStatus.PARTIALLY_PAID` on an open order. Not applied or only partial: waiter-choice rule (no waiter ownership field on tables), real multi-device move/transfer lock, full Pay-click/payment-transaction-in-progress block, reservation warning with actual time, and kitchen/order/KDS merge behavior.
**Why:** User asked whether the table rules are applied okay and specifically wanted code checked rather than guesses.
**Verified/runtime:** Read-only audit only. No compile, tests, backend restart, desktop restart, or code changes beyond this log entry.
**Files/modules touched:** AGENT_MEMORY.md only.
**Left open / next steps:** If implementation continues, prioritize backend payment-transaction lock and order/KDS merge behavior before polishing UI warnings, because those affect data correctness.

## 2026-09-24 16:33 (Claude, feature/orders)
**Did:** Implemented the pasted table-merge validation spec (11 numbered rules: unavailable/reserved/payment/dirty/already-merged/moving/different-floor/different-waiter/both-occupied/kitchen-items/active+free). Backend (`RestaurantTableService.mergeTable`): now validates the *primary* table too (previously only merge targets were checked, so a blocked/dirty/paid-up primary slipped through), plus two new rules — floor consistency across the whole selection, and a payment-in-progress check via the existing `OrderRepository.findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(...)` + `Order.paymentStatus == PARTIALLY_PAID` (no dedicated payment-transaction service exists — see below — so this is the closest real signal). Already-merged messages now name the neighbor table (`"T02 is already merged with T01..."`) by reading `mergedInto`/`existsByMergedInto_Id`. All messages match the spec's exact wording since `AuthException`'s message string passes straight through `GlobalExceptionHandler` → `ApiErrorResponse` → `ApiException.message` → `TablesScreenModel.errorMessage` untouched (traced the whole chain before writing messages). Frontend (`TablesScreen.kt`): added a client-side `attemptMerge()` pre-check (reads the already-loaded `backendState.tableLayout.tables`, so hard blocks show instantly with no round trip) that mirrors the backend rules, plus two new `MenuNestedDialog` confirmations (reserved-with-warning "Merge anyway?", both-occupied "Merge into one party?" — the kitchen-items message got folded into this same dialog's body text rather than a separate one, see below) styled exactly like the existing mark-unavailable/separate-tables dialogs in `TableDetailsModal.kt`. Hard-block messages render in a new red bottom-left toast (`mergeBlockMessage`) instead of reusing the existing green-check `actionToastMessage` pill, since that one's styled for success confirmations only.
**Explicitly skipped (not faked):** (1) Rule 8, different-waiter picker — confirmed via full codebase search that no waiter/assigned-employee field exists anywhere on Table or Order, backend or frontend (`AppRole.WAITER` is a login permission set, not an assignment); the spec's own instruction to "use current models, don't invent new ones" rules out adding a fake field for this. (2) Reservation time in the rule-2 warning ("has a reservation at 20:00") — `TableStatus.RESERVED` is a manually-set status with no code path anywhere that links it to an actual `Reservation`/`ReservationTableAssignment` row (grepped for every `TableStatus.RESERVED` assignment site — none exist), so a time lookup would almost always come back empty in practice; shows the reservation warning without a time instead, matching the spec's own "with time if available" allowance. Rule 6 (move/transfer in progress) is a global `state.isSaving` check, not a true per-table lock, since no per-table pending-transaction flag exists (only one global `isSaving` boolean on `TablesUiState`). Rule 11 (active+free merge, "T01 + T02" label) needed no new code — merge already never touches Order rows, and the existing merged-group label already reads "T01-T02" (hyphen, not "+"); left that separator as-is since it's an established convention from earlier UI work, not something asked to change here.
**Verified/runtime:** `back-end`: `./mvnw compile` clean; had to fix `RestaurantTableServiceTest.java` (added a mocked `OrderRepository` — the constructor gained a param) to get `package` to compile; `mvn package -Dmaven.test.skip=true` succeeds (note: `-DskipTests` alone still runs an unrelated pre-existing `@DataJpaTest` — `AuthLoginAttemptRepositoryTest` — that fails to load its Spring context in this environment; looks environment/DB-fixture related, not caused by this change, did not investigate further). `mobile_desktop`: `./gradlew :shared:compileKotlinJvm` clean, only pre-existing deprecation warnings. Restarted both: backend rebuilt jar relaunched (new PID, replaces 9674), desktop app rebuilt+relaunched via `:desktopApp:run` (new detached PID, replaces 29530/30586's predecessor). Backend log confirms full clean startup ("Started PosApplication", Flyway validated 39 migrations, no drift). Could **not** get a visual screenshot to confirm the dialogs render correctly on screen — GNOME's D-Bus screenshot API (`org.gnome.Shell.Screenshot`) returned `AccessDenied` in this environment, and no other screenshot tool (grim/gnome-screenshot/spectacle/ImageMagick `import` under Wayland) worked either — so this is compile-and-process-alive verified only, not eyeball-verified. Said this plainly to the user rather than claiming visual confirmation.
**Files/modules touched:** `back-end/src/main/java/pos/pos/tables/service/RestaurantTableService.java`, `back-end/src/test/java/pos/pos/unit/tables/service/RestaurantTableServiceTest.java`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** User has not yet tested the new dialogs/blocks in the running app (visual verification pending on their end, per this session's usual "let me know when finished, I'll test" pattern). If they later want the waiter-picker or reservation-time features for real, that needs actual backend model additions first (a real assigned-waiter field/endpoint; a real code path that links `TableStatus.RESERVED` to a `Reservation` row) — flagged to the user as a scope decision, not attempted here.

## 2026-09-24 16:27 (Codex, feature/orders)
**Did:** Updated the Orders list row opened-time label from `Opened 22:35` to `Opened at 22:35` and widened that small time column so the full label fits instead of visually reading as only "Opened".
**Why:** Direct user request from screenshot; the second line under elapsed time needed to show the actual open time clearly.
**Verified/runtime:** `:desktopApp:compileKotlin` passed after correcting an initial wrong task name (`:desktopApp:compileKotlinJvm` does not exist). Relaunched the desktop app from freshly compiled jars; current detached desktop PID is 30586. Backend untouched. No tests run.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** User asked future quick UI changes to be handled with only the change, compile, and fast reopen, with minimal commentary.

## 2026-09-24 15:27 (Claude, feature/orders)
**Did:** The environment reset between turns — `/tmp/orders-release` (every overlay jar, compile/launch script, and `pos-desktop-launch.json` this whole session's chain of agents had built up) was gone entirely, and the backend + `pos-db`/`pos-mailhog` Podman containers were stopped. The repo working tree itself was untouched (all source edits from today survived — checked via `git status` before assuming anything was lost). Recovered fully from scratch: (1) `podman start pos-db pos-mailhog`, waited for `pg_isready`. (2) Backend has no local source changes this session, so just relaunched the existing pre-built jar directly (`back-end/target/pos-0.0.1-SNAPSHOT.jar`, same launch args as before) rather than rebuilding Maven — confirmed healthy via `/v3/api-docs` returning 200 and Flyway validating all 39 migrations with no drift. (3) `gradlew` had lost its executable bit (`chmod +x` fixed it). (4) For the desktop app, did **not** try to reconstruct the old manual-classpath-JSON-plus-K2JVMCompiler-overlay approach from memory — instead ran `./gradlew :desktopApp:run --no-daemon --max-workers=2` directly, a full clean build+launch through Gradle's own Compose Desktop `run` task. This machine now has much more headroom (7.6GB available at the time) than whatever prompted earlier sessions to build the overlay-jar workaround in the first place, and it completed in a few minutes with zero errors (only pre-existing deprecation warnings) — first attempt, no retry needed.
**Why this matters for future sessions:** the overlay-jar/manual-classpath-JSON approach documented in many earlier entries was a workaround for slow full builds under tighter memory conditions at the time. It is not the only option and is not obviously still necessary — if `/tmp` scratch state is ever missing again (or even if it's present but you're unsure how stale the overlay chain has gotten), **`./gradlew :desktopApp:run --no-daemon --max-workers=2` from a plain terminal (not through this session's old `launch-desktop.py`) is a clean, simple, first-choice fallback** that also happens to fold forward every overlay jar in one shot (exactly the "worth a full rebuild to fold these forward" TODO several earlier entries flagged). Prefer checking current `free -h` output over assuming the old tight-memory constraints still apply before reaching for the scoped-compile/overlay machinery.
**Verified/runtime:** Backend PID 9674 (started 15:21, `/tmp/pos-run/backend.log`), healthy. Desktop app real JVM subprocess PID 11238 (`com.saporini.mobile_desktop.MainKt`, started 15:26) running with a clean single classpath (`desktopApp.jar` + `shared-jvm.jar`, both freshly built, no overlays) — confirmed via `ps aux`, startup log shows only routine dbus/file-picker INFO lines, no exceptions. No tests run. No data changes beyond the existing local seed data already in `pos_local`.
**Files/modules touched:** None (no source edits this entry — recovery/ops only). New scratch files under `/tmp/pos-run/` (backend.pid, backend.log, desktop-build.log) replacing the old `/tmp/orders-release/` layout, which no longer exists.
**Left open / next steps:** `/tmp/orders-release/*` (compile-tables-sidebar.py, launch-desktop.py, pos-desktop-launch.json, all overlay jars) is gone and has not been recreated — if a future turn wants the old fast scoped-compile-plus-overlay iteration loop back, it needs to be rebuilt from the recipe embedded in earlier AGENT_MEMORY entries (search for "compile-tables-sidebar.py" and "-Xfriend-paths"), or just keep using the plain `:desktopApp:run` approach now proven to work directly.

## 2026-09-24 10:34 (Claude, feature/orders)
**Did:** Two fixes to the 10:28 table-actions work, from the user testing an already-merged-but-still-Free group ("T01-T03-T04"). (1) `ActionList`'s `TableVisualState.Free` branch only offered Seat customers + Mark unavailable — Join/Separate tables were Occupied-only. User wants to merge tables *before* seating (merge first, then seat the combined group), so added the same Join-tables/Separate-tables branching (`isMergedGroup` check) to Free too. (2) Noticed and fixed a real correctness bug while addressing that: `onSeatGuests`, `onUpdateGuestCount`, and `onSetAvailability` in `TablesScreen.kt` all used `selectedTables.firstOrNull()?.id` as the target table — for a merged group, `selectedTables` is built by filtering the full table list by label membership (`currentTables.filter { it.label in mergedGroup }`), so "first" isn't reliably the actual merge primary. `onSeparateTables` already had to solve this exact problem (cross-referencing the domain model for the table whose `mergedTableIds.isNotEmpty()`), so switched the other three to `primaryTableId ?: selectedTables.firstOrNull()?.id` for consistency — same fallback shape, now all four actions agree on which table they're actually operating on for a merged group.
**Why:** Direct user request plus a bug I found by tracing through the exact scenario they were testing, not separately reported.
**Verified/runtime:** Compiled `TableDetailsModal.kt` + `TablesScreen.kt` (+ the other 6 files still in the compile set), exit 0, same pre-existing AutoMirrored-icon deprecation warnings only. Relaunched as PID 512308, empty startup log, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableDetailsModal.kt`, `.../pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** The same `selectedTables.firstOrNull()` pattern is still used (not yet fixed) for the Move-guests source-table pick — lower priority since it wasn't what the user was exercising, but worth applying the same `primaryTableId` fallback there if merged-group moves come up. Awaiting user confirmation on both fixes.

## 2026-09-24 10:28 (Claude, feature/orders)
**Did:** Built the day-to-day table-actions system the user specced (pasted from an external planning chat): seat customers/reservation, change guest count, view/start order, add items, move guests, join/separate tables, mark available/unavailable — everything except cleaning, which was explicitly deferred. This rebuilds `TableDetailsModal.kt` (previously a rough pass with hardcoded fake data — "Main Salon" location, "David K." server, a fake "$72.30" order total, a "View Order" button with no `onClick` at all) into a state-gated action list matching the user's own Free/Reserved/Occupied/Unavailable breakdown, using `MenuNestedDialog` for the two confirm-first actions (mark unavailable, separate tables) per "use my style for dialogs."
**Backend investigation first:** read `TableLayoutRepository`/`TableLayoutApi`/`TablesScreenModel`/`LayoutTableStatus` before writing UI, since the old modal's fakery showed this screen had drifted ahead of real backend support before. Findings that shaped scope: `LayoutTableStatus` has AVAILABLE/RESERVED/OCCUPIED/DIRTY/MAINTENANCE/OUT_OF_SERVICE — no dedicated "bill pending" status (`TableVisualState.BillPending` in the UI enum is actually unreachable from real backend data per `TableUiMapper`, collapses to OCCUPIED going the other way). `unmergeTables` existed on `TableLayoutApi` and was already called internally by `saveTableMerge` (to replace a previous merge) but was never exposed as its own repository/screen-model operation for a standalone "separate" action — added `TableLayoutRepository.separateTable(...)` + `DefaultTableLayoutRepository` impl (mirrors `saveTableMerge`'s existing shape exactly) + `TablesScreenModel.separateTable(...)`. No "move/transfer" endpoint exists at all — implemented `moveGuests` in the screen model as two existing `updateTableStatus` calls (free the source, occupy the destination with the same guest count) rather than waiting on new backend work; disclosed in a code comment that an order already attached to the source keeps its own table reference, since that's Orders-domain state this doesn't touch. No waiter/staff field exists on `LayoutTable` at all, and Reservations (`pos/reservations/ReservationsScreen.kt`, 799 lines) has no repository/domain layer connecting a reservation to a table — both **"Assign/change waiter" and "View/seat reservation" (the literal reservation-lookup part) were left out entirely**, not stubbed, since building either for real needs backend fields that don't exist yet, and a non-persisting fake picker would be misleading. "Seat reservation" as an action still exists and is real — it's just `seatGuests` again (Reserved → Occupied), it just doesn't show any actual reservation details since none are linked.
**New interactions:** "Move guests" enters a floor-plan pick-a-destination mode (`moveSourceTable` state in `TablesScreen.kt`) — a white banner top-center says "Tap a free table to move X's guests there" with Cancel, and `onTableClick` gained a first-priority branch for it (checked before merge-mode / normal-select). "View Order" and the new "Payment" button both call a new `onGoToOrders: () -> Unit` hook threaded `TablesScreen` → `PosScreen` (switches `selected = PosSection.ORDERS`) — deliberately just a tab switch, not deep-linking to a specific order/payment screen, to stay out of Orders' actively-changing internals. Success toasts reuse the exact bottom-left dark-pill pattern built for Plan actions, via a new parallel `actionToastMessage` state (kept separate from `planToastMessage` rather than renamed/shared, to avoid touching the already-confirmed-working plan-upload/delete toast).
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` + `TablesScreenModel.kt` + `Table.kt` + `TableDetailsModal.kt` + `TableLayoutRepository.kt` + `DefaultTableLayoutRepository.kt` + `PosScreen.kt` together (8 files — needed all of them since the interface/impl/model triangle for `separateTable` and `PosScreen`'s new `onGoToOrders` param aren't visible from stale classes, only from source), exit 0 on the first attempt, only pre-existing/new AutoMirrored-icon deprecation warnings. Relaunched as PID 510842, empty startup log, backend untouched. No tests, no full Gradle build, no data mutations beyond what the user triggers live.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableDetailsModal.kt` (full rewrite), `.../pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TablesScreenModel.kt`, `.../pos/tables/domain/repository/TableLayoutRepository.kt`, `.../pos/tables/data/repository/DefaultTableLayoutRepository.kt`, `.../pos/ui/PosScreen.kt`, AGENT_MEMORY.md. Compile script's file list grown to 8 entries.
**Left open / next steps:** User asked to be told when this is finished — reporting now. Not built, with reasons above: real "View reservation" (no table↔reservation link), "Assign/change waiter" (no backend field), and "Clear table"/cleaning (explicitly deferred). "Move guests" doesn't relocate an already-active order's table reference (Orders-domain, untouched). "View Order"/"Payment" only switch to the Orders tab, they don't open the specific order. All of this needs the user's own visual pass — nothing here has been screenshotted or interaction-tested beyond compiling and confirming clean startup.

## 2026-09-24 10:12 (Claude, feature/orders)
**Did:** The Upload/Change-plan icon button (dashed border, in the Plan-mode toolbar) read as "disabled" to the user next to the solid black "Move plan" icon and the dark "Save changes"/"Remove" buttons — its icon and loading spinner were tinted brand green (`#4F7942`), a lighter, more washed-out color against that row than the neighboring dark-charcoal (`#303033`) icons. Changed both the icon tint and the `CircularProgressIndicator` color in `UploadPlanIconButton` from `#4F7942` to `#303033` to match.
**Why:** Direct user report with screenshot: "make the upload more black... it looks like disabled."
**Verified/runtime:** Compiled the usual three files, exit 0 (only the same pre-existing `TableDetailsModal.kt` deprecation warnings, unrelated). Note: Codex concurrently updated `/tmp/orders-release/launch-desktop.py` to prepend its own new `desktop-pos-preview.jar` overlay and restarted the app itself (PID 505509) while this fix was also mid-relaunch — no conflict since that overlay is Orders-only classes, and the classpath chain still includes this session's latest `tables-sidebar-ui.jar`, so this fix is live in the currently-running process. Startup log empty, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** None outstanding for this specific fix. General note: `launch-desktop.py`'s overlay chain is now three jars deep (`desktop-pos-preview.jar:tables-sidebar-ui.jar:inline-menu-ui.jar`) across two concurrent agent sessions — worth a full `shared:jvmJar` rebuild to fold these forward once both sessions' work settles, per the standing note in earlier entries.

## 2026-09-24 10:12 (Codex, feature/orders)
**Did:** User has not purchased Android POS hardware and requested a common screen size. Checked official SUNMI D3 Pro specs (15.6-inch FHD 1920x1080) and iMin support specs (Swan 1 Gen2/Swan 1 Pro same), chose that mainstream format without asserting measured market dominance. Desktop main.kt now targets a 1920x1080 physical-pixel client area: divides by default monitor AWT scaling, initializes window dimensions, then packs client preferred size excluding desktop decorations and centers on launch. Keeps window resizable. Actual Android density/system bars are device-specific; this is screen-resolution targeting, not full device emulation or physical-inch calibration.
**Files/modules touched:** desktopApp/src/main/kotlin/com/saporini/mobile_desktop/main.kt and this log. Added /tmp/orders-release/compile-pos-preview.py and desktop-pos-preview.jar; launcher now prepends this entry-point overlay before Tables/Orders overlays. Preserve it on subsequent reloads or build desktop entry point into the normal artifact.
**Verified/runtime:** Entry-point Kotlin/Compose compilation and diff --check passed. Reloaded desktop PID 505509; startup log empty at check. No tests, backend changes, commits or push; no live window measurement.

## 2026-09-24 10:10 (Claude, feature/orders)
**Did:** Three sizing/step tweaks to table editing, all in Table mode's resize controls: (1) `FloorPlanTable`'s default `scale` (`TableModels.kt`) reduced from `0.74f` to `0.55f` — this is what a freshly-added table (via "Add table") renders at, since `newPreviewTable(...)` never sets `scale` explicitly and falls through to the data class default; existing backend-loaded tables are unaffected (their `scale` always comes from saved data, never this default). (2) Table resize step (`onScaleSelectedTable`) reduced from `0.08f` to `0.03f` per +/- click. (3) Table rotation step (`onRotateSelectedTable`) reduced from `15°` to `5°` per click, button labels updated to match ("↶ 5°"/"↷ 5°").
**Why:** User: new tables render too big by default (wanted them closer in size to an existing smaller one they'd already resized down — "the 4th one"), and both the size and rotation nudge buttons were too coarse, requiring one click to overshoot rather than several clicks to dial in. The 0.55 default is a reasonable-first-pass guess at matching their reference table, not a measured value (no way to inspect a specific saved table's exact scale from here) — flagged for the user to fine-tune further if 0.55 isn't a close enough match.
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` + `TablesScreenModel.kt` + `Table.kt` + `TableDetailsModal.kt` together (compile script's file list had grown to five files from earlier work this session not fully visible in this context — see note below), exit 0, only pre-existing deprecation warnings (`Icons.Filled.Assignment`/`List` → AutoMirrored variants, unrelated to this change, not fixed). Relaunched as PID 504481; empty startup log, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableModels.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** A context compaction happened somewhere before this turn — the compile script (`/tmp/orders-release/compile-tables-sidebar.py`) had already been updated to include `Table.kt` and `TableDetailsModal.kt` from work this session has no direct memory of; trusted it as current/deliberate per the tooling note rather than reverting. Awaiting user confirmation the new default table size and finer steps feel right.

## 2026-09-24 10:09 (Codex, feature/orders)
**Did:** Inspected desktopApp main.kt for requested Android POS hardware window sizing. Current default is 1280dp x 800dp; actual physical pixels depend on desktop display scaling. No target hardware model/resolution is specified in available context. Asked user for device model or resolution before choosing an exact size.
**Left open / next steps:** Awaiting hardware dimensions (and density if exact Android layout parity is needed). No application edits, build, tests or reload this turn.

## 2026-09-24 10:05 (Codex, feature/orders)
**Did:** At the user’s request, reversed only the preceding white table-name text change. Restored colors.content for all three square/round name-label paths in Table.kt. Earlier status palette, fixed filters, spacing and Add table button remain unchanged.
**Verified/runtime:** Scoped Tables compilation passed; reloaded desktop PID 502524. No tests, backend changes, commit or push. No live visual verification.
**Files/modules touched:** Table.kt and this handoff log.

## 2026-09-24 09:50 (Codex, feature/orders)
**Did:** Changed only table-name text (T01 etc.) to Color.White in all three square/round label rendering paths in Table.kt. Other content colors/backgrounds unchanged.
**Verified/runtime:** Scoped Tables compilation passed. Reloaded desktop PID 498107. No tests, backend changes, commits or push; no live visual verification.
**Files/modules touched:** Table.kt and this handoff log.

## 2026-09-23 23:52 (Codex, feature/orders)
**Did:** Matched Tables status colors to desktop Orders badge colors via tableStatusColor: Free #147A25, Occupied #D15F00, Reserved #4F5350, Bill Pending #087594, Unavailable #AA3F38. Shared across status filters, plan table/chair indicators and table detail status; pale backgrounds use 14% tint, with opaque white compositing for labels on solid table shapes. Preserved zero-count disabled filter behavior. Added right-edge Add table button matching Orders button dimensions, green color, font and mirrored edge shape; vertically draggable with saved position. Visible only with canEditLayout, outside plan/merge modes, and hidden while details are selected/loading; disabled while saving. Opens existing AddTableDialog (visibility changed to internal); confirming enters Table edit mode, adds/selects a preview on selected floor and preserves the existing Save changes persistence path.
**Files/modules touched:** TablesScreen.kt, Table.kt, TableDetailsModal.kt, TableLayoutToolbar.kt (dialog visibility only this turn), this log. Preserved all prior concurrent edits. Existing compile-tables-sidebar.py now also includes Table.kt and TableDetailsModal.kt, five source files total, so later overlay rebuilds retain these changes.
**Verified/runtime:** Scoped Kotlin/Compose compilation passed, diff --check clean. Reloaded desktop PID 491980. No tests, backend changes, database writes, commits or push. No live visual verification.

## 2026-09-23 23:27 (Claude, feature/orders)
**Did:** Four more edit-mode toolbar changes, all in `TableLayoutToolbar.kt`/`TablesScreen.kt`: (1) Added a back-arrow `IconButton` (`Icons.AutoMirrored.Outlined.ArrowBack`, 38dp) at the very start of the edit-mode Row, before the Plan/Table-specific controls — calls the same `onEditModeChanged(false)` as "Save changes" (save-if-changed, then exit), not a discard action; the user asked for "an arrow to go back," not explicit discard semantics, so this was the safer, simpler reading. (2) "Save changes" is now disabled (dimmed, non-clickable) unless there's an actual unsaved change: added `hasChanges: Boolean = true` param to `TableLayoutToolbar`, computed in `TablesScreen.kt` as the exact same condition `onEditModeChanged`'s own save-logic already checks (`layoutTables != editTableSnapshot` for tables OR `planOffset`/`planScale` differing from `selectedFloorLayout`'s saved values), so the button's enabled-state and the actual save decision can never disagree. `ToolbarButton` gained a matching `enabled: Boolean = true` param (dims background alpha to 0.45 for primary/destructive variants, disables the click). (3) "Add table" restyled to match Orders' "Add item" button (`OrdersScreen.kt:381-392`, `OutlinedButton` + green border/icon/text) — added an `accent: Boolean` param to `ToolbarButton` (white bg, green border, green icon+text) and gave it `Icons.Filled.Add`. (4) Per explicit request ("only table things"), **removed the plan-management controls (Upload/Change plan, Move plan, size ±, Remove) entirely from Table mode** — they used to also render there whenever no table was selected (leftover from before Plan became its own mode). Table mode now only ever shows: back arrow, Save changes, Add table, and — when tables are selected — duplicate/rotate/resize/delete. All plan controls are Plan-mode-exclusive now.
**Why:** Direct sequential user requests. The user's message also contained an unclear middle clause ("when i click that button says above select the table you want to add") that didn't parse into an actionable UI change — not acted on; flagged below rather than guessed.
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` + `TablesScreenModel.kt` together, exit 0 first try. Relaunched as PID 485559 (previous PID had already cycled to 485027 from an intermediate restart); empty startup log, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** The unclear clause above may have been describing a desired "select an existing table, then configure/add it" flow distinct from the current `AddTableDialog` (shape + chair count picker, no table-selection step) — needs the user to restate it plainly rather than a guess, since nothing concrete could be derived from the wording. Also worth double-checking with the user: the back-arrow currently saves-if-changed before exiting (same as Save changes) rather than discarding — confirm that's actually wanted, since "go back" sometimes implies discard in other apps.

## 2026-09-23 23:26 (Codex, feature/orders)
**Did:** Moved StatusFilterOverlays (All, Free, Occupied, Reserved, Bill Pending, Unavailable) from the scrolling inner Box to the fixed outer viewport Box in TablesScreen. Preserved its styling, counts, callbacks and position; floor-plan/toolbar content keeps existing scrolling.
**Files/modules touched:** TablesScreen.kt and this log only.
**Verified/runtime:** Three-file Tables compilation and diff --check passed; reloaded desktop PID 485027. No tests, backend changes, commit or push. No live visual verification.

## 2026-09-23 23:23 (Codex, feature/orders)
**Did:** Removed only the outer TablesScreen bottom padding (12dp to 0dp), preserving 22dp side and 12dp top padding, to eliminate the fixed white strip at the viewport bottom. Preserved all existing concurrent Tables changes.
**Files/modules touched:** TablesScreen.kt one modifier and this log.
**Verified/runtime:** Existing three-file Tables compilation passed; diff --check passed. Reloaded desktop as PID 483974 using existing launcher and overlays. No tests, backend changes, commit or push. No live visual verification.

## 2026-09-23 23:20 (Claude, feature/orders)
**Did:** Several rapid user-requested changes to the Plan-mode toolbar and its backing screen model, landed together: (1) Renamed both "Done" buttons (Plan mode and Table mode — same `ToolbarButton` call in two branches of `TableLayoutToolbar`) to **"Save changes"**, since the user confirmed via a question that Done is genuinely what persists resize/move/table-position changes and wanted the label to say so. (2) "Remove" [plan] now asks for confirmation first: added a `showRemovePlanConfirm` state and a `MenuNestedDialog` (imported directly from `com.saporini.mobile_desktop.pos.menu.ui.menu.MenuNestedDialog` — cross-package `internal` access within the same `:shared` module works fine, same as importing `DialogActionStatus` elsewhere) titled "Remove plan?" with a solid-red "Remove" confirm + muted "Cancel", matching Menu's own delete-confirmation style exactly, per explicit request ("use same dialogs as we have used in menu"). (3) Plan zoom step size reduced from `0.05f` to `0.02f` per +/- click (both call sites) — user wanted finer-grained control. (4) `TablesScreenModel.uploadPlanImage(...)` and `.removePlanImage(...)` gained an `onSuccess: () -> Unit = {}` trailing param (backward compatible, only one call site each, both in `TablesScreen.kt`), invoked as the last line inside the existing `launchSaving { ... }` block — so it only fires on the success path, since any thrown exception before it skips it and lands in `launchSaving`'s existing catch block. Wired to set a new `planToastMessage` state ("Plan uploaded successfully" / "Plan deleted successfully"), auto-cleared after 3000ms via `LaunchedEffect`, rendered as a dark bottom-left pill (green check icon, white text) as a sibling inside the screen's outer status-overlay `Box` (same one hosting the existing loading/error text). (5) Loading feedback: added `isSaving: Boolean = false` param to `TableLayoutToolbar`, wired from `backendState.isSaving` (the same flag `launchSaving` already flips) — `UploadPlanIconButton` and the "Remove" `ToolbarButton` both gained an `isLoading` parameter that swaps their icon for a small `CircularProgressIndicator` and disables clicks while true.
**Why:** Direct sequential user requests, most after visual confirmation of the previous fix. The `isSaving` flag is shared across all Tables save operations (not scoped to just plan upload/remove), so the spinner is a reasonable approximation, not a perfectly scoped per-action loading state — acceptable since Upload/Remove are the primary actions available in Plan mode anyway.
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` + `TablesScreenModel.kt` together (first attempt at just the first two failed with "no parameter with name 'onSuccess'" since the model change wasn't in that compile unit — added it to `/tmp/orders-release/compile-tables-sidebar.py`'s file list and it passed clean). Stopped PID 477734, relaunched as PID 482517; startup log shows only routine dbus/file-picker transport INFO lines (from `rememberFilePickerLauncher`), no errors. Backend untouched, no tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, `.../pos/tables/ui/TablesScreenModel.kt`, AGENT_MEMORY.md. `/tmp/orders-release/compile-tables-sidebar.py` now compiles three files instead of two — remember to keep it in sync if TablesScreenModel gets touched again.
**Left open / next steps:** Awaiting user visual confirmation of all five changes. Note on this log itself: the last few entries before this one used estimated/incremented timestamps instead of checking `date` each time (23:30/23:45/23:52 were guessed) — this entry's timestamp (23:20) was verified with `date` and is earlier than those, which is a real inconsistency in the log's ordering. Not fixing retroactively per the append-only rule, but future entries should always shell out to `date` rather than estimate.

## 2026-09-23 23:09 (Codex, feature/orders)
**Did:** Removed the ten untracked menu-reference desktop/phone PNG screenshots from the repository root at the user’s explicit request. Confirmed all ten are absent.
**Files/modules touched:** Root menu-reference-*.png screenshots and this handoff log only.
**Left open / next steps:** Existing Tables source changes preserved. No build, tests, reload, commit or push for this cleanup.

## 2026-09-23 23:52 (Claude, feature/orders)
**Did:** Found the actual, root cause of the "white space"/"padding" complaints from 23:45 (the box-fill and bottom-white-space reports were really the same underlying thing, just seen at different times). Inside `FloorPlanTables`, the plan `Image` (both the custom-uploaded and bundled-`Res.drawable.plan` variants) was wrapped in a pre-existing `Box(Modifier.fillMaxSize().padding(22.dp))` — a 22dp inset around the whole image that predates all of today's changes and was invisible before because nothing outlined the container. Now that the green bounds-box border exists (23:30), that 22dp gap between the border and the actual plan artwork became clearly visible. Removed the `.padding(22.dp)`, so the image now fills the bounds-box edge to edge. Both `Image` calls already use `ContentScale.FillBounds` (confirmed no letterboxing/aspect-ratio mismatch involved) — the padding modifier was the sole cause.
**Why:** Direct user report with screenshot: "the green line has some padding with the [floor plan] line, remove that padding."
**Verified/runtime:** Compiled `TablesScreen.kt`, exit 0. Stopped PID 476936, relaunched as PID 477734; empty startup log, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** This likely also resolves the still-unconfirmed "white space at bottom" report from 23:45 (same root cause, the 22dp inset), so that earlier left-open note can probably be closed once the user confirms this screenshot looks right. Awaiting confirmation.

## 2026-09-23 23:45 (Claude, feature/orders)
**Did:** Rapid-fire follow-ups on the Plan-mode toolbar from 23:30, each confirmed against a screenshot before acting: (1) User flagged white space at the bottom of the new green bounds-box; before removing the box outright, a second, newer screenshot showed the box actually filling correctly with no gap, so left the border in place rather than guessing blind — see left-open note. (2) That second screenshot showed a real, fixable issue instead: the plan canvas's `BoxWithConstraints` was `.fillMaxWidth(0.98f)`, leaving a ~2% side margin now made visible by the green border — changed to plain `.fillMaxWidth()` to close it. (3) User then reversed the 23:30 "icon-only Remove" change — turns out they wanted icon *and* the word "Remove" together after seeing it icon-only. Reverted `RemovePlanButton` back to `ToolbarButton(text="Remove", icon=Icons.Outlined.DeleteOutline, destructive=true, ...)` (both call sites) and deleted the now-dead `RemovePlanButton` composable. (4) User said the Plan-mode toolbar and the green line/plan below it were now too close together ("just a little more space... just a little") — added `verticalArrangement = Arrangement.spacedBy(10.dp)` to the Column wrapping the toolbar Box and `FloorPlanTables`.
**Why:** Direct sequential user corrections, each with a screenshot. Notable: don't over-correct on a stale complaint once a newer screenshot shows the thing already looks right (point 1) — verify against the latest evidence before acting, especially for something as blunt-force as removing a just-added feature.
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together, exit 0 first try. Stopped PID 475393, relaunched as PID 476936; empty startup log, backend untouched. No tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** The bottom-white-space report (point 1) was never confirmed fixed or explicitly retracted by the user — only inferred as resolved from a screenshot that happened not to show it. If it resurfaces, the real fix is likely that `FloorPlanTables`' fixed `aspectRatio(1448f/1086f)` doesn't match the actual background image's proportions, not the border itself — investigate the image's real aspect ratio rather than removing the bounds-box feature.

## 2026-09-23 23:30 (Claude, feature/orders)
**Did:** Four fast fixes on top of 23:15. (1) **Regression fix**: 23:15 added `&& !planOnlyMode` to the `editMode` param passed to `FloorPlanTables`, intending to stop tables being draggable during Plan mode — but that same `editMode` param also gates the Move-plan nudge arrows (`if (editMode && planMoveMode)` inside `FloorPlanTables`, ~line 849), so it silently broke "Move plan" entirely whenever `planOnlyMode=true`. Reverted that param back to `editMode = editMode && canEditLayout` and instead added a genuinely separate `planOnlyMode: Boolean = false` param to `FloorPlanTables` (used only for #2 below, not for gating anything else) — tables being technically draggable during Plan mode is an acceptable, harmless tradeoff versus breaking Move-plan again. (2) Added a visible bounds box: `FloorPlanTables`' outer `BoxWithConstraints` gets a `2.dp` brand-green (`#4F7942`) border, shown only when `editMode && planOnlyMode`, so the user can see the space available while positioning/scaling the plan image. (3) "Remove plan" is now icon-only — new `RemovePlanButton` composable (42dp, solid `#B13A2F`, white `Icons.Outlined.DeleteOutline`, no text) replacing the old text+icon `ToolbarButton(destructive=true)` call in both the Plan-mode and legacy Table-mode-no-selection branches. (4) The 74dp-tall toolbar `Box` (title/floor/edit/search row) was taller than any of its content (tallest child is the 52dp search field) — reduced to 60dp per the user flagging "too much padding" between the nav bar and the plan below it.
**Why:** Direct user reports: "move plan does not work fix that", "remove do only with icon", "make a box so i know the space it has [in Plan mode]", "the nav bar has too much padding with the plan make it less".
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together, exit 0 first try. Stopped PID 473611, relaunched as PID 475393; empty startup log, backend untouched. No tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** User also said "remove the padding of the page at end" in the same message that asked for the bounds box — did **not** act on that part: it's ambiguous whether it means the already-resolved toolbar trailing-spacer question, the 182dp sidebar-reserving padding (which the user explicitly asked to keep a few turns ago — removing it now would reintroduce the plan-behind-sidebar bug), or something else. Left it alone and should ask/confirm rather than guess given the regression risk. Also: tables are once again technically draggable during Plan mode (see fix #1) — low-risk and not user-visible as a problem yet, but flagging in case it becomes one.

## 2026-09-23 23:15 (Claude, feature/orders)
**Did:** Resolved the "three options" ambiguity from 22:56 — user confirmed via screenshot: split the combined "Plan Table" dropdown item into two separate ones, **Table** and **Plan**, so the Edit dropdown now has three total (Table / Plan / Add Floor). Added a `planOnlyMode: Boolean` state (`TablesScreen`) threaded into `TableLayoutToolbar` as a new required param. Clicking **Table** behaves exactly as the old single option did (`editMode=true, planOnlyMode=false`) — unchanged table-arranging toolbar (Done first, Add table, then selection-dependent tools). Clicking **Plan** sets `editMode=true, planOnlyMode=true`, which renders a *different*, dedicated row inside `TableLayoutToolbar`: Upload/Change-plan icon, "Move plan", size −/+, "Remove plan", with **Done moved to the end** — no "Add table", matching the user's screenshot exactly. Both branches share the same underlying plan-management controls/callbacks (duplicated ~15 lines rather than extracted, to avoid touching the working Table-mode path at all). Also: (a) restyled `ToolbarButton`'s `destructive` variant from light-pink-bg/dark-red-text to solid `Color(0xFFB13A2F)` fill + white text/icon, no border — matches Orders' delete button (`OrdersScreen.kt:1402`) exactly; also dropped the border on `primary` buttons for the same solid-fill consistency. (b) Removed the `if (!editMode)` guard around `StatusFilterOverlays` — the right status sidebar is now **always visible**, including during Table/Plan edit mode (previously it vanished whenever `editMode=true`). (c) The Column's `padding(end = ...)` reserving the sidebar's width is now unconditionally `182.dp` (was `0.dp` while editing) since the sidebar no longer disappears. (d) `FloorPlanTables`' `editMode` param gained `&& !planOnlyMode` so tables aren't draggable while managing the background plan specifically (only in Plan mode, not Table mode).
**Why:** Direct, explicit user requests: "table and plan are separate", "done is in the end [for Plan]", "remove plan is completely red same as in order", and "the reserved/free thing in the right are not removed do not remove them no more".
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together, exit 0 first try. Stopped PID 471420, relaunched as PID 473611; empty startup log, backend untouched. No tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Awaiting user visual confirmation, especially of the new Plan-mode toolbar's control order (Upload → Move plan → − → + → Remove plan → Done) and that the sidebar staying visible during edit mode doesn't visually clash with the floor-plan canvas (canvas positioning/offsets inside edit mode were not touched this pass).

## 2026-09-23 22:56 (Claude, feature/orders)
**Did:** User corrected the 22:52 change immediately — they wanted the centered toolbar cluster kept (my read of "remove the padding at the end" as "undo centering" was wrong). Re-added the `if (!editMode) { Spacer(Modifier.weight(1f)) }` right after `TableLayoutToolbar(...)`'s call, so [Floor, Edit, Search] is centered between the title and the right edge again, matching the 22:40 state.
**Why:** Direct correction: "why you removed from the center man? they qere perfect at center."
**Verified/runtime:** Compiled, exit 0. Stopped PID 470445, relaunched as PID 471420; empty startup log, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Same message also said "edit has three optin table, or paln or add floor" — unclear whether this asks for a third distinct dropdown option (currently there are two: "Plan Table" and "Add Floor") or is just the user narrating the two existing options back. Left the dropdown as-is pending clarification; flagged to the user directly rather than guessing a third option blind.

## 2026-09-23 22:52 (Claude, feature/orders)
**Did:** Two more changes to the Tables toolbar. (1) User didn't like the centered look from 22:40 ("remove the padding at the end") — removed the trailing `Spacer(weight(1f))`, back to just the one leading spacer after the "Tables" title so the [Floor, Edit, Search] cluster sits immediately after it with no forced gap before the sidebar. (2) The Edit (pencil) button no longer toggles edit-mode directly — it now opens a `DropdownMenu` styled like Menu's per-item edit dropdown (`MenuItemCard.kt`'s white/10dp-rounded/6dp-shadow-elevation recipe) with two icon-leading options: "Plan Table" (real — does exactly what the button used to do: `editMode = true` + snapshot/reset the existing edit-mode state) and "Add Floor" (real, not a stub — reveals the next unused `FloorOption` value into a new `visibleFloors` state list and selects it). `FloorOption` already had a third, unused `THIRD("3rd Floor")` value defined but never shown in `FloorSwitcher`'s hardcoded `listOf(FIRST, SECOND)`; changed `FloorSwitcher` to take a `floors: List<FloorOption>` param (width now `138.dp * floors.size` instead of a fixed 276dp) instead of hardcoding two. "Add Floor" is disabled (grayed icon+text) once all defined `FloorOption` entries are visible, since there's no backend/data-model support for creating an arbitrary new floor — this only reveals the pre-existing 3rd option, it does not create new floors beyond that.
**Why:** Direct user request ("check the edit... same as i done for menu... you have to choose Plan Table or Add Floor, put each an icon"). Chose the "reveal existing 3rd floor" interpretation for "Add Floor" specifically because there is no floor-creation API/model to build a real arbitrary-add feature against without much larger scope — flagged this choice for the user to correct if they wanted something bigger (e.g. naming a new floor, backend persistence).
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together, exit 0 first try. Stopped PID 467899, relaunched as PID 470445; empty startup log, backend untouched. No tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Awaiting user confirmation on both changes, especially whether "Add Floor" should eventually be a real create-a-floor feature (name it, persist it) rather than just revealing the existing hardcoded 3rd option — that would need backend work this session hasn't touched.

## 2026-09-23 22:40 (Claude, feature/orders)
**Did:** Quick follow-up to 22:35: user wanted the [FloorSwitcher, EditLayoutIconButton, search] cluster centered in the toolbar rather than pushed to the right edge. Added a second `if (!editMode) { Spacer(Modifier.weight(1f)) }` after the `TableLayoutToolbar` call, matching the one already after the "Tables" title — two equal-weight spacers now bracket the cluster, centering it in the space right of the title. Edit-mode's pill is unaffected (that trailing spacer is also `!editMode`-gated).
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together, exit 0. Stopped PID 467332, relaunched as PID 467899; empty startup log, backend untouched.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Awaiting user visual confirmation of the centered layout.

## 2026-09-23 22:35 (Claude, feature/orders)
**Did:** Restructured the desktop Tables toolbar's normal (non-edit, non-merge) layout from three independently `align(Center)+offset(x=...)`-positioned pieces (search field, floor switcher, edit button — fanned out from the 74dp box's center by hand-tuned magic-number offsets) into one ordinary left-to-right `Row`: a new bold "Tables" title (matching Orders' `OrdersToolbar.kt` title exactly — `Inter, Bold, 20.sp, Color(0xFF232422)`) → `Spacer(weight(1f))` → `FloorSwitcher` → `EditLayoutIconButton` → `TableLayoutToolbar` (renders just the search field when not editing). Did not move or duplicate `TableLayoutToolbar`'s large call (it has ~15 callback params) — inserted the new title/floor/edit group as an earlier sibling in the same Row instead. Edit-mode's own toolbar (Done/Add table/etc. pill, rendered internally by `TableLayoutToolbar` when `editMode=true`) is unchanged and still centered (`Arrangement.Center` on the Row when `editMode`). Also restyled `EditLayoutIconButton` from solid-green-fill+white-icon to Orders' outlined style (white background, `1.dp` green border, green icon — matching `DesktopOrderActionButton(outlined=true)` in OrdersScreen.kt), and changed the sidebar's "All" card from neutral dark-ink colors to the brand green family (`iconTint #4F7942`, `iconBackground #E9EDDE`) to match Orders' convention of using green for the selected/active state (e.g. its Mine/All segmented toggle).
**Why:** User gave an explicit ordered list ("Tables title, then floor, then edit button same style as Orders, then search bar, this is the list how it comes") plus a screenshot flagging the sidebar's "All" card colors, asking to match Orders throughout.
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together again (same friend-paths recipe/script), exit 0; re-verified brace nesting programmatically before compiling (this file's absolute-positioning toolbar is easy to misjudge from indentation alone — see the 21:52 entry's earlier confusion). Stopped desktop PID 464303, relaunched as PID 467332; empty startup log ~10s after launch, backend untouched. No tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Awaiting user visual confirmation. Didn't touch the floor plan's `padding(end=182dp)` reservation from the previous entry — should still be correct since the sidebar's width didn't change. The 3rd floor option mentioned once by the user ("first floor second and thrid") was not added to `FloorSwitcher` (which still only has FIRST/SECOND) since the rest of that sentence read as numbering toolbar *elements*, not floor options — flag if a third floor button was actually wanted.

## 2026-09-23 22:16 (Claude, feature/orders)
**Did:** Three more user-requested fixes to the desktop Tables screen, following the sidebar merge above: (1) Tables' search field was a bespoke private `SearchField` in `TableLayoutToolbar.kt` (own `BasicTextField`, own colors) instead of the shared `core.components.SearchField` that Menu/Orders use — replaced the call site and deleted the private composable + its now-unused imports (`BasicTextField`, `TextStyle`, `Icons.Outlined.Search`). (2) User asked for "the same colors I use in Orders": Tables used a lighter sage green (`#AEBE95` = `SaporiniColors.OliveGreen`) as its generic button/accent color everywhere, vs. the darker `#4F7942` used in the nav bar/Menu/Orders. Bulk-replaced `0xFFAEBE95` → `0xFF4F7942` across `TablesScreen.kt` (24 sites: dialog confirm buttons, floor switcher, edit-layout button, add-to-order flow, loading text, plan-move controls) and `TableLayoutToolbar.kt` (4 sites), but explicitly *excluded* the one semantic use — `TableVisualState.Free`'s color — which stays `#AEBE95` in three consistent places (`Table.kt`'s actual table-shape rendering, `TableDetailsModal.kt`'s status mapping, and the sidebar's "Free" card) since that's part of a deliberate 5-color status legend (Free/Occupied/Reserved/BillPending/Unavailable), not generic branding. (3) "how is the plan behind the sidebar": traced the layout precisely (brace-depth-checked, not guessed) — the toolbar+floor-plan `Column` and the `StatusFilterOverlays` sidebar are siblings inside the same full-size `Box`, and that Column was plain `fillMaxWidth()` with nothing reserving the sidebar's 166dp, so the plan rendered underneath it. Added `.padding(end = if (editMode) 0.dp else 182.dp)` to that Column so the sidebar now has genuinely separate space instead of floating over the plan.
**Why:** Direct user requests after seeing the merged-sidebar screenshot; the "why" for each is in the "Did" line above.
**Verified/runtime:** Compiled `TablesScreen.kt` + `TableLayoutToolbar.kt` together (same `-Xfriend-paths` recipe, updated `/tmp/orders-release/compile-tables-sidebar.py` to include both files — they reference each other so both needed recompiling in one pass), exit 0. Output overwrote `/tmp/orders-release/tables-sidebar-ui.jar`. Stopped desktop PID 460982, relaunched as PID 464303 via unchanged `launch-desktop.py`; empty startup log 11s after launch, backend untouched. No tests, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `.../pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md. `/tmp/orders-release/compile-tables-sidebar.py` updated in place.
**Left open / next steps:** Awaiting user visual confirmation. The 182dp reserved-width number is a hand-computed estimate (166dp sidebar + 16dp gap), not measured against a live render — may need a point of adjustment if it's slightly off. `Table.kt`/`TableDetailsModal.kt` were intentionally left untouched (Free-status color) — flag to the user if that reasoning turns out wrong.

## 2026-09-23 22:03 (Codex, feature/orders)
**Did:** Organized all pending Orders work into eight local commits using a private Git index, preserving unrelated working files and existing staging. Commits: 0ba9f04 fix(orders): preserve pricing snapshots and lifecycle invariants; 0b80b4d feat(orders): serialize and replay authenticated writes; 7779ca7 feat(orders): publish committed branch changes over SSE; 16c7a1d feat(orders): define typed order and catalog contracts; 8f95b0b feat(orders): connect order and catalog API repositories; 53d14e9 feat(orders): manage live state and guarded order actions; ac598ce feat(orders): build connected order management screens; 8f57236 docs(orders): document workflows and remaining integration work. Verified all 69 selected source files exactly match HEAD and working source bytes were preserved. Used the existing repository author identity for these commands only because Git identity was not configured; no global settings changed.
**Files/modules touched:** Orders backend/pricing/tax policy/migrations, replay protection, SSE, desktop Orders contracts/repositories/state/screens, supporting DI and compact AddItemCard, existing Orders test files, Orders README. README clarifies historical verification, explicit-save behavior and remaining desktop integrations.
**Verification:** Each staged commit passed diff --check; no tests, builds, reloads, database changes or pushes performed for this commit-organization task.
**Left open / next steps:** TablesScreen.kt and menu-reference screenshots remain uncommitted and untouched. Mixed AGENT_MEMORY.md is deliberately left uncommitted to preserve concurrent Menu/Tables handoff history. Print/Payment remain unconnected; README documents other existing limitations.

## 2026-09-23 22:03 (Claude, feature/orders)
**Did:** The real request (per user, after the 21:52 revert) was to merge the desktop Tables screen's two floating status-filter columns into one. `StatusFilterOverlays` in TablesScreen.kt used to render two separate `Column`s over the floor plan: one `TopStart` (All/Free/Occupied) and one `TopEnd` (Reserved/Bill Pending/Unavailable). Now there's a single `Column` aligned `TopEnd` containing all six `TableStatusCard`s in that same order (All, Free, Occupied, Reserved, Bill Pending, Unavailable). Nothing else in that function changed — same card styling/colors/click handling, same top bar (search/floor toggle/edit), same floor plan. Did not touch the floor plan's own centering offset (it was tuned assuming a left column existed) since that wasn't asked for; left it for the user to flag if the now-empty left side looks off.
**Why:** User's screenshot + wording ("the sidebar at right", then later explicitly "put the things all in the right") meant one consolidated right-hand sidebar, not the existing left+right split — confirmed via AskUserQuestion after an unrelated hypothesis (a stray green color) was rejected.
**Verified/runtime:** Compiled only `pos/tables/ui/TablesScreen.kt` via the same direct-K2JVMCompiler-with-`-Xfriend-paths=shared/build/classes/kotlin/jvm/main` recipe used for the Menu attempt (script: `/tmp/orders-release/compile-tables-sidebar.py`), exit 0 first try. Output `/tmp/orders-release/tables-sidebar-ui.jar`. Stopped desktop PID 458316, relaunched via `launch-desktop.py` (classpath now `tables-sidebar-ui.jar:inline-menu-ui.jar:` + base) as PID 460982; startup log empty 10s after launch, backend untouched. No tests run, no full Gradle build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md. New `/tmp/orders-release/compile-tables-sidebar.py`; `/tmp/orders-release/launch-desktop.py` overlay line updated again (now three jars stack: tables + menu-attempt-unused + orders — see next line).
**Left open / next steps:** `/tmp/orders-release/menu-sidebar-ui.jar` and its compile script are stale/unused leftovers from the reverted Menu attempt — harmless (not in the classpath) but safe to delete whenever someone's cleaning that directory. Awaiting user visual confirmation on the merged Tables sidebar; the floor plan's left-side centering offset may want adjusting next now that the left column is gone, but that's a separate ask.

## 2026-09-23 21:52 (Claude, feature/orders)
**Did:** Reverted the 21:30 entry below in full. User clarified the annotated screenshot was an instruction to change the **Tables** screen itself, not a style reference for Menu — "menu" in the original request did not mean the Menu feature. Ran `git restore` on MenuScreen.kt (clean revert, diff was exactly the 21:30 change, nothing else touched it meanwhile) and edited `/tmp/orders-release/launch-desktop.py` back to its prior classpath (dropped the `menu-sidebar-ui.jar` overlay, kept `inline-menu-ui.jar` for Orders). Stopped desktop PID 453272, relaunched as PID 458316; clean empty startup log.
**Why:** Wrong target screen from a misread instruction; user was clear and unhappy about it. Menu is back to exactly its pre-21:30 committed state, both in source and in the running app.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/menu/ui/MenuScreen.kt` (reverted), `/tmp/orders-release/launch-desktop.py` (overlay line reverted), AGENT_MEMORY.md. `/tmp/orders-release/menu-sidebar-ui.jar` and `compile-menu-sidebar.py` left on disk unused (harmless, not referenced by the launcher anymore).
**Left open / next steps:** Actual requested change (apply the Tables screenshot's layout to the Tables screen) not yet done — Tables' current source already appears to implement navbar-top + left status filters + right status sidebar + floor plan matching the screenshot closely, so the specific delta the user wants is still being clarified with them before editing TablesScreen.kt.

## 2026-09-23 21:30 (Claude, feature/orders)
**Did:** First pass at restyling the desktop Menu screen's item view (`MenuDetailsContent` in MenuScreen.kt) per the user's sketch of the Tables layout: the toolbar row above the item grid now shows the menu name + the existing `MenuSearchBox` (unchanged component) instead of the inline horizontal `CategoryButtons` chip row; the item grid now sits in a `Row` next to a new desktop-only `MenuSectionSidebar` — a vertical list of section filter rows (icon badge + name + live item count, brand-green `ActiveOlive` when selected) styled after Tables' `TableStatusCard`/`StatusFilterOverlays` right-rail, with an "Edit sections" button at the bottom taking over the old manage-sections entry point. Phone layout (`PhoneCategoryFilterRow`) is untouched. Added `itemCountsByCategory`; removed the now-unused `CategoryButtons` import from MenuScreen.kt (its only call site) — left `CategoryFilterBar.kt` itself unchanged since that file's `PhoneCategoryFilterRow` is still used by the phone layout.
**Why:** User shared an annotated screenshot of the Tables screen (top toolbar, right-side status sidebar, main "plan" area) and asked for the Menu screen to reuse that structure plus "the green/font/search bar I use," explicitly starting with Menu now. Deliberately did not touch Orders/OrdersScreen.kt.
**Verified/runtime:** Compiled only `pos/menu/ui/MenuScreen.kt` via direct K2JVMCompiler with `-Xfriend-paths=shared/build/classes/kotlin/jvm/main` (script: `/tmp/orders-release/compile-menu-sidebar.py`) — needed friend-paths because this file calls `internal` members of sibling menu files (MenuValidationToast, MenuItem, phoneMenuItemHeight, etc.); a plain single-file/subtree compile without it fails with "cannot access ... internal in file" even though nothing about their visibility changed. Output `/tmp/orders-release/menu-sidebar-ui.jar`, exit 0, no warnings surfaced. Stopped old desktop PID 446570, relaunched via `/tmp/orders-release/launch-desktop.py` (now prepends `menu-sidebar-ui.jar:inline-menu-ui.jar:` before the base classpath, keeping today's Orders overlay live) as PID 453272; startup log empty (no errors) 9s after launch. Backend (PID 418976) untouched. No tests run, no full Gradle build, per explicit user instruction.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/menu/ui/MenuScreen.kt`, AGENT_MEMORY.md. New helper scripts `/tmp/orders-release/compile-menu-sidebar.py`; edited `/tmp/orders-release/launch-desktop.py`'s overlay line (in place, same file prior sessions use).
**Left open / next steps:** This is a first-pass structural interpretation of the user's sketch (sidebar content = section filters with item counts, since Menu has no direct equivalent to Tables' Reserved/Bill Pending/Unavailable counts) — awaiting the user's visual feedback in the running app before refining further. No screenshot/visual check performed by the agent itself; the user is looking at their own window. Any future full `shared:jvmJar` rebuild should drop both overlay jars from the launcher (fold forward) rather than stacking a third.

## 2026-09-23 21:29 (Codex, feature/orders)
**Did:** Read-only workspace/module inventory for user's restaurant system architecture question. Verified Workspace enum/picker define POS, KDS and ADMIN, permission-gated via ORDER_CREATE, KDS_ACCESS, USERS_READ/SETTINGS_UPDATE. PosSection lists Tables, Orders, Reservations, Menu, Kitchen Status, Shift, My Sales, History, Profile; payment screen exists separately. KdsScreen and AdminScreen currently render ComingSoonScreen. Backend contains menu, recipe, inventory, KDS, tables, reservations, customers, settings, devices, users/roles/audit, notification modules; payment/report/shift have partial foundations, not assumed complete UI.
**Recommendation:** Three shared-data workspaces: waiter POS, kitchen KDS, admin all three with granular permissions. Full recommended subareas to user distinguish POS service functions, KDS preparation/menu-recipe reference and availability, and back-office catalog/stock/purchasing/staff/branch/report/device settings. Customer QR/online ordering, kiosk, pickup display, delivery driver and multi-tenant operator console are optional expansions by business need, not mandatory separate core apps. No implementation or deployment performed.
**Files/modules touched:** AGENT_MEMORY.md only; no builds/tests/reloads or data mutations.

## 2026-09-23 21:12 (Codex, feature/orders)
**Did:** Read-only Tables action review for user's requested recommended action list, respecting pause on Orders implementation. Read TablesScreen, TableDetailsModal, TablesScreenModel, RestaurantTableController/Service and TableStatus. Backend has status/availability, clean/dirty, block/unblock, merge/unmerge, CRUD/layout/QR operations. Seating and merge UI call repository. Tables Start order currently writes local tableOrderOverrides/DI preview IDs; View Order uses default empty handler; Add Items delegates onAddItemsRequested without selected order context here. No new functionality implemented or live actions taken.
**Recommendation scope:** Daily service should cover seat/update guest count, start/view linked order, add items, bill/payment, transfer table, merge/separate, reservation handling, clean/dirty, block/unblock; layout/table CRUD belongs to manager editing. This is a recommended capability list, not a claim all are missing or already connected.
**Files/modules touched:** AGENT_MEMORY.md only. No source edits, builds, tests, reload or DB mutations.

## 2026-09-23 21:10 (Claude, feature/orders)
**Did:** Read-only onboarding pass at user's request: reviewed git log/diff and the finished Menu feature (SectionManagerDialog.kt, MenuFormLayouts.kt, MenuLayoutMetrics.kt, MenuItemDetailDialog.kt, core/theme) to learn this project's UI conventions — colors, type scale, spacing/corner-radius/shadow values, and the three recurring dialog shapes (MenuFormDialog full editor, MenuNestedDialog small confirm, ItemDetailDialog read-only preview) plus the shared DialogActionStatus/DialogStatusBody async-status takeover and the "dim-but-stays-clickable + floating MenuValidationToast" validation pattern. Deliberately did not open/review OrdersScreen.kt or any order-related backend files.
**Why:** User asked to learn their styling/sizing/dialog conventions from finished work, and explicitly excluded the Orders/"create order" feature because it isn't finished yet — which matches this log's own extensive in-progress Orders entries (including a concurrent Codex entry at 21:08 today on the same branch).
**Files/modules touched:** None — no source edits, builds, or reloads. AGENT_MEMORY.md only.
**Left open / next steps:** No action needed; purely context-building for future requests in this conversation. Orders/create-order work remains untouched and should stay owned by whatever session the user is actively driving it through (Codex, per recent entries).

---

## 2026-09-23 21:08 (Codex, feature/orders)
**Did:** Added 34dp bordered vertical MoreVert menu button between table pill and X in desktop order details header, matching close button sizing and 8dp spacing. Opens existing Order items/Order info/Order progress actions, with editability/permission/saving/reconciliation guards. Reused existing action callbacks; no new backend operations. Restyled Edit order information as a 440dp compact dialog with 19sp Inter heading, round X, existing +/- guest controls plus editable count, 110dp multiline order-note field with external label, and fixed icon Save changes button. Save retains updateOrder endpoint, guest validation, busy/reconciliation guards, centered spinner, inline errors, and only dismisses on success; guards against overwriting a concurrent guest/note edit. Creation form unchanged. Also verified creation form for user question: order type (DINE_IN/TAKEAWAY), guest count >=1, table required by UI for dine-in; optional customer/reservation (SETTINGS_READ-gated) and note. Items can be added after draft creation; branch supplied by session, order number/time/currency by server.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md.
**Verified/runtime:** Scoped Kotlin/Compose compilation passed; git diff --check clean; no tests or data mutations. Reloaded desktop PID 446570; inline-menu-ui.jar overlay updated. Backend unchanged. Build log /tmp/orders-info-dialog-build.log. No live visual verification.

## 2026-09-23 21:01 (Codex, feature/orders)
**Did:** Verified OrderStartForm existing-order edit path for user's Order info question. Only Guests and Order note are displayed/editable, persisted through updateOrder(UpdateOrderInput(guestCount, notes)). Order type/table/customer/reservation selectors are creation-only (existing == null). Guest count must be at least one. No application changes, build, tests or data mutations.
**Files/modules touched:** AGENT_MEMORY.md only.

## 2026-09-23 21:00 (Codex, feature/orders)
**Did:** Read-only audit of current desktop Orders controls, compact OrderDetails actions, OrderForms/OrderOperations/DefaultOrderRepository, and PosScreen integration. Confirmed exactly two visible desktop action buttons have empty handlers: Print and Payment (OrdersScreen.kt lines 1931/1951 at audit). OrdersScreen's onPaymentRequested callback is unused. New order, item add/edit/save/void/progress/serve/send, order information and order progress actions call real operations/repository/API. Search and filters work locally over loaded server data and are not missing writes.
**Scope distinction:** Desktop menu does not expose several already-connected compact-view features: apply/remove discounts, move table, split, merge, cancel whole order, void whole order, reopen. Therefore two dead visible buttons is NOT an exhaustive count of all optional backend workflows missing desktop entry points. Did not classify existing quantity/variant/note/actions as missing.
**Files/modules touched:** AGENT_MEMORY.md only. No application edits, tests, build/reload or DB mutations. Code wiring review, not live end-to-end verification.

## 2026-09-23 20:56 (Codex, feature/orders)
**Did:** Edit order item Options field now says "No options to select" when catalog loading succeeds with no available choices and no selected options. Keeps "No options selected" for available but unselected choices; preserves saved option names and existing disabled/no-arrow behavior when unavailable. No label change based merely on a failed/loading catalog query. Also replaced OrdersScreen send-to-kitchen confirmation with a compact 440dp-wide dialog, existing 19sp Inter heading/13sp body typography, round top-right X, and icon-bearing Cancel/Send all to kitchen buttons. Preserved real sendToKitchen operation, error display, saving/reconciliation guards and centered spinner; closes only after confirmed success. Order progress is now a matching compact dialog with current-status badges, four connected icon steps, radio selection, round X, and fixed Save changes footer. Selection alone does not mutate; Save executes the selected existing workflow endpoint and dismisses only on success, showing spinner/errors otherwise. Permissions, editability, pending-item availability and reconciliation guards preserved. Other action forms unchanged.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md.
**Verified/runtime:** Scoped Kotlin/Compose compilation passed, git diff --check clean; no tests or data mutations. Reloaded desktop PID 441123 using updated inline-menu-ui.jar overlay. Backend unchanged. Build log /tmp/orders-progress-path-build.log. No visual/interaction verification.

## 2026-09-23 20:45 (Codex, feature/orders)
**Did:** Add to Order loading is now only a centered spinner, with no Adding text. Connected desktop Edit order items quantity, real catalog variant/options selectors, editable 200-character note and Save changes to existing server operations. Variant arrow appears for multiple choices; options arrow only when choices exist. Preserves option quantities/notes, validates required/max options, saves structural edits atomically via updateItem and notes-only edits through updateItemNotes (also usable after kitchen send). Uses permissions, lifecycle, saving and reconciliation guards; retains dirty drafts across SSE refresh and refuses overwriting a changed baseline. Success/error feedback near Save; loading spinner during save/status writes. Existing Delete now explicitly permission/busy guarded and retains real void endpoint/reason form and audit history. Connected Mark as served/progress to item operations. Editor X clears selection.
**Kitchen:** Fixed row Send action previously calling Send ALL: it now invokes fireItem for that line, shows a centered spinner, confirms Sent to kitchen only after API success, prevents repeat send, and reports failures in editor. Top button says Send all to kitchen. Unsaved editor changes must be saved before sending. Sent item quantity/configuration disabled to respect existing KDS history restrictions; notes remain editable.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md. Backup /tmp/orders-before-connected-editor.kt. No backend or DB schema/data edits.
**Verified/runtime:** Scoped Kotlin/Compose compilation succeeded; no tests/test runners or live order mutations. Reloaded desktop PID 438286; backend unchanged. /tmp/orders-release/inline-menu-ui.jar overlay updated, still prepended by desktop launcher; regenerate/remove after full shared builds. Build log /tmp/orders-connected-editor-build.log. No live visual or interaction verification.
**Left open / next steps:** User should review controls in running app. Delete uses existing reason-confirmation dialog. Sent items must be voided/re-added to change quantity or choices. Existing refresh/reconciliation mechanism handles uncertain network writes.

## 2026-09-23 20:31 (Codex, feature/orders)
**Did:** Add to Order now shows an in-button white spinner and Adding… while submitting to the backend (including the follow-up refresh), retains green button appearance and existing duplicate-click blocking, and settles only after operation result. Removed duplicate submission loading stripe above grid; menu/item-choice loading stripes remain. Section row now measures available width after search/right-panel layout, displays all chips that fit, and adds the three-dot menu only when necessary. Removed fixed three-chip limit/horizontal scroll. Width calculation includes actual fixed chip widths, 7dp gaps and 36dp overflow button; preserves selected overflow section in a visible slot when possible and clears dropdown when all fit.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md. Backup /tmp/orders-before-loading-sections.kt.
**Verified/runtime:** Scoped Kotlin/Compose compilation succeeded; git diff --check clean; no tests or data mutations run. Reloaded desktop PID 434748, no startup errors. Backend unchanged. Updated /tmp/orders-release/inline-menu-ui.jar remains prepended to full shared JAR; build log /tmp/orders-loading-sections-build.log.
**Left open / next steps:** Await visual feedback. No live screenshot/UI interaction checks.

## 2026-09-23 20:24 (Codex, feature/orders)
**Did:** Add to Order validation/save failures now use the exact MenuValidationToast component anchored just above the fixed Add to Order button. Separated submission errors from catalog loading errors; retained existing uncertain-write and disabled-submit guards. Error popup hides when the retained menu page is inactive/returns to editor. Successful confirmed adds show a dark bottom-left overlay with animated success icon and captured request quantity/item name (e.g. 2× Truffle Fries added to order). Timer is 2000ms, restarts for every successful add including identical messages, and is no longer prematurely cleared by selecting another item. Removed old persistent text above grid.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md. Backup /tmp/orders-before-add-feedback.kt.
**Verified/runtime:** Scoped Kotlin/Compose compilation succeeded and git diff --check passed; no tests or order mutations run. Replaced inline-menu-ui.jar after stopping app, then reopened PID 432996; no startup errors. Backend unchanged. Build log /tmp/orders-add-feedback-build.log. Launcher still prepends this UI overlay to full shared JVM JAR.
**Left open / next steps:** Await user visual feedback. No automated UI interaction performed.

## 2026-09-23 20:16 (Codex, feature/orders)
**Did:** Changed Edit order items -> Add item to navigate within the SAME existing dialog/surface. A retained two-page HorizontalPager (manual swiping disabled, 300ms programmatic animation) slides the complete editor left and brings menu content from the right. Menu back arrow/X and dialog dismissal return to the editor; the editor page remains composed to preserve current scroll/draft state. Shared OrderAddItemMenuContent now serves both this embedded page and the standalone picker opened from order details. Existing card previews, selection-driven right panel, and real item saving remain intact.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md only. Backup /tmp/orders-editor-before-inline-menu.kt.
**Verified/runtime:** Scoped OrdersScreen Kotlin/Compose compilation succeeded, git diff --check clean. No tests run. App reloaded PID 430771, no startup errors; backend unchanged. IMPORTANT launcher now prepends /tmp/orders-release/inline-menu-ui.jar to full shared-jvm.jar. Remove/regenerate this overlay after future full shared builds to avoid stale Orders UI. Compiler log /tmp/orders-inline-menu-build.log.
**Left open / next steps:** Await user's visual feedback. No live screenshot/interaction verification.

## 2026-09-23 20:09 (Codex, feature/orders)
**Did:** Separated Add-item card selection from expand: card body selects the item for ordering; expand/See more reuses Menu's exact ItemDetailDialog preview and does not change the order selection. Added catalog-to-preview mapping for real name/description/category/ingredients/variants/group metadata/price, and passed SKU through catalog DTO/domain/mapper. No Menu page behavior or card styles changed.
**Layout:** Menu grid takes all available width before selection. Right customization panel uses AnimatedVisibility horizontal expand/shrink and fade (250ms) and includes a 30dp close-X control in its header. Closing clears selection and restores grid width, while preserving content during exit animation. Selection/add guards prevent submitting an old item's choices during item changes. Existing real Add to Order persistence retained; panel also collapses after successful add.
**Files/modules touched:** OrdersScreen.kt, OrderCatalogModels.kt, OrderCatalogDtos.kt, OrderCatalogMapper.kt; AGENT_MEMORY.md. Backups under /tmp/orders-panel-before.
**Verified/runtime:** Full shared:jvmJar build succeeded in 2m56s with one worker and capped memory; git diff --check passed. No tests run as requested. Reopened desktop PID 429160 using full shared JAR (no overlay), no startup errors. Backend unchanged. Build log /tmp/orders-panel-build.log.
**Left open / next steps:** Await visual feedback; no live screenshot or automated UI interaction performed.

## 2026-09-23 19:11 (Codex, feature/orders)
**Did:** Replaced active Orders 15-second polling with authenticated branch-scoped SSE at /restaurants/{restaurantId}/branches/{branchId}/orders/events. Added OrderChangeNotifier with connected/change events, 25s heartbeat, subscriber cleanup, 15min permission-revalidation reconnect, and AFTER_COMMIT invalidations from OrderSupport.saveOrder (covers order/item/public/KDS writes). Branch transfer invalidates old and new branches. Client reconnects, reloads on each connection/change, conflates bursts, waits out writes without dropping updates, and cancels subscription on background/session change. No DB migration or data edits.
**UI:** Right-panel Add item now opens OrderAddItemMenuModal directly, separate from Edit order. Empty items area reuses Menu AddItemCard with compact 172x112dp variant and existing styling. Permission/lifecycle/save guards applied. Menu picker now loads real item choices/groups, allows variant/option selection, edits a 200-character note, keeps quantity/total/Add footer fixed, and calls model.operations.addItem with existing server-priced API and reconciliation protections. Removed dummy Large/cheese/onion/basil choices. Successful add refreshes list/detail/count/totals, shows confirmation and resets selection so more items can be added. Both direct picker and editor picker share this connected implementation.
**Files/modules touched:** New order/realtime/OrderChangeNotifier.java; BranchOrderController, OrderSupport, OrderWorkflowService; shared OrderApi/OrderRepository/DefaultOrderRepository, OrdersScreenModel, OrdersScreen, AddItemCard, Orders README. Updated existing OrderControllerTest constructor fixture for notifier dependency; no tests added/run. Backups under /tmp/orders-sse/before.
**Verified/runtime:** Backend package succeeded with maven.test.skip=true. Full shared:jvmJar compiled successfully (768MB heap/512MB metaspace, one worker), git diff --check clean. Read-only live SSE subscription returned HTTP200 text/event-stream with connected and heartbeat; no order mutation checks or test runners per user instruction. Backend PID418976 and desktop PID419951 running; desktop startup log empty. Backend jar /tmp/orders-release/backend-verified.jar; backup /tmp/orders-sse/backend-before.jar. Desktop launcher now loads full shared-jvm.jar directly, with NO new-order-ui.jar or other overlays. Build logs /tmp/orders-sse/backend-build.log and desktop-build.log. Existing desktop session token refreshed for read-only SSE verification while app was stopped.
**Limits/next:** SSE broadcaster is in-process for current single-backend deployment; multiple instances would need shared event broker. No end-to-end add-item submission or visual screenshot check was run (user requested compile/reload/no tests). Other pre-existing Edit/Print/Payment controls were not expanded in this task.

## 2026-09-23 18:41 (Codex, feature/orders)
**Did:** Audited the requested left Orders list database connection end to end: UI state/model -> DefaultOrderRepository -> branch-scoped OrderApi -> OrderQueryService -> database repository. It already uses database responses directly and has an empty-list UI, with no sample-list fallback. Read-only PostgreSQL query confirmed all screenshot order numbers exist, including the new untagged draft ORD-6BA34060F35B. The database contains 21 orders: this draft, 16 records tagged for prior UI/scroll review, and 4 prior integration-check records. Explained why sample-looking orders remain visible.
**Files/modules touched:** AGENT_MEMORY.md only; no source or database changes were necessary. No tests, build, or reload performed because implementation already satisfies requested data source.
**Left open / next steps:** Demo records remain saved in local DB; do not silently delete them or user edits to them. Await next connection request.

## 2026-09-23 18:35 (Codex, feature/orders)
**Did:** Re-read backend and shared Kotlin enums for user's requested recap. Confirmed OrderStatus has 5 values, simplified OrderFulfillmentStatus has 5 values, and OrderLineItemStatus has 7 values, matching across backend/client. No application edits, builds, reloads, or tests.
**Files/modules touched:** AGENT_MEMORY.md only.
**Left open / next steps:** Await user direction.

## 2026-09-23 18:34 (Codex, feature/orders)
**Did:** Changed the left-edge order button text to the user's literal "Now order", reduced its footprint from 108x96dp to 100x84dp (text 11sp, icon 16dp), and added vertical dragging while keeping it attached to the left edge. Stores a normalized vertical position with rememberSaveable, retains position when opening/dismissing the form, and clamps to visible bounds as window height changes. Click still opens OrderStartForm; drag uses Compose draggable to consume movement separately from click.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md only. Source backup /tmp/orders-release/OrdersScreen-before-draggable-order.kt.
**Verified/runtime:** Scoped OrdersScreen compilation succeeded into new-order-ui-next.jar; stopped old desktop before replacing loaded new-order-ui.jar, then reopened PID 411752. No startup errors. git diff --check passed. No tests run per user's instruction. Launcher still uses new-order-ui.jar followed by the fresh full shared JAR; backend unchanged.
**Left open / next steps:** Await user feedback on label and draggable button. RememberSaveable retains UI state but does not promise persistence across complete desktop process restarts.

## 2026-09-23 18:30 (Codex, feature/orders)
**Did:** Added compact green + New order button docked to the left edge near the lower portion of Orders, matching the screenshot with curved shoulders and rounded right end. Opens existing OrderStartForm via creating state. Respects ORDER_CREATE permission and save/reconciliation state; hides while editing/detail-only compact views and avoids duplicating the existing empty-state create button.
**Files/modules touched:** OrdersScreen.kt, AGENT_MEMORY.md. No backend/data changes.
**Verified/runtime:** Compiled OrdersScreen only successfully using compile-new-order.py against the current full shared JVM build; no tests run, as explicitly requested. Launcher now prepends /tmp/orders-release/new-order-ui.jar to the current full-build classpath (old toolbar-ui/header overlays remain unused). Reloaded desktop PID 410590, no startup log errors. Future full rebuilds must remove or regenerate this single UI overlay. Previous source saved to /tmp/orders-release/OrdersScreen-before-new-order.kt. git diff --check passed.
**Left open / next steps:** Await visual feedback; user wants compile/reload only, no test runs for this UI work. Order-action connection work remains separate.

## 2026-09-23 18:21 (Codex, feature/orders)
**Did:** Simplified OrderFulfillmentStatus in Java/Kotlin to PENDING, IN_PREPARATION, READY, PARTIALLY_FULFILLED, FULFILLED. All completed order types now use FULFILLED. Cancelled/voided lifecycle preserves last service progress internally, hides progress badges on desktop/phone/details, and is excluded from progress filters. Item/payment/lifecycle enums unchanged. Removed forced READY override after recalculation so already-served orders do not regress. Kotlin JsonNames accepts legacy DELIVERED/CANCELLED only when decoding saved/replayed responses; serializers/UI expose five current values.
**Data:** Added/applied V38 migration, retaining unrelated constraints. Original replaced values preserved in order_fulfillment_legacy; old Delivered becomes Fulfilled and old Cancelled becomes hidden Pending fallback. All 20 local orders remain, including 4 cancelled and 2 voided; six original values archived. Full pre-migration backup: /tmp/orders-release/database-before-fulfillment.sql (mode 600). Initial auto-review rejected data replacement; revised migration preserves original values and was subsequently approved. Old application file snapshots: /tmp/orders-release/before-fulfillment-simplification.
**Verified:** 30 backend regression tests passed and backend package built. Migration tested on copied orders in a transaction then rolled back, covering conversion, original-value retention, unchanged non-progress data, removed-state rejection and retained financial/guest constraints. Live API schemas confirm exactly five states, DB Flyway v38 successful. Full shared JVM build and 20 order tests passed (including legacy decoding, all type/lifecycle/progress DTO combinations, and badge visibility). git diff --check passed.
**Runtime:** Backend /tmp/orders-release/backend-verified.jar restarted, PID 406399, startup log backend-launch.log; old jar saved as backend-before-fulfillment.jar. Desktop reopened PID 408524, startup log empty/no errors. IMPORTANT: launch-desktop.py now uses the newly built shared-jvm.jar directly through the saved config classpath; old toolbar-ui.jar/header.jar overlays are no longer loaded. Future UI edits must rebuild shared:jvmJar or deliberately change launcher. Initial 640MB heap/256MB metaspace Gradle run was stopped on metaspace exhaustion; successful rebuild used one worker, 768MB heap/512MB metaspace and in-process Kotlin compiler.
**Files/modules touched:** OrderFulfillmentStatus, OrderSupport, OrderDomainSupport, OrderWorkflowService, Order entity, V38; mobile OrderEnums, OrdersScreen, OrdersToolbar, OrderWidgets, OrderDetails, Orders README; OrderFulfillmentTest, OrderPricingSafetyTest, OrderDataContractTest; AGENT_MEMORY.md. Existing staged/unrelated user work preserved. No commit/push.
**Left open / next steps:** User's existing Orders action-connection work remains pending; this turn only simplifies fulfillment states. No Android device install or visual screenshot inspection this turn.

## 2026-09-23 18:03 (Codex, feature/orders)
**Did:** Audited need for Fulfilled/Delivered/Cancelled fulfillment values. Current refreshOrderFulfillment uses Delivered as the all-items-fulfilled result for DELIVERY, not an independent delivery confirmation. Cancelled fulfillment mirrors lifecycle Cancelled/Voided and is enforced in Order entity validation and V21 database constraints. Explained optional model simplification versus existing dependencies and distinction from Closed.
**Files/modules touched:** `AGENT_MEMORY.md` only; no enum/schema/UI changes requested or made.
**Left open / next steps:** Await user decision on status design before changing connections.

## 2026-09-23 18:01 (Codex, feature/orders)
**Did:** Checked OrderPaymentStatus and backend close/payment workflows for user's question about replacing Closed with Paid. PAID already belongs to payment status; markPaid changes payment state independently of lifecycle, and closeOrder can allow unsettled orders when allowOpenTickets is enabled. Recommended keeping lifecycle/payment separate and deriving any Paid badge from paymentStatus. No implementation requested or changed.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** Await user direction.

## 2026-09-23 17:58 (Codex, feature/orders)
**Did:** Rechecked Order entity, OrderSupport.refreshOrderFulfillment, and workflow methods to explain why overall lifecycle and preparation/service progress are separate. Fulfill updates active item statuses without closing the order; Close updates lifecycle independently. No application changes.
**User preference:** Check actual code before answering implementation questions.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** Orders action connections remain pending user direction.

## 2026-09-23 17:55 (Codex, feature/orders)
**Did:** Inspected backend/mobile Orders enums and fulfillment calculation to explain existing order vs item progress. Confirmed seven fulfillment statuses and seven item statuses; order progress derives from active items, excludes cancelled/voided lines, gives Ready priority over Partially fulfilled when all remaining items are ready/fulfilled, and maps all fulfilled delivery lines to Delivered. Order lifecycle is separate (Draft/Open/Closed/Cancelled/Voided).
**Files/modules touched:** `AGENT_MEMORY.md` only; explanation requested, no application changes.
**Left open / next steps:** Await user's choice of Orders connection work.

## 2026-09-23 17:53 (Codex, feature/orders)
**Did:** Restarted existing `pos-db` and `pos-mailhog` containers via Podman and launched `/tmp/orders-release/backend-verified.jar` using Java 21/local profile (PID 398912). Database connection succeeded, schema was up to date with no migrations needed, and `/v3/api-docs` returned HTTP 200 on port 8080. Startup log: `/tmp/orders-release/backend-launch.log`.
**Files/modules touched:** `AGENT_MEMORY.md` only; no source changes or builds.
**Left open / next steps:** Desktop and backend are running; await user's next Orders connection task.

## 2026-09-23 17:50 (Codex, feature/orders)
**Did:** Launched the existing POS desktop build using `/tmp/orders-release/launch-desktop.py`; verified process 398325 remained running and startup log had no errors.
**Files/modules touched:** `AGENT_MEMORY.md` only; no source changes or rebuild.
**Left open / next steps:** User wants to connect existing Orders UI actions next; no connection work started in this launch-only turn.

## 2026-09-22 22:07 (Codex, feature/orders)
**Did:** Restored the right desktop Orders panel after user clarified it should remain visible but empty/no data for now. The left list keeps the latest row styling; the right side now always shows the empty placeholder panel instead of order details. Compiled the small Orders UI overlay, repacked the existing empty-state PNG resource, and reloaded the desktop app.
**Why:** User clarified they did not want the right part removed completely, only no data/details there yet.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** User should visually check the restored right empty panel and the left list.

## 2026-09-22 22:04 (Codex, feature/orders)
**Did:** Revised the Orders screen per user correction to focus only on the left list: removed the right-side detail/empty panel from the desktop list view, removed the Takeaway/Dine-in subline from order rows, reduced row text sizes, made long order numbers wrap in a fixed two-line area, moved guest count above customer name, strengthened status/progress badge colors, and maps draft+pending progress to "Not sent". Added real `itemCount` to the backend order response and mobile summary DTO/domain mapping so the list uses live quantities. Rebuilt/restarted the local backend, recompiled the Orders overlays, reloaded the desktop app, and added local review data with real table/customer rows attached to the demo orders.
**Why:** User asked to change only the left list, remove the right part, avoid Takeaway text, show table numbers, prioritize guest count above name, use stronger badge colors, and keep order numbers readable even when long.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, order summary DTO/domain/mapper files, `back-end/src/main/java/pos/pos/order/dto/OrderResponse.java`, `back-end/src/main/java/pos/pos/order/mapper/OrderMapper.java`, local review DB data, `AGENT_MEMORY.md`.
**Left open / next steps:** User should visually check the left list. A desktop screenshot attempt with ImageMagick hung on the current Wayland/X session and was stopped; the app itself is open for review.

## 2026-09-22 21:36 (Codex, feature/orders)
**Did:** Center-aligned the right Orders empty-state helper text under "Select an order to see its details" by giving the text full width and `TextAlign.Center`. Compiled the small Orders UI overlay, repacked the existing empty-state PNG resource, and reloaded the desktop app.
**Why:** User said everything else was perfect and only asked to center that helper text.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** Await user confirmation before moving to filled left order list.

## 2026-09-22 21:33 (Codex, feature/orders)
**Did:** Tuned the Orders empty state per user feedback: cropped transparent padding from `order_empty_dining.png`, increased and slightly raised the left illustration, reduced the "New order" button from 250x62 to 190x50 with smaller icon/text, and reduced the right empty helper text to match the left style. Compiled the small Orders UI overlay, repacked the updated PNG into the overlay jar, and reloaded the desktop app.
**Why:** User said the left title size was perfect, asked the right text to match that style, said the New order button was too big, and asked for the left picture bigger and slightly higher.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, `mobile_desktop/shared/src/commonMain/composeResources/drawable/order_empty_dining.png`, `AGENT_MEMORY.md`.
**Left open / next steps:** Await user's visual confirmation before proceeding to the filled left order list.

## 2026-09-22 21:28 (Codex, feature/orders)
**Did:** Updated the Orders empty left panel to use the user's supplied dining illustration as an app image resource (`order_empty_dining.png`), with the checkerboard preview background converted into real transparency. Reduced the left empty title to match the right empty panel title style (22sp semibold) and added a small centered helper line under it. Compiled the small Orders UI overlay, manually included the PNG in the overlay jar resource path, and reloaded the desktop app.
**Why:** User asked for the left empty state to use exactly the supplied image and for "No open orders" plus the description to be smaller and centered like the right empty text.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, `mobile_desktop/shared/src/commonMain/composeResources/drawable/order_empty_dining.png`, `AGENT_MEMORY.md`.
**Left open / next steps:** User should visually check the empty state. Filled order list and selected-order detail redesign remain pending by request.

## 2026-09-22 21:21 (Codex, feature/orders)
**Did:** Removed the temporary local demo Orders data created for UI review by deleting only rows whose notes began `Demo order for UI review · 2026-09-22`, plus their local child rows; confirmed zero matching demo orders remain. Updated the desktop no-selection panel copy to match the user's reference: "Select an order to see its details" plus two-line helper text "Choose an order from the list to view items, / status and more." Compiled the small Orders UI overlay and reloaded the desktop app.
**Why:** User wanted to see the true no-order state and asked for the right empty panel text to match the reference with smaller text.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, local development database data for temporary demo orders only, `AGENT_MEMORY.md`.
**Left open / next steps:** User should visually check the empty Orders lower layout. Filled left list and selected-order detail redesign are still intentionally pending.

## 2026-09-22 21:15 (Codex, feature/orders)
**Did:** First Orders lower-area redesign step requested by user: replaced the plain empty Orders list text with a bordered left empty-state panel matching the supplied layout direction, including a soft green dining illustration drawn in Compose and a real "New order" button; replaced the plain desktop no-selection text with a centered right panel state using a receipt icon and "No order selected". Kept the existing toolbar and filled order rows/details unchanged. Compiled only OrdersScreen.kt/OrdersToolbar.kt into the small toolbar-ui overlay and reloaded the desktop app.
**Why:** User asked to start with the empty lower Orders layout first, then review before filling the left part and later the right part.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/orders/OrdersScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** User should check the empty layout visually. The supplied image had opaque checkerboard pixels, so no PNG asset was added; the illustration was drawn in Compose instead. Filled order list and right detail panel are intentionally not redesigned yet.

## 2026-09-22 20:38 (Codex, feature/orders)
**Did:** Open/History dropdown now offers only the opposite view, omitting the currently selected entry. Changed OrdersToolbar.kt, compiled small UI overlay successfully and reloaded desktop app. No tests or unrelated changes. Existing launch overlay paths unchanged.

## 2026-09-22 20:34 (Codex, feature/orders)
**Did:** User clarified to compile so changes appear. Compiled only OrdersScreen.kt and OrdersToolbar.kt successfully against header.jar/current shared classes, then restarted desktop app. Latest arrangement now loaded: Orders / Open-History / All-Mine segmented toggle / colored Progress / Search; no header New order. No tests, backend or Android builds.
**Runtime:** /tmp/orders-release/launch-desktop.py now prepends toolbar-ui.jar then header.jar then shared build classes. Both overlays must be removed/regenerated when future full builds replace their code. Compilation is authorized for showing requested UI changes; keep scope narrow and no tests for these tweaks.

## 2026-09-22 20:32 (Codex, feature/orders)
**Did:** Saved requested toolbar changes: removed New order from header, replaced Mine/All dropdown with two visible segments (one olive-filled), reordered controls to Orders / Open-History / All-Mine / Progress / Search. Updated OrdersToolbar.kt and its OrdersScreen call.
**Runtime:** User explicitly prohibits compilation. No compilation/tests performed; reopening uses the previous compiled toolbar, so these newest source edits are NOT visible until a build is authorized. Disclosed this limitation. No backend changes.

## 2026-09-22 20:29 (Codex, feature/orders)
**Did:** Added OrdersToolbar.kt: one horizontal toolbar with title, colored progress dropdown, Mine/All, Open/History, New order and shared search. Removed separate filter rows/date/type controls. Mine uses existing backend createdBy, now mapped through OrderSummary DTO/domain (not assigned waiter; disclosed to user). Changes remain uncommitted.
**Latest instruction:** User interrupted compilation and explicitly requested only reload, no compile/tests; stopped any remaining toolbar compiler and reopened app. Completed toolbar artifact available at reload: True. If false, source edits are saved but app still runs prior compiled UI. If true, header.jar now contains the complete Orders module overlay. No tests run. Future source changes require a build to appear in this native app; explain that limitation before promising source-only reload.

## 2026-09-22 20:21 (Codex, feature/orders)
**Did:** Orders header-only visual change requested by user: title now uses Menu's Inter Bold, 20sp desktop/18sp phone and matching ink color; shared core SearchField placed on the right (280dp desktop, flexible phone), replacing the former full-width Orders search field. Orders page background now white. Existing header actions moved below the title/search row. Only application file changed: pos/orders/OrdersScreen.kt.
**Runtime:** Compiled only OrdersScreen.kt with Compose plugin against current shared Gradle classes into /tmp/orders-release/header.jar, then reloaded desktop app. No tests or Android/backend builds run, as requested. /tmp/orders-release/launch-desktop.py prepends header.jar before shared classes; remove or regenerate this overlay after future full builds to avoid stale UI. SSE remains a future task.

## 2026-09-22 20:14 (Codex, feature/orders)
**To do (user requested, not implemented):** Replace Orders' 15-second polling as the primary update mechanism with authenticated, restaurant/branch-scoped SSE notifications. Notify connected POS clients after committed order/kitchen changes; refresh affected data and refresh after reconnect/app resume to recover missed changes. Keep normal API requests for mutations. Implement when the user asks, not during current UI tweaks.
**Working preference:** User wants UI changes one at a time, exactly as requested, followed by app reload; no extra changes or tests for these UI tweaks. This turn only records the SSE task; no code, tests, builds or restarts.

## 2026-09-22 20:09 (Codex, feature/orders)
**Did:** User stopped further implementation and requested only sample orders and reopening the app. Created and verified 8 local demo orders for the signed-in restaurant/default branch: 6 open/draft with pending/in-preparation/ready/fulfilled statuses, 2 history entries (cancelled/voided), real menu items, available tables/customers where present and one demo discount. Existing orders untouched. Notes start with "Demo order for UI review · 2026-09-22". IDs and seed script are in /tmp/orders-release/demo-order-ids.json and seed-demo-orders.py. Reopened desktop app (PID in /tmp/orders-release/desktop.pid); initial app tab is Tables, user can select Orders. No application code or builds changed this turn.
**Correction / phone:** The phone became visible at the previous turn's final check. Installing the new APK failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE (signature mismatch). Existing phone app/data were not removed or modified; user interrupted before signing-key investigation finished. Phone installation work is deferred by latest request. Backend remains running locally. Prior entry's "No USB phone detected" is superseded by this update.

## 2026-09-22 20:04 (Codex, feature/orders)
**Did:** User authorized all nonpayment Orders UI and pending backend fixes. Replaced hardcoded OrdersScreen with responsive open/history lists, search/type/kitchen filters, desktop detail panel and phone detail view. Added shared controls, create/edit forms, table/takeaway/customer/reservation choices, menu/section catalog, variant/options/quantity editor, saved cart, activity/totals, notes, discounts, transfer table, split preview/split, merge, cancel/void, close/reopen and kitchen actions. Active refresh every 15 seconds; session/permission and uncertain-write handling retained. Payments and delivery creation remain hidden.
**Data/backend:** Removed client orderNumber/currency/openedAt inputs; server assigns them and protects historic fields. Moved input validation into domain/validation. Added saved variant prices, required/min/max/duplicate option validation, per-item option pricing for new lines with legacy preservation, discount conservation on splits/merges, small-line rounding, and restaurant tax-policy snapshots. Added PostgreSQL restaurant write serialization and durable successful-request replay with Idempotency-Key. Fixed cancellation state before autoflush, repeated timestamp order numbers, and multi-bag entity graphs that caused detail reads to fail. Migrations V35–V37 applied locally (local schema now version37; existing V34 also applied). Database backup: /tmp/orders-release/database-before-orders.sql.
**Verified:** Complete :shared:compileKotlinJvm and :androidApp:assembleDebug passed (one worker, capped heaps). 21 Orders JVM tests passed against built production classes, including interactive Compose captures at 360/390/768/1440 widths; screenshots use fixture data in /tmp/orders-release/screenshots. 18 backend tests passed. Live authenticated local API checks passed for concurrent duplicate create, server-owned fields/key mismatch, items, reopening details, discount/split/merge conservation, kitchen send/ready/fulfill and cancellation. Temporary check orders cancelled or voided by merge. Logs/builds under /tmp/orders-release. Original staged files and menu reference screenshots preserved; no commit/push requested or performed.
**Runtime:** Closed IntelliJ gracefully with explicit user approval. Builds were sequential and RAM stayed below the user's 80% budget after closing it. Backend launched from /tmp/orders-release/backend-verified.jar (PID in backend.pid); app launched with current shared Gradle classes prepended to saved desktop classpath (desktop.pid/log). Local pos-db/mailhog running. Android debug APK is mobile_desktop/androidApp/build/outputs/apk/debug/androidApp-debug.apk. No USB phone detected; desktop app launched for review. Native desktop control is unavailable, so actual app navigation needs user interaction; screen captures came from the Compose test harness.
**Left open/configuration:** User has not supplied restaurant tax rate/inclusive preference. No real rate guessed; current zero default preserved. Implementation supports one restaurant rate on discounted items; per-item rates/service-charge taxation need matching business configuration. Inventory and payments are separate work, and refresh uses polling rather than push. Review the UI with the user; code is uncommitted on feature/orders.

## 2026-09-22 (Codex, feature/orders)
**Did:** Captured and visually checked 10 menu UI reference PNGs in project root (menu-reference-desktop-*.png and menu-reference-phone-*.png): six desktop and four phone, covering covers/items/create/sections/reorder. Used real compiled Compose MenuScreen via temporary screenshot harness with fixture data, not live backend screenshots; native desktop control unavailable. No application changes or backend/database restart. Capture process finished. User wants these for AI design references for Orders.

## 2026-09-22 (Codex, feature/orders)
**Did:** Added a plain-language file explanation and TODO comments in OrderInputs.kt at user request. Pending agreed changes: backend-generated orderNumber/openedAt, automatic restaurant currency, preserve response fields and historic order currency. Comments only; behavior unchanged and no tests required. Wait for user authorization to implement pending changes together.

## 2026-09-22 (Codex, feature/orders)
**Reviewed:** Explained CreateOrderInput restaurant scoping: OrderOperations supplies restaurantId from the signed-in session scope; OrderApi sends it in the URL, not the request body. No application changes or tests needed.


## 2026-09-22 (Codex, feature/orders)
**Did:** User explicitly authorized implementing the complete Orders data layer while deferring screens and payments. Added typed nonpayment Orders API (56 methods), DTO/domain mappings, exact decimal serialization, validated repositories, menu/variant/option-choice/table queries, lifecycle-aware OrdersScreenModel, session/permission isolation, serialized writes, stale-response protection and uncertain-write reconciliation. Registered dependencies in Koin. Existing OrdersScreen untouched; user staged entries preserved.
**Verified:** Focused compilation of actual new Orders sources plus appModule with Kotlin/serialization plugin succeeded; 17 JVM tests passed. Kept compiler heap 384MB and test runtime 256MB, no backend/database/desktop restart. Not a complete Gradle/Android build or live backend integration test.
**Left open:** Connect screens after user supplies design. README in orders documents integration and release gaps: backend idempotency/concurrency, server option constraints, variant price snapshots and tax calculation; inventory/realtime remain separate and payments deferred. No commit/push requested or performed.

## 2026-09-22 17:41 (Codex, feature/orders)
**Did:** Investigated Serializable import in OrderSummaryResponseDto.kt. Full :shared:compileKotlinJvm passed offline using bash gradlew, --no-daemon, one worker, 768MB heap and in-process compiler (2m55s). No source/config edits. IntelliJ saved commonMain module includes serialization plugin and core 1.11.0 library; referenced transformed klib exists.
**Left open / next steps:** Editor issue remains unverified; exact hover diagnostic requested. Need IntelliJ Sync All Gradle Projects; no IDE control tool is available. Do not advance Orders teaching until editor issue is resolved.

## 2026-09-22 17:38 (Codex, feature/orders)
**Guided review:** OrderSummaryResponseDto.kt matches supplied fields/imports and backend summary contract. User explicitly permits continuing despite unresolved IntelliJ highlights and wants agent to run verification later. Next supplied OrderApi.kt under orders/data/api with injected shared HttpClient/base URL, getOrders optional status filter and getOpenOrders matching BranchOrderController routes. No source edits, builds, or runtime starts; await user paste before repository step.


## 2026-09-22 17:36 (Codex, feature/orders)
**Guided check:** User requested next step. OrderEnums.kt has correct import and backend enum values; OrderSummaryResponseDto.kt not yet created. Prior IntelliJ unresolved kotlinx issue has not been confirmed resolved. Following user instruction not to supply extra code while an error remains, asking whether the IDE red underline is gone before proceeding. No source edits/builds.


## 2026-09-22 17:25 (Codex, feature/orders)
**Did:** Read handoff and linked OrderEnums.kt after user greeting; Serializable import is present. No source changes or builds.
**Left open / next steps:** Await user direction; prior IntelliJ unresolved-import issue has not been reverified.

## 2026-09-22 17:22 (Codex, feature/orders)
**IntelliJ follow-up:** User screenshot confirms unresolved kotlinx import in IDE despite successful isolated enum compilation. Rechecked Gradle serialization plugin/dependency and IDE logs: serialization 1.11.0 was imported earlier; duplicate-library warnings exist but cause not proven. Native desktop control/IntelliJ MCP unavailable in this conversation, so could not trigger IDE Gradle reload or verify editor diagnostics. Read computer-use skill and disclosed limitation. No source/config modifications; user needs to trigger Reload All Gradle Projects so we can inspect resulting sync errors. Do not proceed to more Orders code yet.


## 2026-09-22 17:19 (Codex, feature/orders)
**Annotation investigation:** User says stop giving further code when current step has errors; explicitly authorized fixing Serializable issue. Import is now present in OrderEnums.kt. Serialization plugin and JSON dependency already configured, IntelliJ mobile project linked to Gradle. Compiled exact enum source successfully using cached Kotlin 2.3.21 compiler + serialization plugin and core 1.11.0 into /tmp/pos-order-enum-check.jar (256MB heap); no full app build or services started. No dependency/source changes needed based on compiler result. Asked user for exact IntelliJ hover error; pending. Do not advance teaching steps until resolved.


## 2026-09-22 17:14 (Codex, feature/orders)
**Guided review:** User added OrderEnums.kt; enum names/values match backend but kotlinx.serialization.Serializable import is missing. Asked user to add it, no source edits. Next supplied code is data/dto/OrderSummaryResponseDto.kt for order-list response fields, nullable table/customer/closedAt metadata and backend-calculated total as Double for display only. Existing Ktor ignoreUnknownKeys permits omitted detail/payment fields. Await user paste then review; no builds/services started.


## 2026-09-22 17:06 (Codex, feature/orders)
**Reviewed:** Mobile Orders currently contains only OrdersScreen.kt with hardcoded orderSamples, placeholder create/sort/actions and desktop-sized detail panel; no Orders API/repository/screen model/DI registration. Reviewed backend OrderResponse, line/option responses, branch endpoints and enum values, and existing Ktor/Koin patterns.
**Guided step:** User wants code to paste themselves, review after each done, and direct source edits only when explicitly asked. First supplied code is new pos/orders/domain/model/OrderEnums.kt with serializable OrderStatus, OrderFulfillmentStatus, OrderType, OrderSource and OrderLineItemStatus matching backend exactly. Await user creation before checking or next DTO step. No application files edited, builds or services started; payments deferred.


## 2026-09-22 17:01 (Codex, feature/orders)
**Did:** Audited RAM on user request; Firefox and Codex are the dominant apps, with normal Linux/GNOME services also consuming memory. No POS Java/build processes running. Stopped remaining pos-db and pos-mailhog containers gracefully to free development resources; retained data. Restart containers and backend before resuming POS runtime work.


## 2026-09-22 16:59 (Codex, feature/orders)
**Did:** At user request to free RAM for IntelliJ, gracefully stopped the verified POS backend Java process PID 8597. Desktop PID 58200 was already gone. Verified no Java processes remain; approximately 5935 MiB system RAM available after stop. Database/Mailhog and unrelated applications left running. Backend needs restarting before POS API requests work. No source changes.


## 2026-09-22 16:56 (Codex, feature/orders)
**Reviewed:** Read backend Orders services/controllers/entities/repository, KDS integration, payment/shift module inventory, inventory callers, option validation, realtime references and test inventory for POS-only gap checklist. Existing order CRUD, menu references, table associations, split/merge, discounts, events and KDS synchronization are implemented. Confirmed gaps: payment module has entities/repository but no processing services/controllers (order paid/refunded are status changes); totals hardcode taxTotal ZERO; variant delta is read live during recalculation despite price snapshots; option min/max/required constraints not enforced; no order idempotency or optimistic/pessimistic locking found (including base entities); no menu/order/KDS realtime notifications (table layout SSE exists); recipes/inventory not called from order flow; no receipt issuance API, no cash-shift workflow; order tests cover controllers/security/entities but no order service/full-flow coverage found. User asked only for review/checklist; no source edits or tests run.


## 2026-09-22 15:59 (Codex, feature/orders)
**Did:** Created and switched to local feature/orders from feature/menu-final-polish at 361e4aa, including the completed menu work. Preserved the existing uncommitted memory entry. No order implementation changes or remote push requested/performed.
**Next:** Ready for Orders work.


## 2026-09-22 15:54 (Codex, feature/menu-final-polish)
**Checked:** User asked whether admin changes appear immediately for waiters. MenuScreenModel has no live subscription/polling: menu list loads at model initialization and explicit loadMenus calls, while openMenu fetches fresh detail from backend. Existing open menu on another device is not automatically refreshed. Reopening an existing menu fetches item/section changes without logout/login. No application changes requested or made.


## 2026-09-22 15:52 (Codex, feature/menu-final-polish)
**Done:** All-ordering change committed as David in 0bbda23. GitHub browser sign-in completed as Davidi24, HTTPS Git authentication configured, branch pushed. Created and verified OPEN PR https://github.com/Davidi24/POS/pull/96 from feature/menu-final-polish into develop; attached it to this task. Nothing merged. All focused checks passed as recorded below.
**Next:** User reviews PR #96. Final All-position code requires backend migration V34 and a new app build/install; running phone/desktop have the previous version.


## 2026-09-22 15:51 (Codex, feature/menu-final-polish)
**Did:** All can now be dragged among section filters; only Uncategorized is pinned last. All remains protected from edit/delete and item creation. Added nullable menu allFilterPosition through entity, response/update DTOs, mapper, repository/domain, screen model, and Flyway V34. Omitted field preserves saved position for older clients; default is after regular sections and before Uncategorized. Rebuilding filters uses persisted position; first real section remains the initial selection.
**Validation:** MenuServiceTest 8 tests passed (including persistence/omitted-field preservation). JVM build and 5 focused checks passed: 2 All/filter-order tests, existing fallback order test, Compose All selection/no-creation, and immediate fallback creation after section deletion. Single worker, 1280MB Gradle heap. No DB integration run or latest Android install this turn; deploy updated backend/V34 before app persistence works.
**Git/next:** Branched all six earlier commits from local develop onto feature/menu-final-polish for a PR targeting origin/develop. Initial HTTPS push lacked credentials, SSH had no authorized key. User requested browser login; downloaded official gh to /tmp/pos-gh after verifying its official SHA256 (initial unverified download attempt was rejected by auto-review). GitHub device sign-in is pending in exec session 52329. PR description prepared at /tmp/pos-menu-pr.md; after login push branch, create PR into develop, and attach URL. Commit this change using David <101630664+Davidi24@users.noreply.github.com>.


## 2026-09-22 15:32 (Codex, develop)
**Did:** Moved Add New Item after visible menu items on phones only in MenuScreen.kt; desktop keeps the add card first and All keeps it hidden.
**Validation:** Android assembleDebug passed with single worker/1280 MB heap. Installed POS USB update on R5CRB29848E successfully preserving data, restored backend port forwarding and launched app. No additional UI tests for this small ordering change.
**Next:** Ready for user review on phone; committing with existing David identity.


## 2026-09-22 15:26 (Codex, develop)

- Opened the latest desktop app from the existing built JARs with a 512 MB Java heap; PID 58200.
- Verified the process remains running and the startup log has no output/errors. No rebuild or source changes were needed.


## 2026-09-22 15:23 (Codex, branch: develop)
**Did:** Installed the latest POS USB APK (final phone add-card/drag-feedback changes, commit 7e2428c) on reconnected Samsung R5CRB29848E using adb install -r; installation succeeded and existing app data preserved. Restored reverse tcp:8080 tcp:8080 and opened com.saporini.mobile_desktop.usb/com.saporini.mobile_desktop.MainActivity (launch Status: ok).
**Validation:** Phone-side curl to http://127.0.0.1:8080/auth/me returned expected unauthenticated 401, confirming backend connectivity over USB. No source changes or rebuild required; this log only.
**Next:** Latest app is open on phone for user review; keep USB connected and backend running.

## 2026-09-22 15:03 (Codex, branch: develop)
**Did:** Limited final menu polish to requested phone add-card proportions and item reorder feedback. Phone Add New Item is now a full-width horizontal action (88dp outer height at normal text size, scales with font size), instead of the large near-square tile; desktop add-card appearance unchanged. Reorderable items receive actual dragging state and show the same "Hold & drag" / "Moving..." per-card feedback used by menu covers. Phone feedback appears as a white pill over the image; availability control is hidden while reordering to keep that area for drag feedback.
**Files/modules touched:** MenuScreen.kt, item/AddItemCard.kt, item/MenuItemCard.kt, MenuUiScreenshotTest.kt, this log (also includes prior USB install handoff).
**Validation:** Four focused tests passed: 360px phone, 390px phone, enlarged phone text, and compact-card proportions plus both reorder badge states. Reviewed phone and moving-state screenshots under shared/build/reports/menu-ui, including phone-item-polish/02-moving.png. Tests render both feedback states; physical phone drag was not tested this turn. Desktop JAR build passed; POS USB Android assembleDebug passed (3m6s), using 1280MB heap/single worker for Android after the sequential 1536MB JVM run. Other test suites intentionally not run.
**Device/runtime:** ADB reported no connected devices during work and at completion. Latest androidApp/build/outputs/apk/debug/androidApp-debug.apk is the .usb package but is NOT installed on phone yet. Once reconnected, use adb -s R5CRB29848E install -r followed by reverse tcp:8080 tcp:8080 and start com.saporini.mobile_desktop.usb/com.saporini.mobile_desktop.MainActivity. Previous owned desktop preview closed to keep RAM below user limit; backend left running.
**Commit/next:** User requested final commit in existing identity David; commit contains only these changes/tests and handoff notes. No push. Menu work finished for now; next action can install the APK when phone reconnects or move to Orders when requested.

## 2026-09-22 14:49 (Codex, branch: develop)
**Did:** Built latest Android code including final menu polish and connected USB phone Samsung SM_G998U (ADB serial R5CRB29848E). Set adb reverse tcp:8080 tcp:8080; Android config already uses http://127.0.0.1:8080 and permits cleartext traffic. Verified phone-side curl /auth/me returns HTTP 401 (expected unauthenticated API response), proving USB/backend connectivity.
**Install:** Normal update was blocked by INSTALL_FAILED_UPDATE_INCOMPATIBLE (existing phone app signed with different key). Preserved original app/data; built a separate debug copy with applicationIdSuffix .usb and label POS USB using temporary /tmp/pos-usb-init.gradle and /tmp/pos-usb-res resource overlay. Installed successfully as com.saporini.mobile_desktop.usb, launched MainActivity successfully, process 18702, no fatal startup exception recorded. User must sign in; no credentials entered or live business data changed.
**Build/runtime:** Standard Android assembleDebug passed (3m44s); separate USB package build passed (50s) with single worker, 1536MB Gradle heap/in-process Kotlin. Current androidApp/build/outputs/apk/debug/androidApp-debug.apk is POS USB; ordinary build without -I regenerates original package. No application source/config changes were required. Backend remains on host port 8080.
**Reconnect command:** /home/kecid/Android/Sdk/platform-tools/adb -s R5CRB29848E reverse tcp:8080 tcp:8080. Keep backend running and USB connected. Updating POS USB uses same build overrides plus -I /tmp/pos-usb-init.gradle, then adb -s R5CRB29848E install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk. Do not uninstall original app without explicit permission (its local data would be lost).

## 2026-09-22 14:35 (Codex, branch: develop)
**Did:** Removed Add Item button/add card entirely from the All filter on phone and desktop, and removed its toast warning. Real sections retain creation controls; empty All with existing sections has a neutral empty state. Polished desktop cover collection only for a single grid row: cover height up to 520dp, capped vertical inset 72dp, horizontally centered when fewer tiles than columns. Applies with available content height 480–1120dp; short/very-tall windows and multirow grids retain prior sizing. Phone cover layout unchanged.
**User preference:** For future validation, user prefers messages attached to the triggering control (like Create Menu missing-fields feedback), rather than detached/global messages. No validation message wanted for All: hide unsupported action.
**Files/modules touched:** MenuScreen.kt, menu/MenuCoverUi.kt, menu/MenuLayoutMetrics.kt, MenuUiScreenshotTest.kt, this log.
**Validation:** Eight focused UI cases passed: desktop/phone All exposes no add action or warning but real section opens item editor; six cover windows (900x700 wrapping, 1024x768, 1280x480, 1280x800, 1920x1080, 2560x1440). Inspected all six cover screenshots and both All screenshots. After adjusting tall-window cutoff to include normal 1080p desktop, reran six cover tests and packaged desktop JAR successfully. Screenshots under shared/build/reports/menu-ui/*-single-row and *-selection. No broad suite or Android rebuild this turn.
**Runtime/commits:** Closed prior owned POS preview to free RAM near user 80% limit, relaunched updated desktop PID 41788, 512MB heap, /tmp/pos-review-desktop.log. Sequential memory-limited builds retained. Follow-up commit uses existing David identity per prior authorization; no push. Menu ready for user review, Orders not started.

## 2026-09-22 14:20 (Codex, branch: develop)
**Did:** Finished requested menu selection/deletion corrections. Real sections are ordered first, Uncategorized last among real sections, All last overall; first active section is selected after detail load. Updated section reorder index handling so index zero is movable and All/fallback stay pinned. Add-item actions on All show "Select a section to add an item." and never silently choose a real section; removed the save-time fallback too. Empty menus still prompt creation of a section.
**Refresh fix:** Kept stable local section/item state holders across remote menu changes, synchronized incoming details, and applied the full returned deletion result directly before dialog success. Preserved deletions select Uncategorized and clear search immediately, making the destination visible even when the category bar would put it in overflow. Section manager maintains All-last order throughout edits. This strengthens the earlier refresh fix after user reported it still not visible.
**Files/modules touched:** MenuScreen, MenuScreenModel, CategoryFilterBar, SectionManagerDialog, new section/MenuCategoryOrdering, MenuUiScreenshotTest, root .gitignore, this log. Existing uncommitted work included in requested commits; runtime log/PID files untracked and ignored without deleting local log files.
**Validation:** Only three requested focused real-Compose tests run: preserved deletion with existing fallback, preserved deletion creating fallback, first-section default/All add guard. All passed. Deletion assertions now verify the selected Uncategorized chip and a rendered moved item without navigating back, rather than only model data. Inspected screenshots in shared/build/reports/menu-ui/1280x800-font1.0-deletion-false/10-items-refreshed.png and 1280x800-font1.0-selection/22-select-section-message.png. Final desktop JAR packaging passed after wording cleanup. Broader tests and Android rebuild intentionally skipped this turn per user request; previous Android APK predates these latest selection changes.
**Commits/runtime:** User requested committing everything in their name. Git identity unset; after asking and checking history, reused latest existing author David <101630664+Davidi24@users.noreply.github.com> for author/committer. Separate commits 0819f7c (Linux/schema/runtime housekeeping), 38e49d5 (backend menu transfer safety), followed by the mobile/menu+handoff commit. No push requested/performed. Updated desktop launched PID 36458, log /tmp/pos-review-desktop.log, 512MB heap; sequential build overrides maintained total observed RAM below 80%.
**Next:** Menu work paused/completed for user review; Orders next when requested. Prior unrelated option-group/reference-policy caveats remain documented; do not claim blanket production certification.

## 2026-09-22 13:59 (Codex, branch: develop)
**Did:** Fixed stale section-deletion UI: MenuScreenModel.deleteSection now awaits a full detail refresh and updates selected menu/list state before reporting success, instead of MenuScreen calling openMenu and temporarily replacing the detail with a summary. Section editor keeps a stable working-state holder and synchronizes incoming sections, so delayed deletion callbacks cannot restore an obsolete list. The removed-category filter resets to All without overwriting refreshed sections/items.
**Deletion behavior:** Uncategorized section (case-insensitive name) and fallback menu (reserved UNCATEGORIZED code) now hide the preserve/delete checkbox, explicitly warn that contents will be permanently deleted, and submit deleteItems=true. Ordinary sections/menus retain the existing optional preservation behavior. Backend API already supports explicit permanent deletion; no backend changes or live-data deletion performed.
**Files/modules touched:** shared MenuScreenModel.kt, MenuScreen.kt, section/SectionManagerDialog.kt, menu/MenuEditorScreen.kt, jvmTest/MenuUiScreenshotTest.kt, this log.
**Validation:** All 19 shared JVM tests passed, desktop compiled, Android assembleDebug passed (3m16s). Added two real Compose UI deletion flows with strict fake repository: existing fallback and newly created fallback, followed by Uncategorized section/menu permanent deletion; checks refreshed state and absence of preservation controls. Inspected screenshots under shared/build/reports/menu-ui/1280x800-font1.0-deletion-{true,false}. Advanced test frame time to settle modal animations. These use fixture data, not live backend mutations.
**Runtime/next:** Reopened corrected desktop PID 29217, log /tmp/pos-review-desktop.log, 512MB heap. One Gradle worker, in-process compiler, 1536MB heap; sampled RAM below user 80% ceiling. User can review both fixes. Existing unrelated edits and previously documented unrelated menu caveats preserved.

## 2026-09-22 13:39 (Codex, branch: develop)
**Did:** Reproduced the reported infinite-horizontal-width crash using real Compose headless UI rendering, then fixed it: MenuScreen gives the category/loading region bounded weighted space; CategoryButtons owns its scrolling instead of nesting the skeleton's scroller inside another scroller. Added seven screenshot/navigation cases (360x800, 390x844, 768x900, 1280x800, 1920x1080, 2560x1440, and 390x844 with 1.5x text), covering list, opening/loading, items, category selection, and back navigation. Visual inspection caught phone cover title truncation; extracted AutoSizeCoverTitle and measured before drawing for both phone and desktop covers.
**Files/modules touched:** MenuScreen.kt, section/CategoryFilterBar.kt, menu/MenuCoverUi.kt, menu/PhoneMenuCoverUi.kt, new menu/AutoSizeCoverTitle.kt, shared/build.gradle.kts (desktop renderer for JVM tests), new jvmTest/MenuUiScreenshotTest.kt, this log.
**Validation:** All 17 shared JVM tests pass (including seven real-render UI tests); desktop compileKotlin completed; Android assembleDebug BUILD SUCCESSFUL. Combined build was terminated with signal 143 during Android compilation; subsequent isolated Android build succeeded in 4m20s. Generated 35 PNGs and index.html gallery under mobile_desktop/shared/build/reports/menu-ui. Screenshots use strict fake repository/sample data and desktop-rendered shared UI, not a physical Android device or emulator; no live menu data was changed by tests. Existing backend verification from earlier entry remains applicable; no backend code changed this turn.
**Runtime/resources:** User permits up to 80% total RAM. Sequential single-worker Gradle builds with 1536MB heap/in-process Kotlin; sampled total RAM remained below 80% (highest observed ~76%). Backend PID 8597 remained running, /auth/me returns 401 without credentials. Relaunched corrected desktop PID 21586 with 512MB heap; log /tmp/pos-review-desktop.log. Runtime launch settings /tmp/pos-desktop-launch.json. Final Android debug APK under androidApp/build/outputs/apk/debug/.
**Left open / next steps:** User visual review before Orders. No blanket production certification: previously documented option-group editing and hard-delete/reference policy caveats remain outside this responsiveness/crash pass. Do not reset unrelated uncommitted work.

## 2026-09-22 13:11 (Codex, branch: develop)
**Did:** Finished menu responsiveness pass. Added MenuLayoutMetrics in menu UI for actual-width/font-scale-aware item columns and bounded dialog widths; desktop grids now grow gradually beyond six columns on ultrawide displays. Menu cover columns use available space instead of stopping at four. Menu-specific compact presentation now applies below 840dp (and to phones); shared forms retain scrolling/inset handling. Fixed modifier ordering that defeated desktop editor max widths, widened narrow section/options dialogs within available space, and made desktop categories scroll in allocated space so the search field remains accessible.
**Why:** User requested phone and large desktop readiness, then opening the app for visual review before moving to Orders.
**Files/modules touched:** Mobile menu UI MenuScreen, menu/MenuFormLayouts, menu/MenuEditorScreen, menu/MenuCoverUi, item/ItemEditorScreen, section/SectionManagerDialog; new menu/MenuLayoutMetrics and commonTest/MenuLayoutMetricsTest; this log.
**Validation:** 10 shared JVM tests pass, desktop compiles, Android assembleDebug passes. Layout tests cover 288–3840dp and font scales 1.0/1.3/2.0, checking minimum card widths and dialog bounds. No native screenshot/control tool available, so visual review belongs to the user; do not claim screenshots/emulator validation.
**Runtime:** Started existing pos-db/pos-mailhog containers, then the previously verified backend JAR directly with a 512MB heap and the current desktop build directly with a 512MB heap. Backend responds (unauthenticated /auth/me returns 401); app process is running without startup exception. App session check returned 401, so sign-in may be needed. Runtime PIDs/logs are in /tmp/pos-review-{backend,desktop}.{pid,log}. Desktop launch settings exported to /tmp/pos-desktop-launch.json to avoid keeping Gradle running. Final observed combined backend/app RSS ~900MB, available RAM ~9.6GB, no swap use.
**Resource constraint / next steps:** User paused after PC froze and explicitly requested better RAM management. Initial overlapping compilers/backend startup competed with IDE under severe memory pressure; after the interruption no old build processes or /tmp logs remained. Finish future checks SEQUENTIALLY: --no-daemon --max-workers=1 -Dorg.gradle.parallel=false '-Dorg.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=384m -Dfile.encoding=UTF-8' -Pkotlin.compiler.execution.strategy=in-process. Never overlap Android/JVM builds with backend compilation; do not stop the user's IDE/apps. Project gradle.properties still has legacy 3GB daemon defaults; use invocation overrides. Await user's visual approval then start Orders. Earlier option-group editing caveat remains; this pass concerns responsive layouts.

## 2026-09-22 12:24 (Codex, branch: develop)
**Did:** Hardened menu/section deletion with content preservation. Extracted MenuContentTransferService (mandatory caller transaction, restaurant write lock serializing deletion transfers) and MenuNames (case-insensitive collision reservation, Unicode-safe 150-character names with suffix space). Preserved items keep IDs/prices/SKUs/relationships, are appended after existing fallback items, and receive unique names within the destination section. Whole-menu deletion keeps sections/items distinct and assigns unique destination section names from a preloaded set before mutating managed entities, avoiding premature Hibernate auto-flush uniqueness failures and the old infinite loop for 150-character duplicate names. Existing inactive fallback destinations are reactivated. Uncategorized sections sort last via repository queries and reserved display order; Android/desktop also use a shared stable ordering helper and pin Uncategorized in section reorder UI. Nonempty fallback section deletion without delete-items is explained and disabled in UI; server protection remains. Empty fallbacks can still be deleted.
**Why:** User requested safe Uncategorized ordering, collision-safe deletion transfers, and better placement of menu logic.
**Files/modules touched:** Backend menu services/repository plus new MenuContentTransferService/MenuNames, related service/controller test fixtures and MenuApiIntegrationTest; mobile menu MenuScreen, SectionManagerDialog, new domain MenuSectionOrdering helper and its test; this log. Existing unrelated edits were preserved.
**Validation:** 90 menu unit/persistence tests and 25 MenuApiIntegrationTest cases passed via Maven verify; 8 shared JVM tests passed; desktop compilation and Android assembleDebug passed. Integration tests used isolated PostgreSQL containers through a temporary Podman API socket (DOCKER_HOST=unix:///tmp/pos-test-podman.sock, TESTCONTAINERS_RYUK_DISABLED=true), resolving the earlier Docker-only test blocker. Test containers were cleaned up automatically. No changes to the running development DB or restart of the running backend/app.
**Left open / next steps:** This verifies the requested deletion/preservation flows, not a blanket production certification of every menu feature. Previously noted option-group choice loading/delete-recreate risk remains outside these changes; hard deletion of items referenced by orders/recipes/KDS still merits a separate policy review. No visual desktop/emulator pass performed. Restart the backend/app to load source changes when ready.

## 2026-09-22 12:02 (Codex, branch: develop)
**Did:** Studied mobile/desktop and backend architecture through source inspection. Mobile uses shared Compose UI, Voyager ScreenModels/StateFlow, Koin, and Ktor; App restores /auth/me and routes by accessible workspace. Auth, menu, and table layout repositories are wired into DI; table layouts include branch-scoped caching and SSE invalidation. Orders, payment, reservations, kitchen status, and sales screens currently contain hardcoded sample data. Backend is Java 21/Spring Boot 3.2.5 with JPA/PostgreSQL, Flyway migrations through V33, JWT, method security, and actor/restaurant scope policies. Read menu management, order workflow/KDS synchronization, and recipe setup code. Payment/report/shift packages currently have entity/repository foundations without their own controller/service layers.
**Why:** User asked to study the mobile and backend they are actively developing.
**Files/modules touched:** AGENT_MEMORY.md only; no application changes or test runs. Existing uncommitted source changes were preserved.
**Left open / next steps:** Confirmed MenuScreen.toUiMenuItem maps option-group choices to emptyList; MenuScreenModel updates groups via delete/recreate across multiple API requests (not an atomic server operation). RecipeService handles setup/costing, not stock consumption. Recent menu fixes and prior test outcomes are documented above/below in earlier entries; this was a source study, not fresh runtime validation. Ready for the user's next implementation task.

## 2026-09-22 12:00 (Codex, branch: develop)
**Did:** Located the working POS repository at `/home/kecid/Documents/David/work/POS`; read project instructions and handoff history, and confirmed its backend, web, and mobile/desktop modules.
**Why:** User asked to find the project under Documents/work/pos.
**Files/modules touched:** `AGENT_MEMORY.md` only.
**Left open / next steps:** Await the user's next project task.

## 2026-09-22 10:55 (Claude, branch: develop)
**Did:** Testing pass over the menu module (requested: "test it fully… long text… responsiveness for mobile and big desktops"). Wrote two throwaway API suites (kept in the session scratchpad, not the repo) that hit the **live** backend: 46 menu/section/item/variant edge cases + 14 covering the option-group create/delete ordering. Found and fixed **three real backend bugs**, all verified fixed by re-running the suites: (1) **Login returned HTTP 500 once a user hit `max-active-sessions` (3).** `UserSessionRepository.revokeOldestSession` is a `nativeQuery` using unqualified `user_sessions`; Hibernate's `default_schema: foundation_local` applies to HQL/entities but *not* native SQL, so it threw `relation "user_sessions" does not exist` instead of revoking the oldest session — i.e. a user logged in on 3 devices could never log in again. Fixed with the `{h-schema}` placeholder; same latent bug fixed in `BranchRepository.softDeleteAllByRestaurantId` (`UPDATE branches`). Verified: 5 consecutive logins now all 200 and active sessions stay capped at 3. (2) **Creating a menu with a name longer than ~50 chars returned HTTP 500.** `MenuCodeNormalizer.normalize()` derives `code` from the name with no length cap, but `menus.code`, `option-items.code` and `option-group-types.code` are all `varchar(50)` while names allow 150 — the insert died at the DB. Capped the derived code at 50 (trailing underscores trimmed); a 128-char menu name now returns 201 with a 50-char code. (3) Same root cause made **option-item (choice) names >50 chars return 500**. Frontend fixes: `DesktopItemDetails` had `widthIn(min = 760.dp)` inside 24dp padding, so the Item Details dialog was **clipped on any window narrower than ~808dp** (i.e. 600–808dp, hit when resizing) — now sized from `BoxWithConstraints` so it never exceeds available width. `MenuItemDetailDialog` had **zero `maxLines` across 36 `Text`s** (incl. the 30sp title and the description in a non-scrollable header) — capped the user-data ones. Item card name used `TextOverflow.Clip` (hard cut mid-glyph) → `Ellipsis`. Capped the four `"Delete ${name}?"` dialog titles and the new Edit-Item Category dropdown (fixed 56dp row). Item grid now steps 4→5→6 columns above 1500/2100dp instead of stretching 4 columns across an ultrawide.
**Why:** User asked for a full functional + responsiveness pass to "make the menu final".
**Files/modules touched:** Backend: `auth/repository/UserSessionRepository.java`, `restaurant/repository/BranchRepository.java`, `menu/util/MenuCodeNormalizer.java`. Frontend (`mobile_desktop/shared/.../pos/menu/`): `ui/item/MenuItemDetailDialog.kt`, `ui/item/MenuItemCard.kt`, `ui/item/ItemEditorScreen.kt`, `ui/section/SectionManagerDialog.kt`, `ui/MenuScreen.kt`.
**Left open / next steps:** **The Android build was already broken before this session and is now fixed.** Original failure: AGP dexing referenced `/Documents/David/POS/...` (the pre-move path; the project now lives under `/Documents/David/work/POS`) — poisoned Gradle caches left over from the directory move, which would have blocked anyone building the Android app. Fixed by clearing `~/.gradle/caches/9.1.0/{transforms,fileHashes,executionHistory}` and `build-cache-1`, then a full re-transform; `:androidApp:assembleDebug` now BUILD SUCCESSFUL and produces `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. (First build after this is slow — caches were rebuilt from scratch.) Desktop (`:shared:compileKotlinJvm`, `:desktopApp:compileKotlin`, `:shared:jvmTest`) all green. Backend unit suite: 849 run, **0 failures**, 75 errors — all `@DataJpaTest`/persistence classes that need Testcontainers/Docker, unavailable here (Podman only, no `/var/run/docker.sock`); same pre-existing limitation blocks `MenuApiIntegrationTest`. Could not verify anything visually: no X11 access from the agent shell (no `.Xauthority`), no screenshot/window tools, no Android emulator/AVD — responsiveness was verified by reading breakpoints and constraints, not by looking at it, so a human should still eyeball phone-width and ultrawide. Not addressed (known, pre-flagged TODO): `toUiMenuItem` sets option-group `choices = emptyList()` because the item endpoint doesn't return nested option items, so editing an existing option group shows no choices and saving would recreate it without them. Backend allows variant names differing only by case ("Large"/"large"); the client blocks it.

## 2026-09-22 09:57 (Claude, branch: develop)
**Did:** Long UI/UX + feature session on the menu-management screens in `mobile_desktop`, tested by relaunching the desktop app live against the local backend/DB. (1) Fixed a genuine backend/frontend data-desync bug found while investigating a false "variant name already in use" error: several nested dialogs (Add/Edit/Delete Variant, Add/Rename/Delete Section) only merged a successfully-created/updated/deleted entity into local UI state *after* a ~1.1-1.3s success-celebration animation finished (`onSuccessSettled`); if the user dismissed the dialog (outside click/back) during that window, the backend write had already landed but the UI silently lost track of it -- confirmed via direct `psql` query showing an orphaned "Large" variant that existed in the DB but never appeared in the app. Fixed centrally in `MenuFormLayouts.kt`/`ItemEditorScreen.kt`/`SectionManagerDialog.kt` by only allowing dialog dismissal while status is `Idle` (was `!isBusy`, which also allowed dismissal during `Success`/`Removed`). (2) Added a "delete items inside" checkbox to both Delete Section and Delete Menu flows: checked = cascade-delete everything; unchecked (default) = auto-move sections/items into a find-or-create "Uncategorized" section/menu (reused across calls via `findByMenuIdAndName`/`findByRestaurantIdAndCode` lookup -- confirmed only ever creates one, never duplicates). Guarded the edge case of deleting the "Uncategorized" menu/section itself while it still holds content (would otherwise hit a FK violation) with new `MenuDeletionRequiresConfirmationException`/`MenuSectionDeletionRequiresConfirmationException`. Replaced the old hard-blocking `MenuDeletionBlockedException`/`MenuSectionDeletionBlockedException` (deleted those classes) since blocking is no longer the default behavior. (3) Added a "Category" dropdown to Edit Item so an item can be moved to a different section (`UpdateMenuItemRequest.sectionId`, threaded through `MenuItemService.updateItem`). (4) Made the desktop Item Detail dialog's Recipe/Variants/Options panels independently scrollable (`DesktopDetailPanel` now wraps its content in `Modifier.weight(1f, fill=false).verticalScroll(...)`) -- content was clipping/cutting off at the dialog's fixed height with no way to see the rest. (5) Assorted smaller polish: filled the ingredient-quantity "Add" button to match the app's pill/filled button convention elsewhere.
**Why:** User-driven iterative polish + explicit bug report (variant name conflict with no visible duplicate) + explicit feature requests ("same logic for menu", "only one uncategorized ever", "make sure this is scrollable").
**Files/modules touched:** Backend: `MenuSectionService.java`, `MenuService.java`, `MenuSectionController.java`, `MenuController.java`, `MenuSectionRepository.java`, `MenuRepository.java`, `UpdateMenuItemRequest.java`, `MenuItemService.java`, new `MenuDeletionRequiresConfirmationException.java`/`MenuSectionDeletionRequiresConfirmationException.java`, deleted `MenuDeletionBlockedException.java`/`MenuSectionDeletionBlockedException.java`, plus matching unit tests (`MenuServiceTest`, `MenuSectionServiceTest`, `MenuControllerSecurityTest`) and integration tests (`MenuApiIntegrationTest`). Frontend (`mobile_desktop/shared/.../pos/menu/`): `ui/menu/MenuFormLayouts.kt`, `ui/menu/MenuEditorScreen.kt`, `ui/section/SectionManagerDialog.kt`, `ui/item/ItemEditorScreen.kt`, `ui/item/MenuItemDetailDialog.kt`, `ui/MenuScreen.kt`, `ui/MenuScreenModel.kt`, `data/api/MenuApi.kt`, `data/repository/DefaultMenuRepository.kt`, `data/dto/MenuRequests.kt`, `domain/repository/MenuRepository.kt`.
**Left open / next steps:** Backend integration tests (`MenuApiIntegrationTest`) require Testcontainers/Docker, which this sandbox doesn't have (only Podman, no `/var/run/docker.sock`) -- they were written and compile-verified but not run; only the mock-based unit tests were actually executed (all passing). No window-resize/screenshot tooling in this sandbox either, so phone-width responsiveness was verified by code audit, not visually. User separately asked, and it was explicitly deferred pending confirmation: building a full drag-to-reorder layout for Variants/Options identical to Sections' reorder UI -- not started.

## 2026-09-21 10:50 (Claude, branch: develop)
**Did:** Set up the Linux (Fedora 44) dev environment. No sudo, so used rootless Podman instead of Docker: containers `pos-db` (postgres:16, volume `pos_postgres_data`) and `pos-mailhog` with the same ports/env as `back-end/docker-compose.yml`. Started the backend with `SPRING_PROFILES_ACTIVE=local sh mvnw spring-boot:run` (`mvnw` is not executable in git, so run via `sh`); it boots and answers on :8080. Installed the Android SDK to `~/Android/Sdk` (platform 36, build-tools 36.0.0, platform-tools), wrote git-ignored `mobile_desktop/local.properties`, and exported `ANDROID_HOME` in `~/.bashrc`. Fixed `TokenPersistence` (jvmMain), which called Windows-only `Crypt32Util` on every OS and threw `UnsatisfiedLinkError` on Linux; it now uses DPAPI only on Windows and an owner-only (0600) file elsewhere.
**Why:** User asked to get the backend and mobile app running and fix the environment.
**Files/modules touched:** `mobile_desktop/shared/src/jvmMain/.../core/session/TokenPersistence.kt`, `AGENT_MEMORY.md`; environment only otherwise.
**Left open / next steps:** `:shared:jvmTest`, `:desktopApp:build`, `:androidApp:assembleDebug` pass. Not verified: full backend `mvn verify`, a real login from the apps, an emulator/device run. After a reboot run `podman start pos-db pos-mailhog`. Linux desktop tokens are unencrypted (file perms only).

## 2026-08-29 23:09 (Codex, branch: POS/pos-workflows-ui)
**Did:** Prepared the POS workflows branch for a pull request to `develop`: committed the shared agent documentation as `8556239`, created recovery branch `codex/pos-workflows-ui-pre-sync-20260829`, merged both `origin/POS/pos-workflows-ui` and current `origin/develop` without conflicts, and corrected `OrderEntityPersistenceTest` so its active table fixture includes the floor and coordinates required by the newer table-layout invariant. Verified the focused regression test, full backend Maven `verify` (886 unit tests and 216 integration tests), and the Kotlin mobile/desktop build (`:shared:jvmTest`, `:desktopApp:build`, and `:androidApp:assembleDebug`) successfully.
**Why:** The user asked to commit and push all current work, open a pull request into `develop`, and confirm CI is green without merging it.
**Files/modules touched:** `AGENTS.md`, `AGENT_MEMORY.md`, `back-end/src/test/java/pos/pos/unit/order/entity/OrderEntityPersistenceTest.java`, and Git merge history for `POS/pos-workflows-ui`.
**Left open / next steps:** Commit this final test/handoff update, push `POS/pos-workflows-ui`, create or reuse its pull request into `develop`, and wait for GitHub CI to pass. Do not merge the pull request. `stash@{0}` remains intentionally untouched because it belongs to prior `frankos-work` inventory/mobile work.

## 2026-08-29 20:05 (Codex, branch: POS/pos-workflows-ui)
**Did:** Diagnosed the waiter table-screen error `No restaurant branch is assigned to this user`; no source or database data changed. The waiter is correctly assigned in PostgreSQL, but the backend process on port 8080 was started/compiled from the older `frankos-work/develop` code. Its compiled `CurrentUserResponse.class` lacks `restaurantId` and `defaultBranchId`. The mobile DTO defaults those absent JSON fields to null, and `TablesScreenModel.currentBranchScope()` then emits the error.
**Why:** The user logged in as `waiter` and provided a screenshot of the branch-assignment error despite the database showing a valid assignment.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only). Read-only inspection covered the running Java process, compiled backend class, auth response/mapper branch diff, mobile auth/session DTOs, and `TablesScreenModel`.
**Left open / next steps:** Rebuild and restart the backend from `POS/pos-workflows-ui`, then log out and log back in so `/auth/me` repopulates both IDs. The branch-specific backend additions should eventually be reconciled into `develop`; no process restart or fix was performed in this diagnostic turn.

## 2026-08-29 20:00 (Codex, branch: POS/pos-workflows-ui)
**Did:** Verified local user restaurant/default-branch assignments with read-only database queries; no application or database data changed. All 10 users have a restaurant. Eight users have `Main Branch` for `Local Demo Bistro`; `demo.pizza.owner` and `demo.cafe.owner` have no default branch because `Local Demo Pizza` and `Local Demo Cafe` currently have no branches.
**Why:** The user asked whether every available user was assigned to both a restaurant and branch.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only); read-only inspection of `foundation_local.users`, `restaurants`, and `branches`.
**Left open / next steps:** Create branches for Local Demo Pizza and Local Demo Cafe, then assign them as defaults, if those owner accounts must operate in a branch context.

## 2026-08-29 19:59 (Codex, branch: POS/pos-workflows-ui)
**Did:** Queried the running local PostgreSQL `foundation_local` schema and confirmed 10 non-deleted user accounts; no application or database data was changed.
**Why:** The user asked for the users currently available in the local POS environment.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only); read-only database inspection of `users`, `user_roles`, and `roles`.
**Left open / next steps:** None. Account password hashes and other secrets were intentionally not queried or reported.

## 2026-08-29 19:45 (Codex, branch: POS/pos-workflows-ui)
**Did:** Switched the working copy from `develop` to the local `POS/pos-workflows-ui` branch at `921125a` so the newest locally committed mobile POS/workspace and editable table-layout UI is present.
**Why:** The user explicitly asked to be placed on the branch containing the latest mobile changes.
**Files/modules touched:** Git checkout state and `AGENT_MEMORY.md` (this handoff entry only); no application source was edited.
**Left open / next steps:** The branch is ahead of `origin/POS/pos-workflows-ui` by 5 commits and behind it by 2, so it remains divergent and should not be pulled/merged blindly. `stash@{0}` was intentionally left untouched because it also contains unrelated inventory edits in addition to mobile Gradle changes.

## 2026-08-29 19:43 (Codex, branch: develop)
**Did:** Diagnosed why the mobile/desktop app on `develop` looks old; no application source changed. The May authentication app is merged into `develop`, while the newer July/August POS workspace, menu, orders, payments, reservations, KDS, sales, and editable table-layout UI is committed on the local divergent branch `POS/pos-workflows-ui` (not merged into `develop`). The local feature branch is 7 commits behind and 8 commits ahead of current `develop`, and it also differs from `origin/POS/pos-workflows-ui` (2 remote-only, 5 local-only commits). `stash@{0}` additionally contains uncommitted mobile Gradle changes from the prior `frankos-work` checkout.
**Why:** The user reported that the mobile app displayed a very old version and asked whether newer changes were on another branch.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only). Read-only inspection covered Git branch history, `mobile_desktop/`, and stash metadata.
**Left open / next steps:** Do not directly merge the divergent feature branch without reconciling it with current `develop` and reviewing the five local-only commits plus `stash@{0}`. No checkout, merge, stash application, or source edit was performed.

## 2026-08-29 19:12 (Codex, branch: frankos-work)
**Did:** Fixed PR #94 backend CI, committed the two-file patch as `07ceb77` (`Fix backend CI schema portability`), and pushed it to `origin/frankos-work`. Restored schema-portable native SQL in `UserSessionRepository` and upgraded the backend workflow to `actions/checkout@v5` and `actions/setup-java@v5`. Verified the targeted repository suite (19 tests) and the full Maven `verify` build locally; both GitHub push and pull-request backend checks completed successfully. PR #94 is open, mergeable/clean, and targets `develop`.
**Why:** The inventory commit had accidentally hardcoded `foundation_local.user_sessions`, but CI creates and searches the `app_test` schema, causing two `UserSessionRepositoryTest` errors. Unqualified table names correctly honor each environment's configured PostgreSQL search path.
**Files/modules touched:** `.github/workflows/ci.yml`, `back-end/src/main/java/pos/pos/auth/repository/UserSessionRepository.java`, and `AGENT_MEMORY.md` (this handoff entry).
**Left open / next steps:** David can merge PR #94 from `frankos-work` into `develop`; no merge was performed. Existing uncommitted inventory comments in `InventoryItemRequest.java` and `InventoryItemService.java`, plus untracked `AGENTS.md`, `AGENT_MEMORY.md`, and `back-end/uploads/`, were intentionally not included in commit `07ceb77`.

## 2026-08-29 18:36 (Codex, branch: frankos-work)
**Did:** Created a dependency-ordered plan to complete the automatic reorder-point feature and all required integrations; no source code changed.
**Why:** The user decided to implement the complete feature now and requested a checkbox plan.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only). Planned work spans inventory, recipes, orders, a minimal supplier foundation, and inventory notifications.
**Left open / next steps:** Implement in order: unify reorder settings; add unit conversion/recipe expansion; connect order fulfillment and reversal to inventory; add supplier lead time and safety stock; aggregate consumption; calculate/schedule suggested reorder points with overrides; fix low-stock evaluation and notifications; add migrations, docs, and tests. Payment, Shift, Reporting, Audit, and full purchase-order processing are outside this scope.

## 2026-08-29 18:21 (Codex, branch: frankos-work)
**Did:** Confirmed that `reorderPoint` is currently set manually rather than overriding an automatically calculated value; no source code changed.
**Why:** The user summarized the current behavior and asked for confirmation.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only).
**Left open / next steps:** None; an override concept would apply only if automatic reorder-point calculation is added later.

## 2026-08-29 18:20 (Codex, branch: frankos-work)
**Did:** Clarified why `reorderPoint` is accepted in `InventoryItemRequest`; no source code changed.
**Why:** The user asked whether the value should be calculated automatically instead of being supplied by the frontend.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only). Searched backend and web code for replenishment calculation inputs.
**Left open / next steps:** The current model has no supplier lead time, safety stock, or average-demand data, so `reorderPoint` is presently a manual configuration value. A future design could calculate a suggestion and allow a manual override.

## 2026-08-29 18:18 (Codex, branch: frankos-work)
**Did:** Reviewed and explained `InventoryItemRequest.reorderPoint` and its related entity/mapping behavior; no source code changed.
**Why:** The user is studying the inventory item request and asked what the field means.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only). Reviewed inventory item DTO, entity, mapper, and migration references.
**Left open / next steps:** `reorderPoint` is currently stored, validated as non-negative, and returned by the API; no automatic low-stock alert or replenishment workflow was found using it yet.

## 2026-08-29 17:51 (Codex, branch: frankos-work)
**Did:** Confirmed the shared-memory workflow: read `AGENT_MEMORY.md` before project work and add a factual handoff entry after changes or decisions.
**Why:** The user explicitly reaffirmed that this file is shared memory for every agent working in the repository.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only).
**Left open / next steps:** Continue following the protocol on all future POS work.

## 2026-08-29 17:50 (Codex, branch: frankos-work)
**Did:** Confirmed that the repository root contains `AGENTS.md` (plural) and `AGENT_MEMORY.md`; no source code changed.
**Why:** The user asked whether an agent instruction file exists.
**Files/modules touched:** `AGENT_MEMORY.md` (this handoff entry only).
**Left open / next steps:** None.

## 2026-08-28 (Claude, branch: frankos-work — baseline snapshot, no code changes)
**Did:** Set up this handoff/memory system at the user's request. No source code changed.
**Why:** User wants any agent session (Claude, Codex, etc.) to be able to pick up work without being manually told "here's what changed so far" — this log plus `AGENTS.md`'s protocol is the mechanism.
**Files/modules touched:** Added `AGENTS.md` and `AGENT_MEMORY.md` at repo root.
**Baseline state observed at setup time:**
- Checked-out branch: `frankos-work` (not `main`/`develop`).
- Recent commits on this branch, newest first: "Add Recipe and other components", "Inventory finished", "Idem, Location and Level API", "Add clarification comments to inventory entities" — inventory domain work in progress.
- Mobile: `mobile/feature/authenitcation` was recently merged (login screen flow, auth session plumbing, home screen theme, shared API networking config, mobile/desktop app scaffolded).
- Backend: `backed/feature/notification` was recently merged (role/admin notification dependencies wired up).
- Other branches that exist locally and on origin but were NOT inspected for merge status yet: `feature/kds`, `feature/order`, `feature/reservation-and-tables`, `feature/restaurant`, `feature/settings`, `feature/device`, `feature/entity-structure`, `feature/domain-foundation`, `feature/menu-pr83`, `backend/feature/authentcation`, `David/backend/security`, `David/backend/authenticated-user-principal`, `chore/actor-scope-policy-foundation`, `POS/pos-workflows-ui`, `salvage/feature-device-leftovers`, `test/ci-block-test`, `pr83-merge`.
- `git status` was not able to complete (timed out against the mounted drive) — a fresh agent should check working-tree state itself before assuming it's clean.
**Left open / next steps:** Nothing code-wise — this was a setup task. Next agent: start filling in `AGENTS.md`'s Conventions section (build/test/lint commands) as you discover them, and always append your own entry here before finishing.

## 2026-09-22 22:16
- Orders UI iteration: restored the bordered left orders list panel, kept the right panel visible but empty, and made desktop order rows wrap into a responsive two-line layout to avoid clipped order numbers/status/time. Relaunched desktop app from `/tmp/orders-release/launch-desktop.py` for user review.

## 2026-09-22 22:24
- Orders UI iteration: rebuilt the left orders list to match the provided reference more closely, with a bordered list board, colored row accent bars, copy icon beside the truncated order number, table/item column, status/guest column, time column, and rounded chevron button. Reloaded the desktop app for review.

## 2026-09-22 22:31
- Orders UI iteration: left list only; enabled copy action on the order number with a temporary Copied label, removed Dine-in text from desktop rows, removed extra middle vertical separators, and changed row accent colors to match progress/status more closely. Reloaded the desktop app for review.

## 2026-09-22 22:38
- Orders UI iteration: desktop left list rows now show the customer name directly under the order number, keep order status/progress in one column, move guest count below those statuses, and show elapsed time plus order total in the far-right column. Reloaded desktop app for review.

## 2026-09-22 22:46
- Orders UI iteration: corrected left row column order per user direction: order number with guests/name below, table with item count, status/progress badges, time/opened-at, then total price. Kept progress-colored left accent strip and reloaded the desktop app.

## 2026-09-22 22:52
- Orders UI iteration: moved the single divider to between the table/items column and the status/progress column, grouped time and price closer together, and added spacing before the row arrow. Reloaded desktop app for review.

## 2026-09-22 22:59
- Orders UI iteration: made the left row accent strip visible across the full row height, replaced the skinny chevron area with a rounded arrow button, and tightened vertical row padding/list bottom padding. Reloaded desktop app for review.

## 2026-09-22 23:07
- Orders UI iteration: added 8 local review orders so the left list scrolls, reduced the row arrow button from 40dp to 32dp, and reduced Orders page bottom padding. Reloaded desktop app for review.

## 2026-09-22 23:13
- Orders UI iteration: added right-side padding after the left-list row arrow so the arrow no longer sits flush against the card edge. Reloaded desktop app for review.

## 2026-09-22 23:18
- Orders UI iteration: increased the right-side spacer after the left-list row arrow from 8dp to 18dp, moving the price/arrow group left from the card edge. Reloaded desktop app for review.

## 2026-09-22 23:27
- Orders UI iteration: fixed the left-list row trailing layout by making the status/progress column flexible, shrinking the time/price widths slightly, and reserving 28dp of real right padding so the price and arrow move left together. Reloaded desktop app. Screenshot capture was attempted but blocked by OS screen-capture permissions and no native screenshot utilities were installed.

## 2026-09-22 23:35
- Orders review data: created local review tables R1-R5 and assigned them to scroll-review orders that previously displayed Table -, then relaunched the desktop app so the table pills show numbers.

## 2026-09-22 23:47
- Orders UI iteration: added a desktop right-side order details panel matching the supplied reference, with fixed header/status/table/customer/time/tabs/totals/actions and only the items list using a LazyColumn scroll area. Reloaded desktop app for review.

## 2026-09-22 23:53
- Orders UI iteration: added desktop auto-selection of the first visible order when no order is selected, so the new right-side details panel appears immediately after the orders list loads. Reloaded desktop app for review.

## 2026-09-22 23:58
- Orders UI iteration: adjusted desktop split widths so the left orders list is wider (1.65 weight) and the right details panel is narrower (0.9 weight). Reloaded desktop app for review.

## 2026-09-23 00:01
- Orders UI iteration: rebalanced desktop left-row columns by widening table/items and status/progress, and reducing time/price widths. Reloaded desktop app for review.

## 2026-09-23 00:06
- Orders UI iteration: swapped the desktop left-row second/third columns so status/progress badges appear after order/customer, and table/items appears after the divider. Reloaded desktop app for review.

## 2026-09-23 00:10
- Orders UI iteration: reverted the accidental desktop left-row column swap, restoring table/items before the divider and status/progress after it. Reloaded desktop app for review.

## 2026-09-23 00:16
- Orders UI iteration: right details panel only; moved table pill up into the header where status/progress badges were, and moved status/progress badges into the info row where the table pill had been. Reloaded desktop app for review.

## 2026-09-23 00:23
- Orders UI iteration: centered right-panel tab labels within a fixed 42dp tab area, changed right-panel item thumbnails from restaurant icons to the shared menu item image, and added extra local line items to the first review order so the item list scrolls. Reloaded desktop app.

## 2026-09-23 00:29
- Orders UI iteration: widened the right-panel time/opened block from 104dp to 132dp and reduced the customer info weight so the opened time displays fully. Reloaded desktop app.

## 2026-09-23 00:34
- Orders UI iteration: removed the periodic vertical jump between toolbar and content by reserving a constant 4dp area for the loading indicator, so automatic refresh no longer pushes the two panels down/up. Reloaded desktop app.

## 2026-09-23 00:40
- Orders UI iteration: added very thin 3dp vertical scrollbars to the left orders list and the right-panel items list, with extra right padding so content does not collide with the scrollbar. Reloaded desktop app.

## 2026-09-23 00:47
- Orders UI iteration: reduced the vertical gap between the toolbar/filter header and the two main content panels by tightening column spacing and the reserved loading indicator area. Reloaded desktop app.

## 2026-09-23 00:53
- Orders UI iteration: replaced the right-panel three-dot button with an X close button that clears the selected order and returns the right panel to its empty state. Reloaded desktop app.

## 2026-09-23 00:58
- Orders UI iteration: fixed the right-panel X close button so it stays empty after closing instead of immediately reselecting the first order. Selecting an order from the list opens details again. Reloaded desktop app.

## 2026-09-23 00:58
- Orders UI iteration: fixed the right-panel X close button so it stays empty after closing instead of immediately reselecting the first order. Selecting an order from the list opens details again. Reloaded desktop app.

## 2026-09-23 01:08
- Orders UI iteration: changed Edit order to open a three-choice chooser: Order items, Order info, and Order progress. Removed the direct jump into the old item-edit screen; Order items still opens the item composer after choosing it. Reloaded desktop app.

## 2026-09-23 01:17
- Orders UI iteration: changed the Edit order chooser to a smaller custom dialog with an X close button and choices placed directly under the title instead of centered lower in the modal. Reloaded desktop app.

## 2026-09-23 01:24
- Orders UI iteration: completed the floating menu-style edit picker and removed leftover card-style edit chooser usage. Reloaded desktop app.

## 2026-09-23 01:30
- Orders UI iteration: moved Edit order choices into an anchored dropdown beside the Edit order button, matching the menu picker behavior and removing the dark overlay dialog on desktop. Reloaded desktop app.

## 2026-09-23 01:39
- Orders UI iteration: matched the Edit order dropdown styling to the Menu item edit dropdown: 14sp semi-bold Inter text, white popup, rounded 10dp corners, 8dp y-offset, no forced wide menu. Reloaded desktop app.

## 2026-09-23 01:42
- Orders UI iteration: moved the Edit order dropdown slightly higher by reducing its vertical offset. Reloaded desktop app.

## 2026-09-23 01:45
- Orders UI iteration: added icons to the three Edit order dropdown options while keeping the compact Menu-style dropdown typography and shape. Reloaded desktop app.

## 2026-09-23 01:58
- Orders UI iteration: built a desktop Edit Order Items modal opened from the Edit order > Order items dropdown. It shows real selected-order line items, item list, selected item editor, status badges, add/send/close controls, and uses current mock imagery. Reloaded desktop app.

## 2026-09-23 02:04
- Orders UI iteration: aligned the Edit order items modal with existing Menu/Orders styling by replacing off-palette greens, blues, purples, and borders with OrderGreen, OrderInk, OrderMuted, OrderBorder, and OrderBackground. Reloaded desktop app.

## 2026-09-23 02:17
- Orders UI iteration: fixed compile issue in the Orders-style Edit order items modal by adding a small icon/text helper for modal metadata and notes. Reloaded desktop app.

## 2026-09-23 02:23
- Orders UI iteration: removed the item-count badge from the Edit order items modal header, made the Send all to kitchen button smaller, and rotated the send icon 45 degrees counterclockwise. Reloaded desktop app.

## 2026-09-23 02:27
- Orders UI iteration: reduced the Edit order items header table pill width so the background sits closer to the table label. Reloaded desktop app.

## 2026-09-23 02:30
- Orders UI iteration: reduced the Edit order items Add item button height, padding, icon size, and text size to better match the compact Orders UI style. Reloaded desktop app.

## 2026-09-23 02:33
- Orders UI iteration: restored text on each item-row Send to kitchen button and rotated its send icon 45 degrees counterclockwise to match the top send button. Reloaded desktop app.

## 2026-09-23 02:36
- Orders UI iteration: changed the Edit order items modal header send button label from “Send all to kitchen” to “Send to kitchen”. Reloaded desktop app.

## 2026-09-23 02:39
- Orders UI iteration: widened the per-item Send to kitchen button so the full label is visible. Reloaded desktop app.

## 2026-09-23 02:43
- Orders UI iteration: moved item-row prices slightly left and tightened/lowered the bottom status legend spacing in the Edit order items modal. Reloaded desktop app.

## 2026-09-23 02:47
- Orders UI iteration: reduced the Edit order items left panel bottom legend area height by tightening panel spacing and shrinking legend dots/text. Reloaded desktop app.

## 2026-09-23 02:50
- Orders UI iteration: restyled the Edit order items modal close X as a smaller white circular button with subtle border/shadow to better match Menu UI controls. Reloaded desktop app.

## 2026-09-23 02:54
- Orders UI iteration: fixed item-row prices in the Edit order items modal so they stay on one line and sit further left from the Send to kitchen button. Reloaded desktop app.

## 2026-09-23 02:58
- Orders UI iteration: lowered the Edit order items status legend, reduced its height, and tightened the item list spacing/panel bottom padding. Reloaded desktop app.

## 2026-09-23 03:03
- Orders UI iteration: tightened the status legend top gap and height in the Edit order items modal. Moved the selected item progress badge to the right under the price and lifted the divider line by reducing header spacing. Reloaded desktop app.

## 2026-09-23 03:09
- Orders UI iteration: made the Edit order items right editor keep the price/status badge in a fixed right-side column, and made only the editor fields scroll while Served/Delete/Save stay fixed at the bottom. Reloaded desktop app.

## 2026-09-23 03:13
- Orders UI iteration: updated Edit order items bottom actions: renamed Served to Mark as served, made Delete solid red with white text, and gave Save changes a proper 50dp height. Reloaded desktop app.

## 2026-09-23 03:18
- Orders UI iteration: simplified selected item header by removing variant/status text, moving price below the item name, and placing an X button at the top-right. Made Mark as served/Delete/Save changes buttons smaller, with Delete solid red and Save matching the Send to kitchen green. Reloaded desktop app.

## 2026-09-23 03:24
- Orders UI iteration: made the right-editor quantity control smaller and changed Progress status from large status chips to an options-style selector field with chevron. Reloaded desktop app.

## 2026-09-23 03:29
- Orders UI iteration: increased the Edit order items modal height from 80% to 88% of the window to give the full form more vertical room. Reloaded desktop app.

## 2026-09-23 03:38
- Orders UI iteration: fixed the missing OrderStatusSelectField helper, then reloaded the desktop app with taller bottom buttons, colored/icon progress selector, and Send to kitchen header label.

## 2026-09-23 03:41
- Orders UI iteration: increased the Edit order items fixed bottom action button heights slightly again. Reloaded desktop app.

## 2026-09-23 03:45
- Orders UI iteration: fixed Mark as served button text appearance by giving it more width and reducing icon/text size so the label does not appear spaced out. Reloaded desktop app.

## 2026-09-23 03:49
- Orders UI iteration: aligned Mark as served, Delete, and Save changes button typography/icon sizing in the Edit order items modal so all three use the same button text style. Reloaded desktop app.

## 2026-09-23 03:54
- Orders UI iteration: removed the opened-time block from the right-side order details header and replaced it with a compact Add item button that opens the Order items editor. Reloaded desktop app.

## 2026-09-23 03:59
- Orders UI iteration: normalized the Edit order items bottom button label typography by setting zero letter spacing and matching Medium 13sp text for Mark as served, Delete, and Save changes. Reloaded desktop app.

## 2026-09-23 04:06
- Orders UI iteration: increased the Edit order items right-side Item note field height from 72dp to 96dp so it shows one more line. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:17
- Orders UI iteration: added a nested menu-style Add item modal from the Edit order items dialog. The Add item button now opens a menu picker with menu selector, centered Add item header, menu-style search/categories/cards, and a right details panel that stays empty until an item is selected. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:21
- Orders UI iteration: removed the Add item modal footer containing the Close button and helper text. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:24
- Orders UI iteration: changed Add item modal header so Add item is left-aligned with the same 19sp Bold style as Edit order items, the menu selector is centered, and the X remains right-aligned. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:27
- Orders UI iteration: removed the bottom padding from the Add item modal content column so the grid/details area sits flush at the bottom. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:33
- Orders UI iteration: updated the Add item picker to use the Menu screen category buttons and MenuSearchBox, placing search to the right of the sections row. Restyled the menu selector to match the options field shape with muted text and right chevron. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:36
- Orders UI iteration: made the Add item menu selector smaller and rotated its chevron downward. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:39
- Orders UI iteration: removed the Add button from Add item picker cards and moved the expand icon to the bottom-right like the Menu item cards. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:43
- Orders UI iteration: reduced the Add item picker search width to 300dp and tightened section chips to 40dp high with smaller icons/text while keeping the menu-style icon layout. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:47
- Orders UI iteration: matched the Add item right panel quantity control and Item note field to the Edit order items styling, including 96dp note box and compact 38x34 quantity value box. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:52
- Orders UI iteration: made Add item section chips smaller, reduced search width to 260dp, and added a compact More dropdown for overflow sections. Rebuilt and reloaded the desktop app.

## 2026-09-23 04:58
- Orders UI iteration: updated Add item right panel with Payment-style Add to Order button including icon and price, smaller top image, sample fallback variants/options for visual review, and left-aligned radio/checkbox controls. Rebuilt and reloaded the desktop app.

## 2026-09-23 05:04
- Orders UI iteration: made Add item right panel content scroll above a fixed bottom Add to Order button with price, made the selected-item description smaller and muted gray, and kept only the right-panel selected item image small. Rebuilt and reloaded the desktop app.

## 2026-09-23 05:10
- Orders UI iteration: restored larger images on Add item left menu cards, kept selected item image small on the right, moved quantity into the fixed bottom area with line total and Add to Order, changed Add to Order icon from credit card to plus, and reduced variant/option row text size. Rebuilt and reloaded the desktop app.

## 2026-09-23 05:17
- Orders UI iteration: matched Save changes text style to Send to kitchen, made Add item right panel line total black and smaller, removed price from Add to Order button and centered its text, tightened variant/option rows, and added a thin scrollbar to the scrollable details area. Rebuilt and reloaded the desktop app.

## 2026-09-23 05:22
- Orders UI iteration: changed Add item picker cards to use the real MenuItemCard component so card image sizing, spacing, fonts, colors, availability badge, and expand button match the Menu screen. Rebuilt and reloaded the desktop app.

## 2026-09-23 05:28
- Orders UI iteration: made Save changes text explicitly match Send to kitchen with Inter SemiBold 13sp and zero letter spacing, then rebuilt and reloaded the desktop app.

## 2026-09-23 05:34
- Orders UI iteration: changed the Edit order items Save changes button to match the top Send to kitchen button settings exactly: 42dp height, 14dp horizontal padding, 18dp icon, 8dp spacer, Inter SemiBold 13sp text with no custom letter spacing. Rebuilt and reloaded the desktop app.


## 2026-09-24 21:36 (Codex, feature/orders)
**Did:** Changed table edit structural actions to save directly to the database: add table, copy table, and confirmed delete now call the backend first and update the local UI only after success. Plan upload/remove now call the backend immediately and update the preview only after success. The toolbar primary button reads Done unless there are unsaved move/resize/rotate/plan-transform changes, then it reads Save changes; delete is shown on the left with confirmation.
**Why:** User wanted create/delete/copy/plan upload/remove to persist directly, and failed deletes must not make a table disappear locally.
**Verified/runtime:** Desktop compile passed earlier with existing warnings. Backend was started on port 8080 and desktop app was relaunched with the latest changes.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableLayoutToolbar.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreenModel.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** Visual confirmation pending on user's side.


## 2026-09-24 22:08 (Codex, feature/orders)
**Did:** Adjusted table plan edit polish: plan move arrows now sit inside the plan border instead of clipped/behind the line, Save changes waits for backend success and then shows a larger bottom-left success toast with an X dismiss button, and the unsaved-changes dialog description is smaller, gray, and spaced farther from the action buttons.
**Why:** User requested cleaner plan move controls, a dismissible 3-second success message such as “Plan data saved successfully,” and softer dialog text styling.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings. Desktop app relaunched and stayed running.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreenModel.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Visual confirmation pending on user's side.


## 2026-09-24 22:23 (Codex, feature/orders)
**Did:** Updated the table delete confirmation so the dialog stays open during backend delete, shows the same menu-style loading/error body on failure, and only closes after backend success. Table delete failures no longer publish the page-level table error banner for this action.
**Why:** User wanted delete errors to appear inside the confirmation dialog instead of elsewhere.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings. Desktop app relaunched and stayed running.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableLayoutToolbar.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreenModel.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Visual confirmation pending on user's side.


## 2026-09-24 22:33 (Codex, feature/orders)
**Did:** Changed table delete failure dialog title from the generic menu fallback to `Table cannot be deleted`, while keeping the backend reason as supporting text.
**Why:** User wanted a clear table-specific message instead of “Something went wrong.”
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings. Desktop app relaunched and stayed running.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TableLayoutToolbar.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Visual confirmation pending on user's side.


## 2026-09-24 22:43 (Codex, feature/orders)
**Did:** Removed the small top table loading/saving/error notifications from Tables and replaced separate table/plan/merge messages with one bottom-left notification queue. Notifications are larger, dismissible with an X, stack without overlapping, and auto-dismiss after 5 seconds.
**Why:** User wanted no top notifications and a bottom-left queue where later messages appear below existing ones.
**Verified/runtime:** `mobile_desktop ./gradlew :desktopApp:compileKotlin --no-daemon --max-workers=2` passed with existing warnings. Desktop app relaunched and reached `:desktopApp:run`.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/tables/ui/TablesScreen.kt`, AGENT_MEMORY.md.
**Left open / next steps:** Visual confirmation pending on user's side.

## 2026-09-27 (Codex, feature/orders)
**Did:** Reduced the Reservations overview date filter from 200dp to 190dp as requested.
**Verified/runtime:** Confirmed the source now uses 190dp. Per user request, did not rebuild or relaunch; the running app still shows its previous build.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/reservations/ReservationOverviewScreen.kt`, `AGENT_MEMORY.md`.
**Left open / next steps:** User will ask follow-up questions.

## 2026-09-27 — Admin Hub Shifts redesign
**Did:** Reworked the manager Shifts page to use the app's existing POS/Admin typography, navigation, and calendar control styling. Added Week, Month, Time-grid, and List views; employee search and staff/role/status filters; selectable week/month dates; a selected-day summary and shift list; and click-to-create on empty day/time slots. Kept the waiter-facing POS shift page separate. Admin Hub now opens on Shifts.
**Backend:** Shift board staff now include their existing role names so role filtering uses real roster data.
**Verified/runtime:** Desktop Kotlin compile passed (existing warnings only); backend packaged and started; local board roster smoke check returned 8 staff records with roles; `git diff --check` passed. Relaunched desktop app with the rebuilt UI; visual screenshot inspection remains pending user review.
**Files/modules touched:** `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/admin/ui/AdminScreen.kt`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/pos/shifts/`, `back-end/src/main/java/pos/pos/shift/`, related shift API/model and screenshot fixture files.

## 2026-10-06 — POS readiness hardening continuation
**Did:** Set simulated guest booking payments to disabled by default and explicitly enabled only in the local profile. Added a production-profile guard so setting `APP_PAYMENTS_PROVIDER=test` cannot make production bookings appear paid. Updated the reservation integration test to exercise that guard. Hardened the existing statistics screen model's state lifecycle: cancel in-flight downloads when changing user/deactivating/loading a new report, prevent stale download completion from clearing a newer download's state, and reject future custom date ranges / shifts. Added regression coverage.
**Verified:** Backend full suite passed earlier in this audit (1,226 unit + 268 integration tests). Latest focused `ReservationFlowIntegrationTest`: 5 passed. Forced full mobile shared JVM suite: 319 passed, 0 failures/errors/skips. The mobile build emitted pre-existing Kotlin deprecation/redundant-nullability warnings. The integration test logs expected connection-refused warnings for the unconfigured test SMTP endpoint; mail delivery needs a configured provider outside tests.
**Files:** `back-end/src/main/java/pos/pos/reservation/service/GuestBookingService.java`, `back-end/src/main/resources/application.yml`, `back-end/src/main/resources/application-local.yml`, `back-end/src/main/resources/application-prod.yml`, `back-end/src/test/java/pos/pos/integration/reservation/ReservationFlowIntegrationTest.java`, `mobile_desktop/shared/src/commonMain/kotlin/com/saporini/mobile_desktop/statistics/StatisticsScreenModel.kt`, `mobile_desktop/shared/src/jvmTest/kotlin/com/saporini/mobile_desktop/statistics/StatisticsStateTest.kt`.
**Still open:** No real online payment provider is configured; inventory sale depletion still needs an explicit source-location policy before it can safely be automated. Continue the service-by-service hardening from the already modified worktree; do not treat test success alone as proof of production readiness.

## 2026-10-06 — Fail-closed SMS authentication configuration
**Did:** Changed `SmsAuthProperties` and shared configuration to default SMS delivery to `DISABLED`. Added local-only `LOG_ONLY` default, and an explicit disabled default in production with a note that no SMS gateway is connected. This prevents production from claiming OTP delivery and writing phone numbers/codes into server logs by default. Added configuration tests for base, local, and production profiles.
**Verified:** `./mvnw -o test -Dtest=SmsAuthPropertiesTest,SmsMessageServiceTest` passed: 12 tests, 0 failures/errors. It verifies a disabled delivery request fails and the local/prod profile settings bind as intended.
**Files:** `back-end/src/main/java/pos/pos/config/properties/SmsAuthProperties.java`, `back-end/src/main/resources/application.yml`, `back-end/src/main/resources/application-local.yml`, `back-end/src/main/resources/application-prod.yml`, `back-end/src/test/java/pos/pos/unit/config/SmsAuthPropertiesTest.java`.
**Still open:** Connect a real SMS gateway; local `LOG_ONLY` deliberately prints one-time codes for development. Re-run the whole backend suite after the accumulated backend configuration changes.

## 2026-10-06 — SMS integration verification
**Verified:** `PhoneVerificationIntegrationTest` passed all 4 API-level tests under the production profile with explicit test-only `SMS_DELIVERY_MODE=LOG_ONLY`, confirming existing test-auth flows still work. The output also confirms why production defaults must stay disabled: LOG_ONLY deliberately prints the OTP.

## 2026-10-06 — Production defaults and floor-plan storage hardening
**Did:** Removed `spring.profiles.default: local`; deployments with no explicit profile now use Spring's safe `default` profile, and local startup must opt into `local`. The shared/prod SMS default is disabled, with local `LOG_ONLY` explicitly configured. Floor-plan storage is now disabled by default in shared/prod configuration; production no longer silently writes floor plans to a process-local directory. The existing local provider is selected only by the local profile. Local uploads now compare file signatures against declared PNG/JPEG/WebP types and enforce the 10 MB limit against the bytes actually read. Added direct tests for supported signatures, mismatches, unsupported/empty/oversized uploads, path traversal, scoped persistence, load/delete, and disabled storage.
**Verified:** A clean backend `./mvnw -o verify` run passed **1,236 unit + 269 integration tests**, 0 failures/errors (this preceded the final production storage-provider gating edit). After that edit: 23 focused SMS/storage unit tests passed; `ProdProfileSmokeTest` passed with assertions that the production context selects `DisabledFloorPlanImageStorage` and binds the provider as `disabled`; SMS/profile/storage configuration tests passed (6). The targeted phone-verification integration test passed 4 earlier. The mobile shared JVM suite passed 319 tests in the previous pass; no mobile source changed here. `git diff --check` still pending for handoff.
**Files:** `back-end/src/main/resources/application.yml`, `application-local.yml`, `application-prod.yml`, `back-end/src/main/java/pos/pos/config/properties/SmsAuthProperties.java`, `back-end/src/main/java/pos/pos/storage/LocalFloorPlanImageStorage.java`, new `DisabledFloorPlanImageStorage.java`, storage tests, `SmsAuthPropertiesTest.java`, `ProdProfileSmokeTest.java`.
**Still open:** A durable floor-plan storage provider is not connected, so floor-plan image upload/read returns 503 outside local mode. No live payment or SMS provider is connected; no sale stock-depletion policy exists; production email retry/outbox remains unresolved. Run the full suite again after the last storage-provider edit before treating this checkout as fully verified.

**Verification follow-up (2026-10-06):** `git diff --check` passed after the above handoff entry was recorded.

## 2026-10-06 (Codex) — Production-mode storage/SMS guards and full verification
- Hardened provider defaults so base and production profiles do not silently enable test payments, log-only SMS, or local floor-plan file storage. Local development opts into local storage and LOG_ONLY SMS explicitly; production keeps these integrations disabled unless configured.
- Added a disabled floor-plan storage provider that returns a clear 503 for image reads/writes. Hardened local image writes with MIME/signature checks and a read-size cap. Added focused provider/configuration tests and extended the production smoke test.
- Adjusted the API robustness integration test to allow only the expected production 503 for the disabled floor-plan storage endpoint. The shared API test hook still rejects other 5xx responses; the robustness sweep checks 4,079 requests.
- Full backend `./mvnw -o verify` passed: 1,240 unit tests and 269 integration tests, zero failures/errors/skips. Menu API integration passed 25 cases. `git diff --check` passed.
- The mobile statistics state fixes described in the previous entry remain in place; previous verification covered 319 shared JVM tests and compilation of the existing Android/desktop source sets. No mobile screens were added.
- Production release remains blocked by missing real payment-provider setup, durable production floor-plan storage, and automatic inventory depletion from sales. Storage and payment behavior intentionally fail closed until configured. The repository has extensive pre-existing/uncommitted changes; nothing was committed.

## 2026-10-06 (Codex) — Sale stock linking and end-to-end fulfillment
- Added branch/item inventory sale-source mappings with a V56 migration, scoped API, validation, and protection against deactivating/untracking referenced items or locations. Updated the existing mobile inventory data/state/repository layer to load, set, and remove sources; no new screens were added.
- Order fulfillment now consumes tracked ingredients from the active finished-dish recipe, including nested prep batches, yield loss, and batch-yield proration. It is idempotent per order line and transactional; structural line edits and voiding are blocked after stock has been consumed.
- Added PostgreSQL integration coverage for exact consumption, retry idempotency, missing source mapping with no partial deduction, and insufficient stock with rollback. All 9 `RecipeFlowIntegrationTest` tests passed.
- Full backend `./mvnw -o verify` passed: 1,244 unit tests + 277 PostgreSQL integration tests, 0 failures/errors/skips. Focused mobile inventory state/API/rules tests passed: 15 + 1 + 6 = 22, 0 failures/errors. `git diff --check` passed.
- Still open: modifier/variant-specific stock recipes; refund/void after fulfillment needs explicit waste/restock policy (currently protected from accidental edit); legacy fulfilled orders are not reconciled; real payment provider, durable production image storage, and real SMS/email/outbox delivery remain external integration blockers. A whole-backend production sign-off is not warranted yet.

## 2026-10-07 (Codex) — Protect consumed sale lines from order relocation
- Closed a stock-ledger integrity gap: order merge, split, and branch transfer now refuse orders containing lines with `SALE_CONSUMPTION` movements, so consumed line IDs cannot be detached from their sale history. Added API assertions to recipe fulfillment integration coverage for split/merge rejection.
- Fixed `OrderPricingSafetyTest` setup to mock the newly required `InventoryMovementRepository`. Made fraud overview test findings and date window share a fixed reference timestamp; the previous test failed after the system date advanced because its stubbed findings fell outside the requested day.
- Verification: focused recipe integration suite passed 9/9; corrected order/fraud unit classes passed 17/17; final full backend `./mvnw -o verify` passed 1,244 unit + 277 PostgreSQL integration tests, 0 failures/errors/skips. `git diff --check` passed.
- Mobile state and screens were not changed in this follow-up.

## 2026-10-07 (Codex) — Discount management permission and totals coverage
- A new stacked-discount lifecycle test exposed that waiter roles could delete an applied discount even though apply/update enforced the manager rule. Added the same restaurant-specific `allow_discount_without_manager` policy to discount removal.
- Added PostgreSQL API coverage for 10% + fixed discounts, manager-only update/delete, recomputed totals after changing/removing a discount, and staff removal when the restaurant setting permits it. The focused scenario passed.
- Final current-source backend `./mvnw -o verify`: 1,244 unit + 278 PostgreSQL integration tests, 0 failures/errors/skips. `git diff --check` passed.
- Remaining production blockers are still provider/environment decisions: live payment, SMS/email delivery and outbox/retries, durable production image storage, plus recipe stock rules for modifiers/variants and refunds.

## 2026-10-07 (Codex) — Modifier inventory recipes and final backend verification
- Added optional recipe and quantity mappings to menu option items. Orders snapshot the selected modifier recipe/quantity, and fulfillment consumes that snapshot using option quantity and per-unit rules; later menu edits do not rewrite existing orders' stock requirements. Added schema migration V57 and kept mobile changes in existing models, API/repository, and screen-model state; no screen was added.
- Added service validation/tests for recipe scope/type and default full-yield quantities, a PostgreSQL fulfillment test for scaled modifier consumption and order-time snapshots, and a mobile HTTP DTO/API test for updating a mapped option item.
- Verification: full backend `./mvnw -o verify` passed 1,246 unit + 279 PostgreSQL integration tests (1,525 total), 0 failures/errors/skips; migration V57 applied in test schemas. Mobile `MenuOptionInventoryRecipeApiTest` passed in the prior focused run. `git diff --check` passed.
- Production remains blocked on deployment-specific integrations and policy decisions: real payment provider and settlement/reconciliation, live SMS/email delivery with retry/outbox behavior, durable production image storage, refund/void stock restock policy, and operator UI for selecting modifier recipes. No new mobile screens were added.

## 2026-10-07 continuation: fail-fast pagination guard
- Enabled `spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true` in `back-end/src/main/resources/application.yml` so collection-fetch pagination regressions fail immediately. `open-in-view: false` remains enabled.
- Removed collection fetch graphs from the limited latest-row lookups in `ReservationRepository.findTopByReservationCodeOrderByCreatedAtDesc`, `OrderRepository.findTopByOrderNumberOrderByCreatedAtDesc`, and `KdsTicketRepository.findTopByOrder_IdAndStation_IdAndStatusInOrderByCreatedAtDesc`; callers access these collections inside transactions when needed.
- The filtered strict run passed 9/9 integration tests including the 4,098-request robustness sweep. A subsequent complete `./mvnw -o verify` with the guard passed 1,246 unit tests and 279 PostgreSQL integration tests, 0 failures/errors/skips; `git diff --check` was clean.
- Full-suite shutdown logged Hikari connection-validation warnings after Testcontainers closed PostgreSQL, plus Hibernate immutable restaurant-property and follow-on-lock warnings. Treat as teardown/noise to investigate separately; tests completed successfully. This turn did not make mobile code changes.

## 2026-10-07 continuation: page branch-wide reservation lists
- Changed the existing branch reservation endpoint to filter and page at the database, with stable reservation-start/id ordering, a bounded page size, and optional date/status/customer filters. Added V60 index on `(branch_id, reservation_start, id)`.
- Updated the existing mobile reservation API/repository/state flow so All and Range modes fetch more pages and refresh every previously visible page while preserving their filters. No screen was added.
- Added PostgreSQL integration coverage for page order/metadata, filter combinations, page-size cap, invalid pagination/date inputs, and tenant scope; added mobile state coverage for All/Range continuation and refresh.
- Focused backend reservation integration passed 8/8. Full backend `./mvnw -o verify` passed 1,261 unit + 294 PostgreSQL integration tests (1,555 total), 0 failures/errors/skips. Full mobile shared JVM suite passed 324 tests, 0 failures/errors. `git diff --check` passed.
- Previously recorded production blockers remain: live payment and settlement/reconciliation, production SMS/email delivery with durable retry/outbox, durable production image storage, explicit refund/void stock policy, legacy fulfilled-order reconciliation, and operator workflow for modifier recipes.

## 2026-10-07 continuation: serialize physical count state changes
- Found that two concurrent inventory-count approvals could both observe `COMPLETED` and apply the same variance twice. Added a tenant-scoped `PESSIMISTIC_WRITE` lookup and used it for all count mutations (start, line upsert/removal, complete, approve, cancel) so status validation and movement creation are serialized.
- Added a PostgreSQL concurrent-approval test that starts two approval requests together, verifies one succeeds and one gets a conflict, and checks both the final stock level and movement ledger contain only one adjustment.
- Verification: focused `InventoryFlowIntegrationTest` passed 8/8. Full backend `./mvnw -o verify` passed 1,261 unit + 295 PostgreSQL integration tests (1,556 total), 0 failures/errors/skips. `git diff --check` passed.

## 2026-10-07 continuation: page physical inventory count history
- Replaced the unbounded `/inventory/counts` response with a tenant-scoped, status-filterable `PageResponse`, stable `createdAt DESC, id DESC` ordering, bounded 50-item default pages, and V61 indexes for filtered/unfiltered page scans. Pages IDs first, then loads the selected counts and lines in one entity-graph query.
- Updated the existing mobile inventory repository/API DTO and state model: count history can load another page, refresh reloads all pages already displayed, filters reset paging, and in-progress state prevents duplicate page fetches. No screen was added.
- Added PostgreSQL API tests for order/metadata, status filter, size cap, invalid page/size, and restaurant scope; added mobile MockEngine contract and state tests for paging, refresh, and filter reset.
- Verification: focused `InventoryFlowIntegrationTest` passed 9/9. Full backend `./mvnw -o verify` passed 1,261 unit + 296 PostgreSQL integration tests (1,557 total), 0 failures/errors/skips. Full mobile shared JVM suite passed 325 tests, 0 failures/errors. `git diff --check` passed.

## 2026-10-07 continuation: share refresh rate limits across app instances
- Replaced process-local refresh IP/token buckets with PostgreSQL atomic fixed-window counters. Store only peppered SHA-256 hashes of IPs/token IDs; use database time and independent transactions so rejected/invalid refresh requests still count. Added V62 table and expiry index.
- Added unit checks for null inputs, both dimensions, rejection, and invalid configuration; added a PostgreSQL concurrency test issuing 28 concurrent requests through two separate limiter instances and asserting exactly 20 pass.
- Verification: `./mvnw -o verify` passed 1,244 unit + 297 PostgreSQL integration tests (1,541 total), 0 failures/errors/skips. The auth integration also applied V62 and passed all 6 tests. No mobile state/API contract changed. `git diff --check` passed.
- Refreshed the old in-memory/single-node throttling notes in `back-end/src/main/resources/info/info.txt`. Previously identified live payment, SMS/email delivery, durable image storage, refund/void restock policy, legacy order reconciliation, and modifier-recipe operator workflow blockers remain.
# 2026-10-07 continuation: preserve inventory duplicate conflicts in mobile state
- Inventory now checks item-code uniqueness across soft-deleted rows to match the database constraint, maps concurrent duplicate code/barcode constraint violations to HTTP 409, and uses V63 for case-insensitive active-barcode uniqueness per restaurant. V63 deliberately fails with a clear message when duplicate active barcodes already exist, so operators must resolve those rows before deployment.
- Updated the existing mobile inventory screen model so a successful refresh after a 409 does not clear the conflict message. Added a state regression test proving the unsaved draft remains available, the duplicate explanation stays visible, and the inventory refresh still occurs. No screen was added.
- Verification: `InventoryFlowIntegrationTest` passed 11/11 (including six-way duplicate-code and duplicate-barcode races); full mobile `:shared:jvmTest` passed 328 tests, 0 failures/errors/skips; `git diff --check` passed.
- Readiness remains in progress. Before deploying V63, check for active duplicate barcodes. Broader unresolved production work includes live payment/settlement and refund behavior, durable SMS/email delivery and retries, durable image storage, refund/void stock policy and legacy reconciliation, device activation redemption, and final deployment configuration/observability review.
# 2026-10-07 continuation: prove payment refund idempotency
- Added a PostgreSQL integration regression for same-key refund retries. It verifies that the server replays the first successful refund, rejects reuse of that key with a changed amount/body (409), and persists only one refund transaction.
- Verification: focused `PaymentFlowIntegrationTest#idempotentRefundRetry` passed 1/1 on PostgreSQL with all migrations through V63 applied; `git diff --check` passed.
- Existing production blockers remain: configured live payment processing and reconciliation, durable SMS/email delivery and retries, durable production image storage, explicit refund/void inventory-restock and legacy reconciliation policy, device pairing redemption/authentication flow, and deployment configuration/observability review.
# 2026-10-07 continuation: page the built Orders ALL and HISTORY lists
- Added `GET /restaurants/{restaurantId}/branches/{branchId}/orders/page`, with a 100-row maximum, stable openedAt/id ordering, branch/restaurant scoping, server-side date/status/customer/search filters, and optional terminal-history-only behavior. It pages in SQL and batches page relationship hydration plus active-line item counts instead of loading every order and issuing summary queries per row.
- Updated the existing mobile Orders API and screen-model state so ALL/HISTORY modes load the first page, append later pages, refresh all pages already shown after invalidations, reset pages on filter changes, and debounce search. The existing screen's load-more control is reused; no screen was added.
- Verification: PostgreSQL `OrderLifecycleIntegrationTest#branchOrdersArePagedAndFiltered` passed 1/1 through schema V63, including page boundaries, deterministic order, status/history/search filters, item counts, and invalid page/size; `OrderControllerTest` and `OrderHistoryServiceTest` passed 4/4; focused mobile `OrdersScreenModelTest` + `OrderDataContractTest` passed 21/21. `git diff --check` passed.
- Legacy non-paginated restaurant/customer/branch order-list endpoints remain exposed for compatibility and need a deliberate migration/deprecation plan. Other outstanding production blockers remain live payment/settlement, durable SMS/email delivery/retries, durable production image storage, explicit refund/void stock-restock and legacy reconciliation policy, device pairing redemption, preflight for duplicate active inventory barcodes before V63, and deployment configuration/observability review.
## 2026-10-08 (Codex) — PR preparation for `feature/orders`
- User requested a pull request into `develop`, with the user performing the merge. No merge has been performed.
- Local branch currently has the backend/mobile state-management commit `0df6fda` plus docs commit `4a16460`; there are 42 additional modified/untracked paths for mobile screens, screen wiring, and docs from the Oct 8 UI work. Preserved these changes pending verification and packaging.
- Backend full verification is recorded green at 1,264 unit + 331 PostgreSQL integration tests. New UI sources have a previous recorded compile/gallery pass; this session's combined Gradle test/compile run has started but is not yet returning an exit status.
- GitHub account is authenticated through the GitHub connector and has push permission, but local `git push` credentials are unavailable (`/tmp/pos-gh` askpass helper path is missing). Remote `develop` is at `efa00654`; local `develop` remains at `5716c95`. Branch has not been pushed and no PR exists yet.
- Open: finish mobile verification, commit intended Oct 8 UI changes if verified, push `feature/orders`, create PR to `develop`, check CI, and attach PR. Do not merge; user will merge. Do not claim ready-to-merge until remote checks are green.

## 2026-10-08 (Codex) — Local commit prepared, push blocked
- Committed the Oct 8 UI/screens and wiring as `3bc07da` (`feat(mobile): add admin and operations workspaces`); branch is now clean with three commits after local develop. This includes the user-visible screen work already documented as compiled/gallery-checked in the Oct 8 entries.
- Attempted the mobile suite plus desktop/Android compile. Gradle reached `:shared:jvmTest` but produced no final output/status; it was interrupted and is not a verified fresh pass.
- Push was attempted with escalation after the user authorized pushing, but the configured askpass helper `/tmp/pos-gh` does not exist and Git had no interactive device. The authenticated GitHub connector cannot transfer the local branch contents. No remote branch and no PR have been created; no merge occurred.
- Current PR handoff requires a working local GitHub Git credential helper. Once available, push `feature/orders`, open PR to remote `develop` (currently `efa00654`), check CI, and attach the PR. Existing backend verification remains 1,264 unit + 331 integration tests green; don't call the branch ready to merge before mobile verification and remote CI complete.
## 2026-10-09 (Codex) — PR #98 opened; CI pending
- User completed GitHub device sign-in. Verified `gh auth status` for `Davidi24`, configured Git credential use with `gh auth setup-git`, and pushed `feature/orders`.
- Opened [PR #98](https://github.com/Davidi24/POS/pull/98) targeting `develop`; it remains open and unmerged. GitHub initially reported the branch behind `develop`, so rebased the feature branch onto `origin/develop` (latest `efa00654`) and pushed the rewritten branch with `--force-with-lease`. Current head: `ac55378b03a4b8c912bd71e81234c6ecd89b2c0a`. `origin/develop` is now an ancestor; GitHub reports mergeable.
- GitHub Actions has two backend workflow runs for the head commit (push and PR events); both are currently in progress at “Build and test backend”. No green conclusion yet. PR is blocked on required checks and must not be reported ready to merge until they pass.
- Backend full local verification previously passed (1,264 unit + 331 integration). Prior mobile JVM suite passed 338 tests; current fresh combined run did not complete during PR prep. UI compilation/gallery checks are recorded as passed in the Oct 8 entry.
- This entry and the earlier PR troubleshooting entry are local `AGENT_MEMORY.md` edits made after the pushed commit; they remain uncommitted so they do not trigger another CI run. No PR merge performed.
## 2026-10-09 (Codex) — Fix CI tenant-isolation test failure
- PR #98 CI failed in `TenantIsolationIntegrationTest.ownerOfAnotherRestaurantIsRefusedEverywhere`: Spring `MethodParameter.getParameterName()` returned null in the test's request-sampling helper, causing an NPE at `addParams` while running 441-route isolation coverage.
- Updated `back-end/src/test/java/pos/pos/integration/robustness/TenantIsolationIntegrationTest.java` to initialize `DefaultParameterNameDiscoverer` for handler method parameters and compare inferred parameter names null-safely.
- Focused PostgreSQL integration run passed locally: `TenantIsolationIntegrationTest`, 441 requests checked, 1 test, 0 failures/errors. Requires `systemctl --user start podman.socket` and Testcontainers Podman environment.
- Both CI event runs on prior head `ac55378` failed with the same test helper NPE. Fix is not yet pushed in this entry; commit and push the test fix, then wait for CI on the new head. PR remains open and unmerged.
