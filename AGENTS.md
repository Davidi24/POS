# AGENTS.md — POS Project

This file is read by AI coding agents (Claude, Codex, or any other assistant) working in this repository. Follow it every session.

## Protocol — read this first, every session
1. Before doing anything else, read `AGENT_MEMORY.md` in this same directory. It is the running handoff log of what previous sessions — by any agent, on any branch — have done, decided, and left unfinished.
2. Do the work the user asked for.
3. Before ending your turn / handing off, append a new dated entry to `AGENT_MEMORY.md` (format is documented inside that file). Do this whether or not the user explicitly asks — this is how the next agent (or your own next session) picks up context without the user having to re-explain "here's what changed so far".
4. Keep entries factual and specific: what changed, which files/modules, why, what's left open. Skip filler and skip restating things that are already obvious from git history.
5. Never overwrite past entries — only add new ones, newest at the top.

## Project overview
POS is a multi-module point-of-sale system:
- `back-end/` — Java Spring Boot API, built with Maven (`mvnw`). Runs against Postgres via `docker-compose.yml`.
- `web/` — TypeScript monorepo using pnpm workspaces + Turborepo (`apps/`, `packages/`, `tooling/`).
- `mobile_desktop/` — Kotlin Multiplatform app via Gradle (`androidApp/`, `desktopApp/`, `shared/`).
- `doc/ERD` — entity-relationship diagrams for the data model.

## Branching
- `main` — stable/release branch.
- `develop` — integration branch.
- Feature work happens on scoped branches (`feature/*`, `backend/*`, `mobile/*`, etc.) merged via PR.
- Check `git branch --show-current` for the branch actually checked out right now — it changes between sessions.

## Conventions
Fill in and expand this section as you discover build/test/lint commands, code style rules, and other repo-specific conventions. Keep this section stable reference material; put day-to-day change history in `AGENT_MEMORY.md`, not here.

### Backend
- Build the runnable jar with `./mvnw -o package -Dmaven.test.skip=true`. `-DskipTests` does NOT skip tests here (the pom wires surefire's `skipTests` to `${skip.unit.tests}`).
- Local backend runs as `java -jar target/pos-0.0.1-SNAPSHOT.jar` from `back-end/` against the podman `pos-db` container (db `pos_local`, user `pos_user`). The local schema is `foundation_local`, and Flyway history is `foundation_local.flyway_schema_history`.
- Stop the running backend before (or right after) rebuilding the jar. A running process whose jar was replaced starts answering 500s.
- Integration tests (`*IntegrationTest`, `*IT`, failsafe) use Testcontainers on podman. Start the socket with `systemctl --user start podman.socket`, then run with `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true`.
  - Everything: `./mvnw -o verify` (1,264 unit tests and 331 ITs as of 2026-10-07).
  - Unit tests only: `./mvnw -o test -Dtest=Name`.
  - One IT: `./mvnw -o verify -Dskip.unit.tests=true -Dit.test=Name -Dfailsafe.failIfNoSpecifiedTests=false`.
- API-level ITs extend `integration/support/AbstractPosApiIntegrationTest` (schema `pos_api_it`, `newWorld()`, `staff()`, `token()`, `menu()/item()/order()`, `setting()`). It fails any test that produced a 500, so every bad input must come back as a 4xx.
- Input rules: every `@RequestBody` is `@Valid`, and every free-text field has a `@Size` (2000 for notes/descriptions unless the column says otherwise). Bodies over 1 MB get a 413, and list page sizes are capped (`PageableUtils`). `GlobalExceptionHandler` turns database errors and entity rule failures into 400/409.
- The latest migration is V65 (order replay table rename); the next free migration is V66.
- Money endpoints:
  - Payments: `/restaurants/{r}/orders/{o}/payments` (take: ORDER_CLOSE, refund: PAYMENT_REFUND, cancel: ORDER_VOID), plus the receipt and branch payment list. Totals go through `PaymentCalculator`.
  - Statistics: `/statistics/*`, needs REPORTS_READ.
  - Fraud: `/fraud/*`, needs FRAUD_READ; reviewing needs FRAUD_REVIEW. Its thresholds are settings (`PATCH /settings/fraud-checks`).
- Staff and custom roles belong to a restaurant (`users.restaurant_id`, `roles.restaurant_id`; a super admin's roles are shared by all). `RoleHierarchyService` keeps people from seeing or managing other restaurants' staff and roles.
- Inventory low stock uses the level's own reorder quantity, otherwise the item's reorder point. Movement history is paged (`page`, `size` ≤ 500). Recipes refuse sub-recipe cycles and 100% waste.
- Roles and permissions are seeded from `AppRole`/`AppPermission` on every startup (`SuperAdminBootstrapRunner`). It updates role flags and ADDS missing permissions, but never removes a permission from a role.
- App workspaces (POS/KDS/Admin) are gated only by the `POS_ACCESS`/`KDS_ACCESS`/`ADMIN_ACCESS` permissions. Restaurants is Super Admin only.
- Other modules follow reservations through events in `pos.pos.reservation.event` (`ReservationStatusChangedEvent`, `ReservationDeletingEvent`) rather than being called from reservation services. Publish via `ReservationLifecycleService.announceStatusChange` for any status change made outside `transitionReservation`.
- Short codes and numbers shown to people (reservation codes, order numbers, KDS ticket numbers) must use random bits (`UUID.randomUUID()`). Truncated time-ordered UUIDs repeat for ~27 s and collide.
- For JPA child collections with a unique key (e.g. KDS routings), update matching rows in place. Removing and re-adding the same key makes Hibernate insert before it deletes, which violates the constraint.
- Local super admin for API checks: see `bootstrap.super-admin` in `application-local.yml`; log in with `POST /auth/device/login` `{identifier, password}`. After a reboot, run `podman start pos-db pos-mailhog` before starting the backend.
- Guest emails (booking confirmed, reminders, payment links) only reach MailHog if the backend starts with `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS_ENABLE=false`. View them at http://localhost:8025. Guest pages are served by the backend at `/public/book/{slug}/{code}` and `/public/bookings/{token}`.
- Staff pay = worked minutes × the shift's `hourly_rate` (copied from `staff_pay_rates` at clock-in) + tips on orders the person created. Read it through `ShiftPayService` (`/shifts/pay`); wages are set in Admin Hub → Shifts → Hours & pay.
- Booking money (deposit, paid extras, pre-order refunds) goes through `BookingMoneyService`. The deposit is a fixed amount per booking. Refunds keep the card fee from settings, except when the restaurant declines or a request expires (full refund). Payments run in test mode (`app.payments.provider=test`) until a real provider is connected.

### App (mobile_desktop)
- Settings: every value a restaurant might change (durations, buffers, reminder times, hold/grace minutes, thresholds, deadlines) must be editable in the **Admin Hub settings**, with the agreed value as the default. Never hard-code these (user rule).
- UI wording: never use "party"/"parties" in on-screen text (the user reads it as a celebration). Say "booking" or "group" (e.g. "Biggest booking: 3 guests", "Big groups (6+)"). Code names like `partySize` are fine.
- Build/check without tests: `./gradlew :desktopApp:compileKotlin`; test sources: `./gradlew :shared:compileTestKotlinJvm`. Launch: `./gradlew :desktopApp:run` (main class `com.saporini.mobile_desktop.MainKt`). When restarting, find PIDs and kill them in a separate command from the one that relaunches Gradle; a `grep`/`pkill` pattern like `desktopApp:run` also matches your own shell.
- Paged reservation lists: a refresh must reload every page already loaded (never drop back to page 0), so screens keep their scroll position. Long lists use `LazyColumn` keyed by id and load the next page near the end, with a manual "Try again" after a failure.
- Overview numbers come from the server summary (`/summary`, optionally per `floor`), never from a partially loaded list.
- New overview-style screens reuse `core/components/OverviewKit.kt` (stat card, panel, tabs, chips, empty states) plus the Reservations header controls (`HeaderButton`, `HeaderDropdown`, `CompactDatePicker`, `ToolbarHeight`), so they match the Reservations overview.
- Inside a `Row(Modifier.height(IntrinsicSize.Min/Max))`, don't use `BoxWithConstraints` or lazy lists: they can't report intrinsic sizes and crash at runtime. Draw with `drawBehind` instead.
- App tests: `./gradlew :shared:jvmTest` (all of them) or `--tests 'com.saporini.mobile_desktop.admin.*'`. Screen models are tested with `StandardTestDispatcher` + `Dispatchers.setMain`, fake repositories, and `MockEngine` for the `*Api` classes.
- State-only modules (ScreenModel + repository, registered in `core/di/appModule.kt`) intentionally have no screens in the current scope:
  - Payments: `pos/payment`.
  - Statistics and fraud: `statistics`, `fraud`.
  - Admin Hub: `admin/people` (Staff, Roles), `admin/inventory` (Inventory, Recipes), `admin/devices`, `admin/audit`.
  - Food pre-orders: `pos/reservations/preorder`.
  - Each model has `setActive(true/false)` for when its screen shows. Admin models gate actions on the signed-in person's permissions.
- The app's JSON leaves out default values (`encodeDefaults = false`). A request DTO field that must always be sent (e.g. `active`, `trackInventory`) must have no default.
- Parallel loads inside a screen model must wrap their `async` calls in `coroutineScope { }`. A bare `async` inside `launch` escapes the `try/catch` and crashes the app when a call fails.
- Writes check a sign-in token, not the list's load revision. Otherwise a refresh during a save drops the answer and leaves `saving` stuck.
