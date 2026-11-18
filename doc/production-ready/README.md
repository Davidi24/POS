# Production-ready pass

One file per step, worked through one at a time in this order (each step needs the ones above it).

How every step goes (user, 2026-11-16):
1. **Screens first**: agree what the step's screens need with the user and build them (mock data where the server isn't ready).
2. **Development**: once the user approves the screens, make the feature complete (server and app wired, MOCKs replaced). No tests here.
3. **Hard testing list**: write in the step's file what must be tested hard. The hard testing itself runs later, when the user says
   (once they have the subscription for it), not during the step.

Each step has its own branch, named like its file without the number plus `-production` (`sign-in-and-accounts-production`, `users-and-roles-production`, …), started from `develop`; when a step is finished it's pushed and merged before the next step's branch starts (user, 2026-11-17).

Read the step's file before working on it, keep its sections and "Done" up to date, and change its Status only as the user agrees
(Not started → Screens → Development → Hard testing list ready → Done).

| # | Step | Status |
|---|------|--------|
| 1 | [Sign-in & accounts](01-sign-in-and-accounts.md) | Development |
| 2 | [Languages](02-languages.md) | Not started |
| 3 | [Users & roles](03-users-and-roles.md) | Not started |
| 4 | [Restaurant & branches (plus opening hours)](04-restaurant-and-branches.md) | Not started |
| 5 | [Settings](05-settings.md) | Not started |
| 6 | [Devices & printing](06-devices-and-printing.md) | Not started |
| 7 | [Notifications](07-notifications.md) | Not started |
| 8 | [Shifts](08-shifts.md) | Not started |
| 9 | [Inventory](09-inventory.md) | Not started |
| 10 | [Suppliers](10-suppliers.md) | Not started |
| 11 | [Menu & recipes](11-menu-and-recipes.md) | Not started |
| 12 | [Kitchen](12-kitchen.md) | Not started |
| 13 | [Tables](13-tables.md) | Not started |
| 14 | [Reservations](14-reservations.md) | Not started |
| 15 | [Orders](15-orders.md) | Not started |
| 16 | [Payments & receipts](16-payments-and-receipts.md) | Not started |
| 17 | [Offline](17-offline.md) | Not started |
| 18 | [Salary](18-salary.md) | Not started |
| 19 | [My Sales](19-my-sales.md) | Not started |
| 20 | [Statistics](20-statistics.md) | Not started |
| 21 | [Fraud detection & Audit log](21-fraud-and-audit-log.md) | Not started |
| 22 | [Onboarding](22-onboarding.md) | Not started |
| 23 | [Web](23-web.md) | Not started |

Added 2026-11-17 (user: "offline, multi-language and onboarding"): Languages right after sign-in, so every later screen is
translated as it's finished; Offline after Payments, once the service flows work online (steps 12–16 already give new orders,
dishes and payments their own id and an idempotency key); Onboarding last, since it walks through screens from almost every step.

Rules for every step
- Settings: check that the step's own settings are actually used.
- Notifications: check the step's own notifications.
- Audit log: every change the step's service makes writes an audit entry (who, what, before → after).
- Replace the step's MOCKs (listed in its file and in AGENTS.md) with real server data.
- Languages (from step 3 on): the step's screens take their words from the translation files, in every language.
- Offline (steps 12–16): new orders, dishes and payments get their id on the device and an idempotency key, ready for step 17.

Waiting for your decision: [decide-later.md](decide-later.md) (suggestions kept for the end, by step).
