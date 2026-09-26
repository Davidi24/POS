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
- Repository/persistence tests use Testcontainers, which can't find a Docker environment on this podman host, so they error locally. Unit tests with mocks run fine.
- Roles and permissions are seeded from `AppRole`/`AppPermission` on every startup (`SuperAdminBootstrapRunner`). It updates role flags and ADDS missing permissions, but never removes a permission from a role.
- App workspaces (POS/KDS/Admin) are gated only by the `POS_ACCESS`/`KDS_ACCESS`/`ADMIN_ACCESS` permissions. Restaurants is Super Admin only.
- Other modules follow reservations through events in `pos.pos.reservation.event` (`ReservationStatusChangedEvent`, `ReservationDeletingEvent`) rather than being called from reservation services. Publish via `ReservationLifecycleService.announceStatusChange` for any status change made outside `transitionReservation`.
- Short codes and numbers shown to people (reservation codes, order numbers, KDS ticket numbers) must use random bits (`UUID.randomUUID()`). Truncated time-ordered UUIDs repeat for ~27 s and collide.
- For JPA child collections with a unique key (e.g. KDS routings), update matching rows in place. Removing and re-adding the same key makes Hibernate insert before it deletes, which violates the constraint.
