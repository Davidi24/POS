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
