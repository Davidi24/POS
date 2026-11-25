# 12. Tables

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: POS → Tables (floor plan, Edit, Create places, join and unmerge, move guests, table panel), Settings → Tables.
- Server: tables, layout, merge/unmerge, table categories (places), seating rules, special cases job.

## How it is now
- One open order per table; joining combines orders; special cases free tables nobody acted on.
- Seating limits and coming-booking rings come from settings.

## Known gaps
- Reservations still says "Booking areas" (Tables says "Places").

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: seating, joining, unmerging, moving guests, set free, bill and the special-case actions work on PIN; editing the floor plan, floors and places needs the password.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
