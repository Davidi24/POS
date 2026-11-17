# Production-ready pass

One file per step, worked through one at a time in this order (each step needs the ones above it).

How every step goes (user, 2026-11-16):
1. **Screens first**: agree what the step's screens need with the user and build them (mock data where the server isn't ready).
2. **Development**: once the user approves the screens, make the feature complete (server and app wired, MOCKs replaced). No tests here.
3. **Hard testing list**: write in the step's file what must be tested hard. The hard testing itself runs later, when the user says
   (once they have the subscription for it), not during the step.

Read the step's file before working on it, keep its sections and "Done" up to date, and change its Status only as the user agrees
(Not started → Screens → Development → Hard testing list ready → Done).

| # | Step | Status |
|---|------|--------|
| 1 | [Sign-in & accounts](01-sign-in-and-accounts.md) | Screens |
| 2 | [Users & roles](02-users-and-roles.md) | Not started |
| 3 | [Restaurant & branches (plus opening hours)](03-restaurant-and-branches.md) | Not started |
| 4 | [Settings](04-settings.md) | Not started |
| 5 | [Devices & printing](05-devices-and-printing.md) | Not started |
| 6 | [Notifications](06-notifications.md) | Not started |
| 7 | [Shifts](07-shifts.md) | Not started |
| 8 | [Inventory](08-inventory.md) | Not started |
| 9 | [Suppliers](09-suppliers.md) | Not started |
| 10 | [Menu & recipes](10-menu-and-recipes.md) | Not started |
| 11 | [Kitchen](11-kitchen.md) | Not started |
| 12 | [Tables](12-tables.md) | Not started |
| 13 | [Reservations](13-reservations.md) | Not started |
| 14 | [Orders](14-orders.md) | Not started |
| 15 | [Payments & receipts](15-payments-and-receipts.md) | Not started |
| 16 | [Salary](16-salary.md) | Not started |
| 17 | [My Sales](17-my-sales.md) | Not started |
| 18 | [Statistics](18-statistics.md) | Not started |
| 19 | [Fraud detection & Audit log](19-fraud-and-audit-log.md) | Not started |

Rules for every step
- Settings: check that the step's own settings are actually used.
- Notifications: check the step's own notifications.
- Audit log: every change the step's service makes writes an audit entry (who, what, before → after).
- Replace the step's MOCKs (listed in its file and in AGENTS.md) with real server data.
