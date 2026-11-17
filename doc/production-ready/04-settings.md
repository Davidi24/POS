# 4. Settings

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Settings, ten pages: Devices & printing, Tables, Reservations, Orders & kitchen, Payments & receipts, Shifts, Inventory, Fraud checks, Notifications, Online booking (says "coming later").
- Server: `/restaurants/{r}/settings` and its parts (`/settings/tables`, `/reservation-policy`, `/payments`, `/shifts`, `/inventory`, `/fraud-checks`, …).

## How it is now
- User rule: every value a restaurant might change is an Admin Hub setting with the agreed default, never hard-coded.

## This step
- Every setting is saved, loaded and shown correctly, with the right default.
- Each later step checks that its own settings are actually used.

## Known gaps
- Online booking page is empty ("coming later").

## Decisions to take
- What Online booking holds, or hide it until the booking website step.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
