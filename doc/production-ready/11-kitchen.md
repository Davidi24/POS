# 11. Kitchen

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: KDS (Tickets, Upcoming, Menu, History), POS → Kitchen Status.
- Server: `/kds/*` (board, tickets, back, pos-timing), stations and routing.

## How it is now
- Tickets board: New, Cooking, Ready to pick up; the KDS never serves (the waiter serves from POS).
- Without stations the KDS says "The kitchen isn't set up yet".

## Known gaps
- Printing a ticket is a toast only.
- Held and timed tickets: the server never sets `due_at` itself (demo data set it by SQL).
- Where stations and routing are set up in the app.

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: the whole kitchen display works on PIN; setting up stations and routing (settings) needs the password.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
