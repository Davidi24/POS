# Agent Memory — POS

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
