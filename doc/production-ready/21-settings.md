# 21. Settings

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

Moved here from step 5 (user, 2026-11-24: "is it better to leave settings for later, since I'll see things and decide what
settings they should have?"). Each step decides its own settings while its screens are looked at; this step is the last pass
over all of them, after Fraud & audit log and before Onboarding.

## Where it is
- App: Admin Hub → Settings, ten pages: Devices & printing, Tables, Reservations, Orders & kitchen, Payments & receipts, Shifts,
  Inventory, Fraud checks, Notifications, Online booking. Devices & printing and Notifications are empty, Online booking says
  "coming later" (as of 2026-11-24).
- Server: `/restaurants/{r}/settings` and its parts (`/settings/tables`, `/reservation-policy`, `/payments`, `/shifts`,
  `/inventory`, `/fraud-checks`, …).

## How it is now
- User rule: every value a restaurant might change is an Admin Hub setting with the agreed default, never hard-coded.

## Where each part is decided (2026-11-24)
- Till lock times (30 s, 3 min in important work) and the PIN pause (5 wrong PINs, 15 min): step 5, Devices & printing (its
  Settings page). The password lock, the 30-day sign-in and 5 devices per person stay fixed for everyone (safety).
- Telling someone when an account locks or a till pauses, and the Notifications settings page: step 6, Notifications.
- Breakfast / lunch / dinner hours for Statistics: step 19, Statistics.
- Online booking's page: step 24, Web.
- Every other page (Tables, Reservations, Orders & kitchen, Payments & receipts, Shifts, Inventory, Fraud checks): in the step
  of that feature.

## This step
- [ ] Every setting is saved, loaded and shown correctly, with the right default.
- [ ] Nothing a restaurant might change is still fixed in the code.
- [ ] No empty pages left (each page filled by its step, or hidden).

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: every Admin Hub setting needs the password.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- [ ] Agree the screens with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- [ ] The feature complete, after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- [ ] Run (later, when the user says).

## Done
- Nothing yet.
